package io.monero.core.transaction

import io.monero.core.KeyDerivation
import io.monero.core.MoneroAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TxBuilderTest {

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

    @Test
    fun testBuildTxRoundTripParses() = kotlinx.coroutines.test.runTest {
        // Wallet keys (sender/change)
        val senderKeys = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0x11.toByte() })
        val changeAddress = MoneroAddress.fromKeys(senderKeys.publicSpendKey, senderKeys.publicViewKey)

        // Recipient
        val recipientKeys = KeyDerivation.deriveWalletKeys(ByteArray(32) { 0x22.toByte() })
        val recipientAddress = MoneroAddress.fromKeys(recipientKeys.publicSpendKey, recipientKeys.publicViewKey)

        // Fake chain outputs for decoy selection
        val fakeOutputs = (1L..200L).associateWith { idx ->
            RingMember(
                globalIndex = idx,
                publicKey = ByteArray(32) { ((idx.toInt() * 3) and 0xFF).toByte() },
                commitment = ByteArray(32) { ((idx.toInt() * 7) and 0xFF).toByte() }
            )
        }.toMutableMap()

        // The real spendable output must exist in provider, so rings include it.
        val realGlobalIndex = 150L
        val realOutput = SpendableOutput(
            txHash = ByteArray(32) { 0x33.toByte() },
            outputIndex = 0,
            globalIndex = realGlobalIndex,
            amount = 5_000_000_000L,
            publicKey = ByteArray(32) { 0x44.toByte() },
            blockHeight = 100L,
            keyImage = ByteArray(32) { 0x55.toByte() },
            isUnlocked = true
        )
        fakeOutputs[realGlobalIndex] = RingMember(
            globalIndex = realGlobalIndex,
            publicKey = realOutput.publicKey,
            commitment = ByteArray(32) { 0x66.toByte() }
        )

        val distribution = OutputDistribution(
            startHeight = 0,
            distribution = List(500) { (it + 1).toLong() },
            base = 0
        )
        val provider = FakeOutputProvider(
            currentHeight = 200L,
            outputs = fakeOutputs,
            distribution = distribution
        )

        val selectionConfig = SelectionConfig(
            currentHeight = 200L,
            minConfirmations = 10,
            ringSize = 16,
            numOutputs = 2
        )

        val builder = TxBuilder(
            outputProvider = provider,
            selectionConfig = selectionConfig,
            decoyConfig = DecoySelectionConfig(ringSize = 16)
        )

        val built = builder.build(
            availableOutputs = listOf(realOutput),
            destinations = listOf(
                TxBuilder.Destination(
                    address = recipientAddress,
                    amount = 1_000_000_000L
                )
            ),
            changeAddress = changeAddress,
            rctType = RctType.CLSAG
        )

        assertTrue(built.txBlob.isNotEmpty())
        assertEquals(2, built.tx.version)
        assertNotNull(built.tx.rctSignature)
        assertEquals(RctType.CLSAG, built.tx.rctSignature?.type)

        val parsed = TransactionParser.parse(built.txBlob)
        assertEquals(2, parsed.version)
        assertEquals(built.tx.inputs.size, parsed.inputs.size)
        assertEquals(built.tx.outputs.size, parsed.outputs.size)
        assertNotNull(parsed.rctSignature)
        assertEquals(built.tx.rctSignature?.type, parsed.rctSignature?.type)
        assertEquals(built.tx.rctSignature?.txnFee, parsed.rctSignature?.txnFee)
    }
}
