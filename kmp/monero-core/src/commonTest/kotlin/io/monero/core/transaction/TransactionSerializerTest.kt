package io.monero.core.transaction

import kotlin.test.*

class TransactionSerializerTest {

    @Test
    fun testVarintEncoding() {
        val writer = BinaryWriter()

        // Single byte values (0-127)
        writer.writeVarint(0)
        writer.writeVarint(1)
        writer.writeVarint(127)

        val bytes = writer.toByteArray()
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0x7F.toByte(), bytes[2])
    }

    @Test
    fun testVarintEncodingMultiByte() {
        val writer = BinaryWriter()

        // 128 = 0x80 -> [0x80, 0x01]
        writer.writeVarint(128)

        val bytes = writer.toByteArray()
        assertEquals(2, bytes.size)
        assertEquals(0x80.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
    }

    @Test
    fun testVarintEncodingLarge() {
        val writer = BinaryWriter()

        // 300 = 0x12C -> [0xAC, 0x02]
        writer.writeVarint(300)

        val bytes = writer.toByteArray()
        assertEquals(2, bytes.size)
        assertEquals(0xAC.toByte(), bytes[0])
        assertEquals(0x02.toByte(), bytes[1])
    }

    @Test
    fun testSerializeSimpleTransaction() {
        val tx = createTestTransaction()
        val serialized = TransactionSerializer.serialize(tx)

        assertTrue(serialized.isNotEmpty())
        // Version 2 as varint
        assertEquals(0x02.toByte(), serialized[0])
    }

    @Test
    fun testSerializeToHex() {
        val tx = createTestTransaction()
        val hex = TransactionSerializer.serializeToHex(tx)

        assertTrue(hex.isNotEmpty())
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(0, hex.length % 2)
    }

    @Test
    fun testPrefixHash() {
        val tx = createTestTransaction()
        val hash = TransactionSerializer.prefixHash(tx)

        assertEquals(32, hash.size)
        assertFalse(hash.all { it == 0.toByte() })
    }

    @Test
    fun testSerializePrefixDeterministic() {
        val tx = createTestTransaction()
        val prefix1 = TransactionSerializer.serializePrefix(tx)
        val prefix2 = TransactionSerializer.serializePrefix(tx)

        assertContentEquals(prefix1, prefix2)
    }

    @Test
    fun testSerializeOutput() {
        val output = TxOutput(
            index = 0,
            amount = 0,
            target = TxOutTarget(
                key = ByteArray(32) { 0xAB.toByte() }
            )
        )

        val tx = Transaction(
            hash = ByteArray(32),
            version = 2,
            unlockTime = 0,
            inputs = emptyList(),
            outputs = listOf(output),
            extra = TxExtra(null, emptyList(), null, null, ByteArray(0)),
            rctSignature = null
        )

        val serialized = TransactionSerializer.serialize(tx)
        assertTrue(serialized.isNotEmpty())
    }

    @Test
    fun testSerializeOutputWithViewTag() {
        val output = TxOutput(
            index = 0,
            amount = 0,
            target = TxOutTarget(
                key = ByteArray(32) { 0xCD.toByte() },
                viewTag = 0x42
            )
        )

        val tx = Transaction(
            hash = ByteArray(32),
            version = 2,
            unlockTime = 0,
            inputs = emptyList(),
            outputs = listOf(output),
            extra = TxExtra(null, emptyList(), null, null, ByteArray(0)),
            rctSignature = null
        )

        val serialized = TransactionSerializer.serialize(tx)
        assertTrue(serialized.isNotEmpty())

        // Should contain the view tag byte 0x42 somewhere
        assertTrue(serialized.contains(0x42.toByte()))
    }

    @Test
    fun testSerializeInput() {
        val input = TxInput(
            amount = 0,
            keyOffsets = listOf(100, 50, 25, 10),
            keyImage = ByteArray(32) { 0xEE.toByte() }
        )

        val tx = Transaction(
            hash = ByteArray(32),
            version = 2,
            unlockTime = 0,
            inputs = listOf(input),
            outputs = emptyList(),
            extra = TxExtra(null, emptyList(), null, null, ByteArray(0)),
            rctSignature = null
        )

        val serialized = TransactionSerializer.serialize(tx)
        assertTrue(serialized.isNotEmpty())

        // Input type 0x02 should appear
        assertTrue(serialized.contains(0x02.toByte()))
    }

    @Test
    fun testBuildExtra() {
        val txPubKey = ByteArray(32) { 0x11.toByte() }
        val additionalPubKeys = listOf(
            ByteArray(32) { 0x22.toByte() },
            ByteArray(32) { 0x33.toByte() }
        )
        val paymentId = ByteArray(8) { 0x44.toByte() }

        val extra = TransactionSerializer.buildExtra(txPubKey, additionalPubKeys, paymentId)

        assertContentEquals(txPubKey, extra.txPubKey)
        assertEquals(2, extra.additionalPubKeys.size)
        assertContentEquals(paymentId, extra.paymentId)
        assertNotNull(extra.nonce)
    }

    @Test
    fun testSerializeRctSignature() {
        val tx = createTransactionWithRct()
        val serialized = TransactionSerializer.serialize(tx)

        assertTrue(serialized.isNotEmpty())
        // RCT type should be present
    }

    @Test
    fun testRoundTripSimple() {
        // Create a transaction with RCT signature, serialize, parse back
        val tx = createTransactionWithRct()
        val serialized = TransactionSerializer.serialize(tx)

        // Parse back
        val parsed = TransactionParser.parse(serialized)

        assertEquals(tx.version, parsed.version)
        assertEquals(tx.unlockTime, parsed.unlockTime)
        assertEquals(tx.inputs.size, parsed.inputs.size)
        assertEquals(tx.outputs.size, parsed.outputs.size)
        assertEquals(tx.rctSignature?.type, parsed.rctSignature?.type)
        assertEquals(tx.rctSignature?.txnFee, parsed.rctSignature?.txnFee)
    }

    private fun createTestTransaction(): Transaction {
        return Transaction(
            hash = ByteArray(32) { it.toByte() },
            version = 2,
            unlockTime = 0,
            inputs = listOf(
                TxInput(
                    amount = 0,
                    keyOffsets = listOf(1000, 200, 50),
                    keyImage = ByteArray(32) { 0xAA.toByte() }
                )
            ),
            outputs = listOf(
                TxOutput(
                    index = 0,
                    amount = 0,
                    target = TxOutTarget(ByteArray(32) { 0xBB.toByte() })
                ),
                TxOutput(
                    index = 1,
                    amount = 0,
                    target = TxOutTarget(ByteArray(32) { 0xCC.toByte() })
                )
            ),
            extra = TxExtra(
                txPubKey = ByteArray(32) { 0xDD.toByte() },
                additionalPubKeys = emptyList(),
                paymentId = null,
                nonce = null,
                raw = ByteArray(0)
            ),
            rctSignature = null
        )
    }

    private fun createTransactionWithRct(): Transaction {
        return Transaction(
            hash = ByteArray(32) { it.toByte() },
            version = 2,
            unlockTime = 0,
            inputs = listOf(
                TxInput(
                    amount = 0,
                    keyOffsets = listOf(1000, 200),
                    keyImage = ByteArray(32) { 0xAA.toByte() }
                )
            ),
            outputs = listOf(
                TxOutput(
                    index = 0,
                    amount = 0,
                    target = TxOutTarget(ByteArray(32) { 0xBB.toByte() })
                )
            ),
            extra = TxExtra(
                txPubKey = ByteArray(32) { 0xDD.toByte() },
                additionalPubKeys = emptyList(),
                paymentId = null,
                nonce = null,
                raw = ByteArray(0)
            ),
            rctSignature = RctSignature(
                type = RctType.BulletproofPlus,
                txnFee = 20000000,
                ecdhInfo = listOf(
                    EcdhInfo(ByteArray(32), ByteArray(8) { 0x12.toByte() })
                ),
                outPk = listOf(
                    ByteArray(32) { 0xEE.toByte() }
                )
            )
        )
    }
}
