package io.monero.core.transaction

import io.monero.core.KeyDerivation
import io.monero.core.MoneroAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TxBuilderAdvancedTest {

    private class FakeOutputProvider(
        private val currentHeight: Long,
        private val outputs: Map<Long, RingMember>,
        private val distribution: OutputDistribution
    ) : OutputProvider {
        override suspend fun getOutputDistribution(fromHeight: Long, toHeight: Long?): OutputDistribution = distribution
        override suspend fun getTotalOutputCount(): Long = outputs.keys.maxOrNull() ?: 0L
        override suspend fun getOutputs(indices: List<Long>): List<RingMember> {
            return indices.map { idx ->
                outputs[idx] ?: RingMember(
                    globalIndex = idx,
                    publicKey = ByteArray(32) { (idx.toInt() and 0xFF).toByte() },
                    commitment = ByteArray(32) { ((idx.toInt() + 1) and 0xFF).toByte() }
                )
            }
        }
        override suspend fun getCurrentHeight(): Long = currentHeight
    }

    private fun createTestEnvironment(): Triple<TxBuilder, List<SpendableOutput>, MoneroAddress> {
        val senderKeys = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0x11.toByte() })
        val senderAddress = MoneroAddress.fromKeys(senderKeys.publicSpendKey, senderKeys.publicViewKey)

        val fakeOutputs = (1L..200L).associateWith { idx ->
            RingMember(
                globalIndex = idx,
                publicKey = ByteArray(32) { ((idx.toInt() * 3) and 0xFF).toByte() },
                commitment = ByteArray(32) { ((idx.toInt() * 7) and 0xFF).toByte() }
            )
        }.toMutableMap()

        val spendableOutputs = listOf(
            SpendableOutput(
                txHash = ByteArray(32) { 0x33.toByte() },
                outputIndex = 0,
                globalIndex = 50L,
                amount = 2_000_000_000L,
                publicKey = ByteArray(32) { 0x44.toByte() },
                blockHeight = 100L,
                keyImage = ByteArray(32) { 0x55.toByte() },
                isUnlocked = true
            ),
            SpendableOutput(
                txHash = ByteArray(32) { 0x66.toByte() },
                outputIndex = 1,
                globalIndex = 80L,
                amount = 3_000_000_000L,
                publicKey = ByteArray(32) { 0x77.toByte() },
                blockHeight = 110L,
                keyImage = ByteArray(32) { 0x88.toByte() },
                isUnlocked = true
            ),
            SpendableOutput(
                txHash = ByteArray(32) { 0x99.toByte() },
                outputIndex = 2,
                globalIndex = 120L,
                amount = 1_500_000_000L,
                publicKey = ByteArray(32) { 0xAA.toByte() },
                blockHeight = 120L,
                keyImage = ByteArray(32) { 0xBB.toByte() },
                isUnlocked = true
            )
        )

        spendableOutputs.forEach { so ->
            fakeOutputs[so.globalIndex] = RingMember(so.globalIndex, so.publicKey, ByteArray(32))
        }

        val distribution = OutputDistribution(
            startHeight = 0,
            distribution = List(500) { (it + 1).toLong() },
            base = 0
        )
        val provider = FakeOutputProvider(200L, fakeOutputs, distribution)

        val selectionConfig = SelectionConfig(
            currentHeight = 200L,
            minConfirmations = 10,
            ringSize = 16,
            numOutputs = 2
        )

        val builder = TxBuilder(provider, selectionConfig, DecoySelectionConfig(ringSize = 16))
        return Triple(builder, spendableOutputs, senderAddress)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // K4.8: Batch transfer (multiple destinations)
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun testBatchTransfer() = kotlinx.coroutines.test.runTest {
        val (builder, outputs, changeAddr) = createTestEnvironment()

        val recipient1 = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0x22.toByte() })
        val recipient2 = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0x33.toByte() })
        val addr1 = MoneroAddress.fromKeys(recipient1.publicSpendKey, recipient1.publicViewKey)
        val addr2 = MoneroAddress.fromKeys(recipient2.publicSpendKey, recipient2.publicViewKey)

        val built = builder.build(
            availableOutputs = outputs,
            destinations = listOf(
                TxBuilder.Destination(addr1, 500_000_000L),
                TxBuilder.Destination(addr2, 800_000_000L)
            ),
            changeAddress = changeAddr,
            rctType = RctType.CLSAG
        )

        // Should have 3 outputs: 2 destinations + 1 change
        assertEquals(3, built.tx.outputs.size)
        assertTrue(built.fee > 0)
        assertTrue(built.change > 0)

        // Round-trip parse
        val parsed = TransactionParser.parse(built.txBlob)
        assertEquals(3, parsed.outputs.size)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // K4.8: sweep_all
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun testSweepAll() = kotlinx.coroutines.test.runTest {
        val (builder, outputs, _) = createTestEnvironment()

        val recipientKeys = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0xCC.toByte() })
        val recipientAddr = MoneroAddress.fromKeys(recipientKeys.publicSpendKey, recipientKeys.publicViewKey)

        val built = builder.sweepAll(
            availableOutputs = outputs,
            destinationAddress = recipientAddr,
            rctType = RctType.CLSAG
        )

        // Sweep produces exactly 1 output (no change)
        assertEquals(1, built.tx.outputs.size)
        assertEquals(0L, built.change)
        assertTrue(built.fee > 0)

        // Total input = 2B + 3B + 1.5B = 6.5B
        // send = total - fee
        val totalInput = outputs.sumOf { it.amount }
        // The fee is deducted, so sendAmount = totalInput - fee
        // We can't directly check sendAmount but we know fee is positive

        val parsed = TransactionParser.parse(built.txBlob)
        assertEquals(1, parsed.outputs.size)
        assertNotNull(parsed.rctSignature)
        assertEquals(built.fee, parsed.rctSignature?.txnFee)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // K4.9: Offline signing export/import
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun testOfflineSigningExportUnsigned() = kotlinx.coroutines.test.runTest {
        val (builder, outputs, changeAddr) = createTestEnvironment()

        val recipientKeys = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0xDD.toByte() })
        val recipientAddr = MoneroAddress.fromKeys(recipientKeys.publicSpendKey, recipientKeys.publicViewKey)

        val built = builder.build(
            availableOutputs = outputs,
            destinations = listOf(TxBuilder.Destination(recipientAddr, 1_000_000_000L)),
            changeAddress = changeAddr
        )

        // For test purposes, create fake masks
        val outputMasks = built.tx.outputs.map { ByteArray(32) { 0x12.toByte() } }
        val outputAmounts = listOf(1_000_000_000L, built.change)

        val exportJson = OfflineSigning.exportUnsigned(built, outputMasks, outputAmounts)
        assertTrue(exportJson.isNotEmpty())
        assertTrue(exportJson.contains("txPrefixHex"))
        assertTrue(exportJson.contains("inputs"))
        assertTrue(exportJson.contains("outputs"))

        // Parse back
        val parsed = OfflineSigning.parseUnsigned(exportJson)
        assertEquals(1, parsed.version)
        assertEquals(built.rings.size, parsed.inputs.size)
        assertEquals(built.tx.outputs.size, parsed.outputs.size)
        assertEquals(built.fee, parsed.fee)
    }

    @Test
    fun testOfflineSigningExportSigned() {
        val txBlob = ByteArray(256) { it.toByte() }
        val txHash = ByteArray(32) { 0xAB.toByte() }
        val fee = 50_000_000L

        val exportJson = OfflineSigning.exportSigned(txBlob, txHash, fee)
        assertTrue(exportJson.isNotEmpty())
        assertTrue(exportJson.contains("txBlobHex"))
        assertTrue(exportJson.contains("txHashHex"))

        val parsed = OfflineSigning.parseSigned(exportJson)
        assertEquals(1, parsed.version)
        assertEquals(fee, parsed.fee)

        val recoveredBlob = OfflineSigning.signedExportToBlob(parsed)
        assertTrue(txBlob.contentEquals(recoveredBlob))
    }
}
