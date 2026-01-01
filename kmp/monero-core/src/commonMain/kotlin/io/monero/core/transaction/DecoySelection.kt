package io.monero.core.transaction

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Ring member (decoy) information.
 */
data class RingMember(
    /** Global output index on the blockchain */
    val globalIndex: Long,
    /** Output public key */
    val publicKey: ByteArray,
    /** Commitment for RingCT outputs */
    val commitment: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as RingMember
        return globalIndex == other.globalIndex
    }
    
    override fun hashCode(): Int = globalIndex.hashCode()
}

/**
 * Output distribution data for gamma selection.
 */
data class OutputDistribution(
    /** Starting height of the distribution */
    val startHeight: Long,
    /** Distribution values (cumulative output count per block) */
    val distribution: List<Long>,
    /** Base output count */
    val base: Long
) {
    val endHeight: Long get() = startHeight + distribution.size - 1
    
    /**
     * Get the cumulative output count at a given height.
     */
    fun cumulativeAtHeight(height: Long): Long {
        if (height < startHeight) return base
        val index = (height - startHeight).toInt()
        if (index >= distribution.size) return base + distribution.last()
        return base + distribution[index]
    }
    
    /**
     * Get block height for a given global output index.
     */
    fun heightForOutputIndex(globalIndex: Long): Long {
        if (globalIndex <= base) return startHeight
        
        val adjustedIndex = globalIndex - base
        var low = 0
        var high = distribution.size - 1
        
        while (low < high) {
            val mid = (low + high) / 2
            if (distribution[mid] < adjustedIndex) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        
        return startHeight + low
    }
}

/**
 * Configuration for decoy selection.
 */
data class DecoySelectionConfig(
    /** Ring size (number of ring members including real output) */
    val ringSize: Int = 16,
    
    /** Minimum number of recent outputs to avoid */
    val recentOutputsCount: Int = 50,
    
    /** Gamma distribution shape parameter */
    val gammaShape: Double = 19.28,
    
    /** Gamma distribution scale parameter */
    val gammaScale: Double = 1.0 / 1.61,
    
    /** Minimum age of decoys in blocks */
    val minDecoyAge: Int = 10,
    
    /** Use gamma distribution (true) or uniform (false) */
    val useGammaDistribution: Boolean = true
)

/**
 * Interface for fetching output information from the network.
 */
interface OutputProvider {
    /**
     * Get output distribution for decoy selection.
     */
    suspend fun getOutputDistribution(
        fromHeight: Long = 0,
        toHeight: Long? = null
    ): OutputDistribution
    
    /**
     * Get outputs by global indices.
     */
    suspend fun getOutputs(indices: List<Long>): List<RingMember>
    
    /**
     * Get the total number of outputs on the blockchain.
     */
    suspend fun getTotalOutputCount(): Long
    
    /**
     * Get current blockchain height.
     */
    suspend fun getCurrentHeight(): Long
}

/**
 * Selects decoy outputs (ring members) for transaction inputs.
 * 
 * Uses the gamma distribution algorithm from Monero to select
 * decoys that match the spending patterns of real outputs.
 */
class DecoySelector(
    private val outputProvider: OutputProvider,
    private val config: DecoySelectionConfig = DecoySelectionConfig(),
    private val random: Random = Random.Default
) {
    companion object {
        /** Approximate average seconds between blocks */
        private const val BLOCK_TIME_SECONDS = 120
        
        /** Seconds per day */
        private const val SECONDS_PER_DAY = 86400
        
        /** Recent cutoff in blocks (~2 weeks) */
        private const val RECENT_CUTOFF_BLOCKS = 7 * 24 * 30  // ~7 days worth
    }
    
    /**
     * Select ring members for a transaction input.
     * 
     * @param realOutput The real output being spent
     * @param currentHeight Current blockchain height
     * @return List of ring members including the real output, sorted by global index
     */
    suspend fun selectDecoys(
        realOutput: SpendableOutput,
        currentHeight: Long
    ): List<RingMember> {
        val distribution = outputProvider.getOutputDistribution()
        val totalOutputs = outputProvider.getTotalOutputCount()
        
        // Number of decoys needed
        val numDecoys = config.ringSize - 1
        
        // Select decoy indices
        val selectedIndices = mutableSetOf<Long>()
        selectedIndices.add(realOutput.globalIndex)  // Include real output
        
        var attempts = 0
        val maxAttempts = numDecoys * 200
        
        while (selectedIndices.size < config.ringSize && attempts < maxAttempts) {
            attempts++
            
            // Try gamma first, fall back to uniform if it fails too often
            val candidateIndex = if (config.useGammaDistribution && attempts < maxAttempts / 2) {
                selectGammaIndex(totalOutputs, distribution, currentHeight)
            } else {
                selectUniformIndex(totalOutputs)
            }
            
            // Validate candidate - use < totalOutputs, not >= 
            if (candidateIndex < 1 || candidateIndex > totalOutputs - 1) continue
            if (candidateIndex in selectedIndices) continue
            
            // Check age requirements (skip if distribution doesn't cover)
            val candidateHeight = distribution.heightForOutputIndex(candidateIndex)
            if (candidateHeight > 0 && currentHeight > candidateHeight) {
                if (currentHeight - candidateHeight < config.minDecoyAge) continue
            }
            
            selectedIndices.add(candidateIndex)
        }
        
        if (selectedIndices.size < config.ringSize) {
            throw IllegalStateException("Could not select enough decoys: ${selectedIndices.size}/${config.ringSize}")
        }
        
        // Fetch output data
        val outputs = outputProvider.getOutputs(selectedIndices.toList())
        
        // Sort by global index (required for ring signatures)
        return outputs.sortedBy { it.globalIndex }
    }
    
    /**
     * Select an output index using gamma distribution.
     * 
     * The gamma distribution models the spending behavior of outputs,
     * with most outputs being spent within a few days of receiving them.
     */
    private fun selectGammaIndex(
        totalOutputs: Long,
        distribution: OutputDistribution,
        currentHeight: Long
    ): Long {
        // Sample from gamma distribution
        val gammaSample = sampleGamma(config.gammaShape, config.gammaScale)
        
        // Convert to block age
        val ageBlocks = (gammaSample * SECONDS_PER_DAY / BLOCK_TIME_SECONDS).toLong()
        
        // Calculate target height
        val targetHeight = maxOf(0, currentHeight - ageBlocks)
        
        // Get output range at target height
        val outputsAtHeight = distribution.cumulativeAtHeight(targetHeight)
        val outputsAtNextHeight = distribution.cumulativeAtHeight(targetHeight + 1)
        
        // Random output within that block
        return if (outputsAtNextHeight > outputsAtHeight) {
            random.nextLong(outputsAtHeight, outputsAtNextHeight)
        } else {
            outputsAtHeight
        }
    }
    
    /**
     * Select an output index uniformly at random.
     */
    private fun selectUniformIndex(totalOutputs: Long): Long {
        return random.nextLong(1, totalOutputs)
    }
    
    /**
     * Sample from a gamma distribution using Marsaglia and Tsang's method.
     */
    private fun sampleGamma(shape: Double, scale: Double): Double {
        if (shape < 1) {
            // For shape < 1, use Ahrens-Dieter method
            return sampleGamma(1.0 + shape, scale) * random.nextDouble().pow(1.0 / shape)
        }
        
        val d = shape - 1.0 / 3.0
        val c = 1.0 / kotlin.math.sqrt(9.0 * d)
        
        while (true) {
            var x: Double
            var v: Double
            
            do {
                x = sampleNormal()
                v = 1.0 + c * x
            } while (v <= 0)
            
            v = v * v * v
            val u = random.nextDouble()
            
            if (u < 1.0 - 0.0331 * (x * x) * (x * x)) {
                return d * v * scale
            }
            
            if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) {
                return d * v * scale
            }
        }
    }
    
    /**
     * Sample from standard normal distribution using Box-Muller transform.
     */
    private fun sampleNormal(): Double {
        val u1 = random.nextDouble()
        val u2 = random.nextDouble()
        return kotlin.math.sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
    }
    
    /**
     * Get the position of the real output in the ring.
     * 
     * @param ring List of ring members sorted by global index
     * @param realGlobalIndex Global index of the real output
     * @return Position of real output (0-indexed)
     */
    fun getRealOutputPosition(ring: List<RingMember>, realGlobalIndex: Long): Int {
        return ring.indexOfFirst { it.globalIndex == realGlobalIndex }
    }
}

/**
 * Validates ring member selection.
 */
object RingValidator {
    /**
     * Check if a ring is valid.
     */
    fun isValidRing(
        ring: List<RingMember>,
        realOutput: SpendableOutput,
        config: DecoySelectionConfig
    ): Boolean {
        // Check ring size
        if (ring.size != config.ringSize) return false
        
        // Check real output is included
        if (ring.none { it.globalIndex == realOutput.globalIndex }) return false
        
        // Check sorted by global index
        for (i in 1 until ring.size) {
            if (ring[i].globalIndex <= ring[i - 1].globalIndex) return false
        }
        
        // Check no duplicates
        if (ring.distinctBy { it.globalIndex }.size != ring.size) return false
        
        return true
    }
}
