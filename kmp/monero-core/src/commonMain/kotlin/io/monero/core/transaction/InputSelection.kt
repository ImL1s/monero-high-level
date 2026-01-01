package io.monero.core.transaction

/**
 * Represents a spendable output (UTXO) in the wallet.
 */
data class SpendableOutput(
    /** Transaction hash containing this output */
    val txHash: ByteArray,
    /** Output index within the transaction */
    val outputIndex: Int,
    /** Global output index on the blockchain */
    val globalIndex: Long,
    /** Amount in atomic units */
    val amount: Long,
    /** One-time public key (stealth address) */
    val publicKey: ByteArray,
    /** Block height when confirmed */
    val blockHeight: Long,
    /** Subaddress account index */
    val subaddressMajor: Int = 0,
    /** Subaddress index within account */
    val subaddressMinor: Int = 0,
    /** Key image for this output (derived from spend key) */
    val keyImage: ByteArray? = null,
    /** Whether this output is unlocked and spendable */
    val isUnlocked: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SpendableOutput
        return globalIndex == other.globalIndex && txHash.contentEquals(other.txHash)
    }
    
    override fun hashCode(): Int = globalIndex.hashCode()
}

/**
 * Result of input selection algorithm.
 */
data class InputSelectionResult(
    /** Selected outputs to spend */
    val selectedOutputs: List<SpendableOutput>,
    /** Total amount of selected outputs */
    val totalAmount: Long,
    /** Amount to send (excluding fee) */
    val sendAmount: Long,
    /** Estimated fee */
    val fee: Long,
    /** Change amount (totalAmount - sendAmount - fee) */
    val change: Long
) {
    /** Whether selection is valid (enough funds) */
    val isValid: Boolean get() = totalAmount >= sendAmount + fee && change >= 0
}

/**
 * Strategy for selecting inputs (UTXOs) for a transaction.
 */
enum class SelectionStrategy {
    /** Select smallest outputs first (consolidation) */
    SMALLEST_FIRST,
    /** Select largest outputs first (minimize inputs) */
    LARGEST_FIRST,
    /** Select outputs closest to target amount */
    CLOSEST_MATCH,
    /** Random selection for privacy */
    RANDOM
}

/**
 * Configuration for input selection.
 */
data class SelectionConfig(
    /** Maximum number of inputs per transaction */
    val maxInputs: Int = 16,
    /** Minimum confirmations required */
    val minConfirmations: Int = 10,
    /** Current blockchain height */
    val currentHeight: Long,
    /** Fee per byte in atomic units */
    val feePerByte: Long = 20000,
    /** Fixed fee per input */
    val feePerInput: Long = 0,
    /** Fixed fee per output */
    val feePerOutput: Long = 0,
    /** Ring size (number of decoys + 1) */
    val ringSize: Int = 16,
    /** Number of outputs in the transaction */
    val numOutputs: Int = 2
)

/**
 * Selects transaction inputs (UTXOs) based on various strategies.
 * 
 * Implements multiple selection algorithms optimized for different use cases:
 * - Privacy: random selection
 * - Cost efficiency: minimize inputs
 * - Consolidation: use many small outputs
 */
