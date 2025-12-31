package io.monero.core.transaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class TransactionModelsTest {
    
    @Test
    fun testTxOutputCreation() {
        val pubKey = ByteArray(32) { it.toByte() }
        val target = TxOutTarget(pubKey, 0x42)
        val output = TxOutput(
            index = 0,
            amount = 1000000000L,
            target = target,
            globalIndex = 12345L
        )
        
        assertEquals(0, output.index)
        assertEquals(1000000000L, output.amount)
        assertEquals(0x42.toByte(), output.target.viewTag)
        assertEquals(12345L, output.globalIndex)
    }
    
    @Test
    fun testTxInputCreation() {
        val keyImage = ByteArray(32) { (it + 100).toByte() }
        val keyOffsets = listOf(1000L, 500L, 300L, 200L, 100L, 50L, 25L)
        
        val input = TxInput(
            amount = 0L, // RingCT
            keyOffsets = keyOffsets,
            keyImage = keyImage
        )
        
        assertEquals(0L, input.amount)
        assertEquals(7, input.keyOffsets.size)
        assertEquals(1000L, input.keyOffsets[0])
    }
    
    @Test
    fun testKeyImageFromHex() {
        val hex = "aa".repeat(32) // 32 bytes of 0xaa
        val keyImage = KeyImage.fromHex(hex)
        assertEquals(32, keyImage.bytes.size)
    }
    
    @Test
    fun testKeyImageToHex() {
        val bytes = ByteArray(32) { 0xab.toByte() }
        val keyImage = KeyImage(bytes)
        val hex = keyImage.toHex()
        assertEquals(64, hex.length)
        assertTrue(hex.all { it == 'a' || it == 'b' })
    }
    
    @Test
    fun testRctTypeFromValue() {
        assertEquals(RctType.Null, RctType.fromValue(0))
        assertEquals(RctType.Full, RctType.fromValue(1))
        assertEquals(RctType.Simple, RctType.fromValue(2))
        assertEquals(RctType.Bulletproof, RctType.fromValue(3))
        assertEquals(RctType.Bulletproof2, RctType.fromValue(4))
        assertEquals(RctType.CLSAG, RctType.fromValue(5))
        assertEquals(RctType.BulletproofPlus, RctType.fromValue(6))
        assertEquals(RctType.Null, RctType.fromValue(99)) // Unknown
    }
    
    @Test
    fun testOwnedOutputOutpoint() {
        val txHash = ByteArray(32) { 0x12.toByte() }
        val output = OwnedOutput(
            txHash = txHash,
            outputIndex = 1,
            globalIndex = 54321L,
            amount = 5000000000L,
            publicKey = ByteArray(32),
            blockHeight = 1000L,
            timestamp = 1609459200L
        )
        
        assertTrue(output.outpoint.endsWith(":1"))
        assertTrue(output.outpoint.startsWith("12"))
    }
    
    @Test
    fun testOwnedOutputEquality() {
        val txHash = ByteArray(32) { 0x42.toByte() }
        val output1 = OwnedOutput(
            txHash = txHash.copyOf(),
            outputIndex = 0,
            globalIndex = 100L,
            amount = 1000L,
            publicKey = ByteArray(32),
            blockHeight = 500L,
            timestamp = 0L
        )
        val output2 = OwnedOutput(
            txHash = txHash.copyOf(),
            outputIndex = 0,
            globalIndex = 200L, // Different global index
            amount = 2000L,     // Different amount
            publicKey = ByteArray(32),
            blockHeight = 600L,
            timestamp = 0L
        )
        
        // Should be equal based on txHash and outputIndex only
        assertEquals(output1, output2)
    }
    
    @Test
    fun testEcdhInfoEquality() {
        val mask = ByteArray(32) { 1 }
        val amount = ByteArray(32) { 2 }
        
        val info1 = EcdhInfo(mask.copyOf(), amount.copyOf())
        val info2 = EcdhInfo(mask.copyOf(), amount.copyOf())
        
        assertEquals(info1, info2)
    }
    
    @Test
    fun testTxOutTargetWithViewTag() {
        val key = ByteArray(32)
        val targetWithTag = TxOutTarget(key, 0xff.toByte())
        val targetWithoutTag = TxOutTarget(key)
        
        assertNotNull(targetWithTag.viewTag)
        assertEquals(null, targetWithoutTag.viewTag)
    }
    
    @Test
    fun testTransactionEquality() {
        val hash1 = ByteArray(32) { 0xaa.toByte() }
        val hash2 = ByteArray(32) { 0xaa.toByte() }
        val hash3 = ByteArray(32) { 0xbb.toByte() }
        
        val tx1 = Transaction(
            hash = hash1,
            version = 2,
            unlockTime = 0,
            inputs = emptyList(),
            outputs = emptyList(),
            extra = TxExtra(null, raw = ByteArray(0)),
            rctSignature = null
        )
        val tx2 = Transaction(
            hash = hash2,
            version = 2,
            unlockTime = 100, // Different
            inputs = emptyList(),
            outputs = emptyList(),
            extra = TxExtra(null, raw = ByteArray(0)),
            rctSignature = null
        )
        val tx3 = Transaction(
            hash = hash3,
            version = 2,
            unlockTime = 0,
            inputs = emptyList(),
            outputs = emptyList(),
            extra = TxExtra(null, raw = ByteArray(0)),
            rctSignature = null
        )
        
        // Equal by hash only
        assertEquals(tx1, tx2)
        assertFalse(tx1 == tx3)
    }
    
    @Test
    fun testScannedOutputOwned() {
        val output = TxOutput(0, 0, TxOutTarget(ByteArray(32)))
        val scanned = ScannedOutput(
            output = output,
            amount = 1000000L,
            isOwned = true,
            subaddressIndex = 0 to 1
        )
        
        assertTrue(scanned.isOwned)
        assertEquals(1000000L, scanned.amount)
        assertEquals(0 to 1, scanned.subaddressIndex)
    }
}
