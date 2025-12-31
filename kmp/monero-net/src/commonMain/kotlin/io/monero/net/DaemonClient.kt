package io.monero.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Monero Daemon RPC client interface.
 *
 * Supports both HTTP and ZMQ connections to monerod.
 * Uses JSON-RPC 2.0 protocol.
 */
interface DaemonClient {
    /**
     * Get daemon info
     */
    suspend fun getInfo(): DaemonInfo

    /**
     * Get current blockchain height
     */
    suspend fun getHeight(): Long

    /**
     * Get block by height
     */
    suspend fun getBlockByHeight(height: Long): BlockInfo

    /**
     * Get blocks in range
     */
    suspend fun getBlocks(startHeight: Long, endHeight: Long): List<BlockInfo>

    /**
     * Get outputs for key offsets (for decoy selection)
     */
    suspend fun getOutputs(offsets: List<Long>): List<OutputInfo>

    /**
     * Submit raw transaction
     */
    suspend fun sendRawTransaction(txBlob: ByteArray): TxSubmitResult

    /**
     * Get fee estimate
     */
    suspend fun getFeeEstimate(): FeeEstimate

    /**
     * Get transaction pool (mempool)
     */
    suspend fun getTransactionPool(): List<PoolTransaction>

    /**
     * Close connection
     */
    fun close()
}

/**
 * Daemon connection configuration
 */
data class DaemonConfig(
    val host: String = "localhost",
    val port: Int = 18081,
    val useSsl: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val timeoutMs: Long = 30_000
)

/**
 * Daemon info response
 */
@Serializable
data class DaemonInfo(
    val height: Long,
    val targetHeight: Long,
    val difficulty: Long,
    val txCount: Long,
    val txPoolSize: Int,
    val altBlocksCount: Int,
    val outgoingConnectionsCount: Int,
    val incomingConnectionsCount: Int,
    val whitePoolSize: Int,
    val greyPoolSize: Int,
    val mainnet: Boolean,
    val testnet: Boolean,
    val stagenet: Boolean,
    val topBlockHash: String,
    val synchronized: Boolean,
    val version: String
)

/**
 * Block information
 */
@Serializable
data class BlockInfo(
    val height: Long,
    val hash: String,
    val timestamp: Long,
    val prevHash: String,
    val nonce: Long,
    val txHashes: List<String>,
    val minerTx: String? = null
)

/**
 * Output information for decoy selection
 */
@Serializable
data class OutputInfo(
    val height: Long,
    val key: String,
    val mask: String,
    val txid: String,
    val unlocked: Boolean
)

/**
 * Transaction submission result
 */
@Serializable
data class TxSubmitResult(
    val success: Boolean,
    val reason: String? = null,
    val doubleSpend: Boolean = false,
    val feeTooLow: Boolean = false,
    val invalidInput: Boolean = false,
    val invalidOutput: Boolean = false,
    val tooBig: Boolean = false,
    val overspend: Boolean = false,
    val txExtraTooBig: Boolean = false
)

/**
 * Fee estimate
 */
@Serializable
data class FeeEstimate(
    val feePerByte: Long,
    val quantizationMask: Long,
    val fees: List<Long> // per priority level
)

/**
 * Pool (mempool) transaction
 */
@Serializable
data class PoolTransaction(
    val txHash: String,
    val blobSize: Int,
    val fee: Long,
    val receivedTime: Long,
    val keptByBlock: Boolean
)