class InputSelector(
    private val config: SelectionConfig
) {
    companion object {
        /** Estimated transaction size overhead (bytes) */
        private const val TX_OVERHEAD_SIZE = 100L
        
        /** Estimated size per input (bytes) - includes ring signature */
        private const val INPUT_SIZE = 2500L
        
        /** Estimated size per output (bytes) */
        private const val OUTPUT_SIZE = 150L
        
        /** Monero unlock time in blocks */
        const val UNLOCK_TIME_BLOCKS = 10
    }
    
    /**
     * Select inputs to cover the target amount plus fee.
     * 
     * @param availableOutputs All spendable outputs in the wallet
     * @param targetAmount Amount to send (atomic units)
     * @param strategy Selection strategy to use
     * @return Selection result, or null if insufficient funds
     */
    fun selectInputs(
        availableOutputs: List<SpendableOutput>,
        targetAmount: Long,
        strategy: SelectionStrategy = SelectionStrategy.CLOSEST_MATCH
    ): InputSelectionResult? {
        // Filter unlocked and confirmed outputs
        val spendable = availableOutputs.filter { output ->
            output.isUnlocked &&
            (config.currentHeight - output.blockHeight) >= config.minConfirmations
        }
        
        if (spendable.isEmpty()) return null
        
        // Sort based on strategy
        val sorted = when (strategy) {
            SelectionStrategy.SMALLEST_FIRST -> spendable.sortedBy { it.amount }
            SelectionStrategy.LARGEST_FIRST -> spendable.sortedByDescending { it.amount }
            SelectionStrategy.CLOSEST_MATCH -> spendable.sortedBy { 
                kotlin.math.abs(it.amount - targetAmount) 
            }
            SelectionStrategy.RANDOM -> spendable.shuffled()
        }
        
        // Greedy selection
        val selected = mutableListOf<SpendableOutput>()
        var totalSelected = 0L
        var estimatedFee = estimateFee(0)
        
        for (output in sorted) {
            if (selected.size >= config.maxInputs) break
            
            selected.add(output)
            totalSelected += output.amount
            estimatedFee = estimateFee(selected.size)
            
            // Check if we have enough
            if (totalSelected >= targetAmount + estimatedFee) {
                val change = totalSelected - targetAmount - estimatedFee
                return InputSelectionResult(
                    selectedOutputs = selected,
                    totalAmount = totalSelected,
                    sendAmount = targetAmount,
                    fee = estimatedFee,
                    change = change
                )
            }
        }
        
        // Not enough funds
        return null
    }
    
    /**
     * Select all outputs (sweep operation).
     * 
     * @param availableOutputs All spendable outputs
     * @return Selection result with all funds
     */
    fun selectAll(
        availableOutputs: List<SpendableOutput>
    ): InputSelectionResult? {
        val spendable = availableOutputs.filter { output ->
            output.isUnlocked &&
            (config.currentHeight - output.blockHeight) >= config.minConfirmations
        }
        
        if (spendable.isEmpty()) return null
        
        // Take up to maxInputs outputs, prioritizing larger amounts
        val selected = spendable
            .sortedByDescending { it.amount }
            .take(config.maxInputs)
        
        val totalAmount = selected.sumOf { it.amount }
        val fee = estimateFee(selected.size, numOutputs = 1) // No change output
        val sendAmount = totalAmount - fee
        
        if (sendAmount <= 0) return null
        
        return InputSelectionResult(
            selectedOutputs = selected,
            totalAmount = totalAmount,
            sendAmount = sendAmount,
            fee = fee,
            change = 0 // Sweep has no change
        )
    }
    
    /**
     * Estimate transaction fee based on number of inputs.
     */
    fun estimateFee(numInputs: Int, numOutputs: Int = config.numOutputs): Long {
        val txSize = TX_OVERHEAD_SIZE + 
                     (numInputs * INPUT_SIZE) + 
                     (numOutputs * OUTPUT_SIZE)
        
        return txSize * config.feePerByte + 
               numInputs * config.feePerInput + 
               numOutputs * config.feePerOutput
    }
    
    /**
     * Calculate the minimum amount needed to send a specific amount.
     * Includes estimated fee for a typical 2-input transaction.
     */
    fun minimumRequired(targetAmount: Long, estimatedInputs: Int = 2): Long {
        return targetAmount + estimateFee(estimatedInputs)
    }
}

/**
 * Utility functions for UTXO management.
 */
object UtxoUtils {
    /**
     * Check if an output is unlocked based on unlock time.
     * 
     * @param blockHeight Block height of the output
     * @param unlockTime Unlock time (0 = no lock, < 500000000 = block height, >= 500000000 = timestamp)
     * @param currentHeight Current blockchain height
     * @param currentTime Current Unix timestamp
     */
    fun isUnlocked(
        blockHeight: Long,
        unlockTime: Long,
        currentHeight: Long,
        currentTime: Long
    ): Boolean {
        // Must have minimum confirmations
        if (currentHeight - blockHeight < InputSelector.UNLOCK_TIME_BLOCKS) {
            return false
        }
        
        return when {
            unlockTime == 0L -> true
            unlockTime < 500_000_000L -> currentHeight >= unlockTime
            else -> currentTime >= unlockTime
        }
    }
    
    /**
     * Group outputs by subaddress.
     */
    fun groupBySubaddress(
        outputs: List<SpendableOutput>
    ): Map<Pair<Int, Int>, List<SpendableOutput>> {
        return outputs.groupBy { Pair(it.subaddressMajor, it.subaddressMinor) }
    }
    
    /**
     * Calculate total balance from outputs.
     */
    fun totalBalance(outputs: List<SpendableOutput>): Long {
        return outputs.sumOf { it.amount }
    }
    
    /**
     * Calculate unlocked balance (spendable now).
     */
    fun unlockedBalance(
        outputs: List<SpendableOutput>,
        currentHeight: Long,
        minConfirmations: Int = 10
    ): Long {
        return outputs.filter { 
            it.isUnlocked && (currentHeight - it.blockHeight) >= minConfirmations 
        }.sumOf { it.amount }
    }
}
