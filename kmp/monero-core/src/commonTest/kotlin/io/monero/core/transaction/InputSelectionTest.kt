package io.monero.core.transaction

import kotlin.test.*

class InputSelectionTest {
    
    private fun createOutput(
        amount: Long,
        blockHeight: Long = 1000,
        globalIndex: Long = 0
    ) = SpendableOutput(
        txHash = ByteArray(32) { globalIndex.toByte() },
        outputIndex = 0,
        globalIndex = globalIndex,
        amount = amount,
        publicKey = ByteArray(32),
        blockHeight = blockHeight,
        isUnlocked = true
    )
    
    @Test
    fun testSelectInputsSmallestFirst() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 1000
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(1_000_000_000, globalIndex = 1),  // 1 XMR
            createOutput(500_000_000, globalIndex = 2),    // 0.5 XMR
            createOutput(100_000_000, globalIndex = 3),    // 0.1 XMR
            createOutput(50_000_000, globalIndex = 4)      // 0.05 XMR
        )
        
        val result = selector.selectInputs(
            outputs,
            targetAmount = 600_000_000,  // 0.6 XMR
            strategy = SelectionStrategy.SMALLEST_FIRST
        )
        
        assertNotNull(result)
        assertTrue(result.isValid)
        assertTrue(result.selectedOutputs.size >= 2)
        assertEquals(600_000_000, result.sendAmount)
    }
    
    @Test
    fun testSelectInputsLargestFirst() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 1000
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(1_000_000_000, globalIndex = 1),
            createOutput(500_000_000, globalIndex = 2),
            createOutput(100_000_000, globalIndex = 3)
        )
        
        val result = selector.selectInputs(
            outputs,
            targetAmount = 400_000_000,
            strategy = SelectionStrategy.LARGEST_FIRST
        )
        
        assertNotNull(result)
        assertTrue(result.isValid)
        // Should select the 1 XMR output first
        assertEquals(1, result.selectedOutputs.size)
        assertEquals(1_000_000_000, result.selectedOutputs[0].amount)
    }
    
    @Test
    fun testSelectInputsClosestMatch() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 1000
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(1_000_000_000, globalIndex = 1),
            createOutput(550_000_000, globalIndex = 2),  // Closest to 500M
            createOutput(100_000_000, globalIndex = 3)
        )
        
        val result = selector.selectInputs(
            outputs,
            targetAmount = 500_000_000,
            strategy = SelectionStrategy.CLOSEST_MATCH
        )
        
        assertNotNull(result)
        assertTrue(result.isValid)
        // Should select 550M first (closest to target)
        assertTrue(result.selectedOutputs.any { it.amount == 550_000_000L })
    }
    
    @Test
    fun testSelectInputsInsufficientFunds() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 1000
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(100_000_000, globalIndex = 1),
            createOutput(50_000_000, globalIndex = 2)
        )
        
        val result = selector.selectInputs(
            outputs,
            targetAmount = 1_000_000_000  // More than available
        )
        
        assertNull(result)
    }
    
    @Test
    fun testSelectInputsUnconfirmed() {
        val config = SelectionConfig(
            currentHeight = 1005,  // Only 5 confirmations
            minConfirmations = 10,
            feePerByte = 1000
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(1_000_000_000, blockHeight = 1000, globalIndex = 1)
        )
        
        // Output has only 5 confirmations, needs 10
        val result = selector.selectInputs(outputs, targetAmount = 100_000_000)
        
        assertNull(result)
    }
    
    @Test
    fun testSelectAll() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 1000
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(1_000_000_000, globalIndex = 1),
            createOutput(500_000_000, globalIndex = 2),
            createOutput(100_000_000, globalIndex = 3)
        )
        
        val result = selector.selectAll(outputs)
        
        assertNotNull(result)
        assertTrue(result.isValid)
        assertEquals(3, result.selectedOutputs.size)
        assertEquals(1_600_000_000, result.totalAmount)
        assertEquals(0, result.change)  // Sweep has no change
        assertTrue(result.fee > 0)
        assertEquals(result.totalAmount - result.fee, result.sendAmount)
    }
    
    @Test
    fun testMaxInputsLimit() {
        val config = SelectionConfig(
            currentHeight = 2000,
            maxInputs = 3,
            feePerByte = 100
        )
        val selector = InputSelector(config)
        
        // Create many small outputs
        val outputs = (1..10).map { i ->
            createOutput(100_000_000, globalIndex = i.toLong())
        }
        
        val result = selector.selectInputs(
            outputs,
            targetAmount = 250_000_000,
            strategy = SelectionStrategy.LARGEST_FIRST
        )
        
        assertNotNull(result)
        assertTrue(result.selectedOutputs.size <= 3)
    }
    
    @Test
    fun testFeeEstimation() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 1000,
            ringSize = 16
        )
        val selector = InputSelector(config)
        
        val fee1 = selector.estimateFee(1)
        val fee2 = selector.estimateFee(2)
        val fee3 = selector.estimateFee(3)
        
        // More inputs = higher fee
        assertTrue(fee2 > fee1)
        assertTrue(fee3 > fee2)
        
        // Fee should be reasonable (not zero, not astronomically high)
        assertTrue(fee1 > 0)
        assertTrue(fee1 < 1_000_000_000)  // Less than 1 XMR
    }
    
    @Test
    fun testChangeCalculation() {
        val config = SelectionConfig(
            currentHeight = 2000,
            feePerByte = 100  // Low fee for predictable test
        )
        val selector = InputSelector(config)
        
        val outputs = listOf(
            createOutput(1_000_000_000, globalIndex = 1)  // 1 XMR
        )
        
        val result = selector.selectInputs(
            outputs,
            targetAmount = 100_000_000  // 0.1 XMR
        )
        
        assertNotNull(result)
        // Change = total - send - fee
        assertEquals(result.totalAmount - result.sendAmount - result.fee, result.change)
        assertTrue(result.change > 0)
    }
}

