package io.monero.core.scanner

import io.monero.core.address.SubaddressIndex
import io.monero.core.transaction.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewKeyScannerTest {
    
    // Test keys (32 bytes each)
    private val viewSecretKey = ByteArray(32) { (it + 1).toByte() }
    private val viewPublicKey = ByteArray(32) { (it + 33).toByte() }
    private val spendPublicKey = ByteArray(32) { (it + 65).toByte() }
    
    @Test
    fun testScannerCreation() {
        val scanner = ViewKeyScanner(
            viewPublicKey = viewPublicKey,
            viewSecretKey = viewSecretKey,
            spendPublicKey = spendPublicKey
        )
        assertNotNull(scanner)
    }
    
    @Test
    fun testPrecomputeSubaddresses() {
        val scanner = ViewKeyScanner(
            viewPublicKey = viewPublicKey,
            viewSecretKey = viewSecretKey,
            spendPublicKey = spendPublicKey
        )
        
        // Precompute 2 accounts with 5 addresses each
        scanner.precomputeSubaddresses(majorMax = 1, minorMax = 4)
        
        // Should have precomputed 2 * 5 = 10 subaddresses
        // (including (0,0) for main address)
    }
    
    @Test
    fun testScanEmptyTransaction() {
        val scanner = ViewKeyScanner(
            viewPublicKey = viewPublicKey,
            viewSecretKey = viewSecretKey,
            spendPublicKey = spendPublicKey
        )
        
        // Transaction with no outputs
        val tx = Transaction(
            hash = ByteArray(32),
            version = 2,
            unlockTime = 0,
            inputs = emptyList(),
            outputs = emptyList(),
            extra = TxExtra(txPubKey = ByteArray(32), raw = ByteArray(0)),
            rctSignature = null
        )
        
        val results = scanner.scanTransaction(tx)
        assertTrue(results.isEmpty())
    }
    
    @Test
    fun testScanTransactionNoTxPubKey() {
        val scanner = ViewKeyScanner(
            viewPublicKey = viewPublicKey,
            viewSecretKey = viewSecretKey,
            spendPublicKey = spendPublicKey
        )
        
        // Transaction with no tx public key in extra
        val tx = Transaction(
            hash = ByteArray(32),
            version = 2,
            unlockTime = 0,
            inputs = emptyList(),
            outputs = listOf(
                TxOutput(0, 0, TxOutTarget(ByteArray(32)))
            ),
            extra = TxExtra(txPubKey = null, raw = ByteArray(0)),
            rctSignature = null
        )
        
        val results = scanner.scanTransaction(tx)
        assertTrue(results.isEmpty())
    }
    
    @Test
    fun testSubaddressTable() {
        val table = SubaddressTable()
        
        val key1 = ByteArray(32) { 0x01 }
        val key2 = ByteArray(32) { 0x02 }
        
        table.add(key1, SubaddressIndex(0, 0))
        table.add(key2, SubaddressIndex(1, 5))
        
        assertEquals(2, table.size)
        
        val result1 = table.lookup(key1)
        assertNotNull(result1)
        assertEquals(0, result1.major)
        assertEquals(0, result1.minor)
        
        val result2 = table.lookup(key2)
        assertNotNull(result2)
        assertEquals(1, result2.major)
        assertEquals(5, result2.minor)
        
        val result3 = table.lookup(ByteArray(32) { 0x03 })
        assertNull(result3)
        
        table.clear()
        assertEquals(0, table.size)
    }
    
    @Test
    fun testSubaddressIndex() {
        val main = SubaddressIndex.MAIN
        assertTrue(main.isMainAddress)
        assertFalse(main.isSubaddress)
        assertEquals("(0,0)", main.toString())
        
        val sub = SubaddressIndex(1, 5)
        assertFalse(sub.isMainAddress)
        assertTrue(sub.isSubaddress)
        assertEquals("(1,5)", sub.toString())
        
        val parsed = SubaddressIndex.parse("(2,10)")
        assertEquals(2, parsed.major)
        assertEquals(10, parsed.minor)
    }
    
    @Test
    fun testViewTagComputation() {
        val scanner = ViewKeyScanner(
            viewPublicKey = viewPublicKey,
            viewSecretKey = viewSecretKey,
            spendPublicKey = spendPublicKey
        )
        
        // Create an output with a view tag
        val txPubKey = ByteArray(32) { (it * 3).toByte() }
        val output = TxOutput(
            index = 0,
            amount = 0,
            target = TxOutTarget(ByteArray(32), viewTag = 0x42)
        )
        
        // Scan should check view tag first
        val result = scanner.scanOutput(
            output = output,
            outputIndex = 0,
            txPubKey = txPubKey
        )
        
        // Result may be null if view tag doesn't match
        // (expected in this test with dummy keys)
    }
    
    @Test
    fun testScannedOutputCreation() {
        val output = TxOutput(0, 0, TxOutTarget(ByteArray(32)))
        
        val scanned = ScannedOutput(
            output = output,
            amount = 1_000_000_000L,
            isOwned = true,
            subaddressIndex = 0 to 1
        )
        
        assertTrue(scanned.isOwned)
        assertEquals(1_000_000_000L, scanned.amount)
        assertEquals(0 to 1, scanned.subaddressIndex)
    }
}
