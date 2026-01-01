package io.monero.core.transaction

import kotlin.test.*
import kotlin.random.Random

class DecoySelectionTest {
    
    @Test
    fun testRingMemberEquality() {
        val member1 = RingMember(
            globalIndex = 12345,
            publicKey = ByteArray(32) { 0x01 }
        )
        val member2 = RingMember(
            globalIndex = 12345,
            publicKey = ByteArray(32) { 0x02 }  // Different key
        )
        val member3 = RingMember(
            globalIndex = 99999,
            publicKey = ByteArray(32) { 0x01 }
        )
        
        // Equality based on globalIndex only
        assertEquals(member1, member2)
        assertNotEquals(member1, member3)
    }
    
    @Test
    fun testOutputDistributionCumulative() {
        val distribution = OutputDistribution(
            startHeight = 100,
            distribution = listOf(10, 25, 50, 80, 100),
            base = 1000
        )
        
        assertEquals(1000, distribution.cumulativeAtHeight(99))  // Before start
        assertEquals(1010, distribution.cumulativeAtHeight(100))  // First
        assertEquals(1025, distribution.cumulativeAtHeight(101))
        assertEquals(1100, distribution.cumulativeAtHeight(104))  // Last
        assertEquals(1100, distribution.cumulativeAtHeight(200))  // After end
    }
    
    @Test
    fun testOutputDistributionHeightLookup() {
        val distribution = OutputDistribution(
            startHeight = 100,
            distribution = listOf(10, 25, 50, 80, 100),
            base = 1000
        )
        
        // Output 1005 should be at height 100 (first 10 outputs)
        assertEquals(100, distribution.heightForOutputIndex(1005))
        
        // Output 1020 should be at height 101 (outputs 10-25)
        assertEquals(101, distribution.heightForOutputIndex(1020))
        
        // Output 1060 should be at height 103 (outputs 50-80)
        assertEquals(103, distribution.heightForOutputIndex(1060))
    }
    
    @Test
    fun testDecoySelectionConfig() {
        val config = DecoySelectionConfig()
        
        assertEquals(16, config.ringSize)
        assertEquals(19.28, config.gammaShape)
        assertTrue(config.useGammaDistribution)
    }
    
    @Test
    fun testRingValidatorValidRing() {
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 500,
            amount = 1000000,
            publicKey = ByteArray(32),
            blockHeight = 100
        )
        
        val ring = (0 until 16).map { i ->
            RingMember(
                globalIndex = 100L + i * 50,  // 100, 150, 200, ...
                publicKey = ByteArray(32) { i.toByte() }
            )
        }.toMutableList()
        
        // Insert real output at correct position
        ring[8] = RingMember(
            globalIndex = 500,
            publicKey = realOutput.publicKey
        )
        
        // Sort by global index
        val sortedRing = ring.sortedBy { it.globalIndex }
        
        val config = DecoySelectionConfig(ringSize = 16)
        assertTrue(RingValidator.isValidRing(sortedRing, realOutput, config))
    }
    
    @Test
    fun testRingValidatorInvalidSize() {
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 100,
            amount = 1000000,
            publicKey = ByteArray(32),
            blockHeight = 100
        )
        
        val ring = (0 until 10).map { i ->
            RingMember(globalIndex = i.toLong() * 10, publicKey = ByteArray(32))
        }
        
        val config = DecoySelectionConfig(ringSize = 16)
        assertFalse(RingValidator.isValidRing(ring, realOutput, config))
    }
    
    @Test
    fun testRingValidatorMissingRealOutput() {
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 99999,  // Not in ring
            amount = 1000000,
            publicKey = ByteArray(32),
            blockHeight = 100
        )
        
        val ring = (0 until 16).map { i ->
            RingMember(globalIndex = i.toLong() * 10, publicKey = ByteArray(32))
        }
        
        val config = DecoySelectionConfig(ringSize = 16)
        assertFalse(RingValidator.isValidRing(ring, realOutput, config))
    }
    
    @Test
    fun testRingValidatorNotSorted() {
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 50,
            amount = 1000000,
            publicKey = ByteArray(32),
            blockHeight = 100
        )
        
        val ring = (0 until 16).map { i ->
            RingMember(globalIndex = (15 - i).toLong() * 10, publicKey = ByteArray(32))
        }
        
        val config = DecoySelectionConfig(ringSize = 16)
        assertFalse(RingValidator.isValidRing(ring, realOutput, config))
    }
    
    @Test
    fun testRingValidatorDuplicates() {
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 50,
            amount = 1000000,
            publicKey = ByteArray(32),
            blockHeight = 100
        )
        
        val ring = (0 until 16).map { i ->
            RingMember(
                globalIndex = if (i == 15) 0 else i.toLong() * 10,  // Duplicate at end
                publicKey = ByteArray(32)
            )
        }.sortedBy { it.globalIndex }
        
        val config = DecoySelectionConfig(ringSize = 16)
        assertFalse(RingValidator.isValidRing(ring, realOutput, config))
    }
}