class UtxoUtilsTest {
    
    @Test
    fun testIsUnlockedNoLockTime() {
        assertTrue(UtxoUtils.isUnlocked(
            blockHeight = 1000,
            unlockTime = 0,
            currentHeight = 1020,
            currentTime = 0
        ))
    }
    
    @Test
    fun testIsUnlockedNotEnoughConfirmations() {
        assertFalse(UtxoUtils.isUnlocked(
            blockHeight = 1000,
            unlockTime = 0,
            currentHeight = 1005,  // Only 5 confirmations
            currentTime = 0
        ))
    }
    
    @Test
    fun testIsUnlockedBlockHeightLock() {
        // Locked until block 2000
        assertFalse(UtxoUtils.isUnlocked(
            blockHeight = 1000,
            unlockTime = 2000,
            currentHeight = 1500,
            currentTime = 0
        ))
        
        assertTrue(UtxoUtils.isUnlocked(
            blockHeight = 1000,
            unlockTime = 2000,
            currentHeight = 2010,
            currentTime = 0
        ))
    }
    
    @Test
    fun testIsUnlockedTimestampLock() {
        val futureTime = 600_000_000L  // A timestamp
        val currentTime = 500_000_000L
        
        assertFalse(UtxoUtils.isUnlocked(
            blockHeight = 1000,
            unlockTime = futureTime,
            currentHeight = 2000,
            currentTime = currentTime
        ))
        
        assertTrue(UtxoUtils.isUnlocked(
            blockHeight = 1000,
            unlockTime = futureTime,
            currentHeight = 2000,
            currentTime = futureTime + 100
        ))
    }
    
    @Test
    fun testGroupBySubaddress() {
        val outputs = listOf(
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 1,
                amount = 100,
                publicKey = ByteArray(32),
                blockHeight = 100,
                subaddressMajor = 0,
                subaddressMinor = 0
            ),
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 2,
                amount = 200,
                publicKey = ByteArray(32),
                blockHeight = 100,
                subaddressMajor = 0,
                subaddressMinor = 1
            ),
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 3,
                amount = 300,
                publicKey = ByteArray(32),
                blockHeight = 100,
                subaddressMajor = 0,
                subaddressMinor = 0
            )
        )
        
        val grouped = UtxoUtils.groupBySubaddress(outputs)
        
        assertEquals(2, grouped.size)
        assertEquals(2, grouped[Pair(0, 0)]?.size)
        assertEquals(1, grouped[Pair(0, 1)]?.size)
    }
    
    @Test
    fun testTotalBalance() {
        val outputs = listOf(
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 1,
                amount = 100_000_000,
                publicKey = ByteArray(32),
                blockHeight = 100
            ),
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 2,
                amount = 200_000_000,
                publicKey = ByteArray(32),
                blockHeight = 100
            )
        )
        
        assertEquals(300_000_000, UtxoUtils.totalBalance(outputs))
    }
    
    @Test
    fun testUnlockedBalance() {
        val outputs = listOf(
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 1,
                amount = 100_000_000,
                publicKey = ByteArray(32),
                blockHeight = 100,
                isUnlocked = true
            ),
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 2,
                amount = 200_000_000,
                publicKey = ByteArray(32),
                blockHeight = 990,  // Only 10 confirmations
                isUnlocked = true
            ),
            SpendableOutput(
                txHash = ByteArray(32),
                outputIndex = 0,
                globalIndex = 3,
                amount = 300_000_000,
                publicKey = ByteArray(32),
                blockHeight = 100,
                isUnlocked = false  // Locked
            )
        )
        
        // Current height 1000, min confirmations 10
        // Only output 1 (height 100) and output 2 (height 990) have enough confirmations
        // But output 3 is locked
        val unlocked = UtxoUtils.unlockedBalance(outputs, currentHeight = 1000, minConfirmations = 10)
        assertEquals(300_000_000, unlocked)  // 100M + 200M
    }
}