class MockOutputProvider : OutputProvider {
    var totalOutputs = 10_000_000L
    var currentHeight = 3_000_000L
    
    override suspend fun getOutputDistribution(fromHeight: Long, toHeight: Long?): OutputDistribution {
        // Create a realistic distribution covering many blocks
        val size = 100_000
        val startHeight = 1L
        return OutputDistribution(
            startHeight = startHeight,
            distribution = (0 until size).map { it * 100L },  // 100 outputs per block
            base = 0L
        )
    }
    
    override suspend fun getOutputs(indices: List<Long>): List<RingMember> {
        return indices.map { idx ->
            RingMember(
                globalIndex = idx,
                publicKey = ByteArray(32) { (idx % 256).toByte() },
                commitment = ByteArray(32) { ((idx + 1) % 256).toByte() }
            )
        }
    }
    
    override suspend fun getTotalOutputCount(): Long = totalOutputs
    
    override suspend fun getCurrentHeight(): Long = currentHeight
}

class DecoySelectorIntegrationTest {
    
    @Test
    fun testSelectDecoysReturnsCorrectRingSize() = kotlinx.coroutines.test.runTest {
        val provider = MockOutputProvider()
        val config = DecoySelectionConfig(ringSize = 16)
        val selector = DecoySelector(provider, config, Random(42))
        
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 5_000_000,
            amount = 1_000_000_000,
            publicKey = ByteArray(32) { 0xAB.toByte() },
            blockHeight = 2_900_000
        )
        
        val ring = selector.selectDecoys(realOutput, provider.currentHeight)
        
        assertEquals(16, ring.size)
        assertTrue(ring.any { it.globalIndex == realOutput.globalIndex })
    }
    
    @Test
    fun testSelectDecoysIsSorted() = kotlinx.coroutines.test.runTest {
        val provider = MockOutputProvider()
        val selector = DecoySelector(provider, random = Random(123))
        
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 5_000_000,
            amount = 1_000_000_000,
            publicKey = ByteArray(32),
            blockHeight = 2_900_000
        )
        
        val ring = selector.selectDecoys(realOutput, provider.currentHeight)
        
        // Verify sorted
        for (i in 1 until ring.size) {
            assertTrue(ring[i].globalIndex > ring[i - 1].globalIndex)
        }
    }
    
    @Test
    fun testGetRealOutputPosition() = kotlinx.coroutines.test.runTest {
        val provider = MockOutputProvider()
        val selector = DecoySelector(provider, random = Random(456))
        
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 5_000_000,
            amount = 1_000_000_000,
            publicKey = ByteArray(32),
            blockHeight = 2_900_000
        )
        
        val ring = selector.selectDecoys(realOutput, provider.currentHeight)
        val position = selector.getRealOutputPosition(ring, realOutput.globalIndex)
        
        assertTrue(position in 0 until ring.size)
        assertEquals(realOutput.globalIndex, ring[position].globalIndex)
    }
    
    @Test
    fun testSelectDecoysNoDuplicates() = kotlinx.coroutines.test.runTest {
        val provider = MockOutputProvider()
        val selector = DecoySelector(provider, random = Random(789))
        
        val realOutput = SpendableOutput(
            txHash = ByteArray(32),
            outputIndex = 0,
            globalIndex = 5_000_000,
            amount = 1_000_000_000,
            publicKey = ByteArray(32),
            blockHeight = 2_900_000
        )
        
        val ring = selector.selectDecoys(realOutput, provider.currentHeight)
        
        val uniqueIndices = ring.map { it.globalIndex }.toSet()
        assertEquals(ring.size, uniqueIndices.size)
    }
}
