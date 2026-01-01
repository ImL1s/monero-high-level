package io.monero.core.sync

import io.monero.core.scanner.ViewKeyScanner
import io.monero.core.transaction.OwnedOutput
import io.monero.core.transaction.Transaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Sync state representing the current synchronization status
 */
sealed class SyncState {
    /** Not started syncing */
    data object Idle : SyncState()
    
    /** Currently syncing blocks */
    data class Syncing(
        val currentHeight: Long,
        val targetHeight: Long,
        val blocksProcessed: Long,
        val startTime: Long = System.currentTimeMillis()
    ) : SyncState() {
        val progress: Float get() = if (targetHeight > 0) currentHeight.toFloat() / targetHeight else 0f
        val blocksPerSecond: Float get() {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000f
            return if (elapsed > 0) blocksProcessed / elapsed else 0f
        }
        val estimatedSecondsRemaining: Float get() {
            val remaining = targetHeight - currentHeight
            return if (blocksPerSecond > 0) remaining / blocksPerSecond else Float.MAX_VALUE
        }
    }
    
    /** Sync completed */
    data class Synced(val height: Long) : SyncState()
    
    /** Sync error occurred */
    data class Error(val message: String, val cause: Throwable? = null) : SyncState()
}

/**
 * Events emitted during synchronization
 */
sealed class SyncEvent {
    /** New block processed */
    data class BlockProcessed(val height: Long, val hash: ByteArray) : SyncEvent()
    
    /** New transaction found */
    data class TransactionFound(val tx: Transaction) : SyncEvent()
    
    /** New output received */
    data class OutputReceived(val output: OwnedOutput) : SyncEvent()
    
    /** Output spent */
    data class OutputSpent(val keyImage: ByteArray) : SyncEvent()
    
    /** Chain reorganization detected */
    data class ReorgDetected(val fromHeight: Long, val toHeight: Long) : SyncEvent()
    
    /** Sync progress update */
    data class ProgressUpdate(val current: Long, val target: Long) : SyncEvent()
}

/**
 * Configuration for sync manager
 */
data class SyncConfig(
    /** Number of blocks to fetch in parallel */
    val batchSize: Int = 100,
    
    /** Delay between batches (ms) */
    val batchDelayMs: Long = 100,
    
    /** Number of confirmations required */
    val confirmations: Int = 10,
    
    /** Auto-restart on error */
    val autoRetry: Boolean = true,
    
    /** Maximum retry attempts */
    val maxRetries: Int = 3,
    
    /** Retry delay (ms) */
    val retryDelayMs: Long = 5000
)

/**
 * Block data from daemon
 */
data class BlockData(
    val height: Long,
    val hash: ByteArray,
    val timestamp: Long,
    val prevHash: ByteArray,
    val transactions: List<Transaction>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BlockData
        return height == other.height && hash.contentEquals(other.hash)
    }
    
    override fun hashCode(): Int = hash.contentHashCode()
}

/**
 * Interface for block data provider (implemented by DaemonClient)
 */
interface BlockProvider {
    suspend fun getHeight(): Long
    suspend fun getBlockByHeight(height: Long): BlockData
    suspend fun getBlocksByRange(startHeight: Long, endHeight: Long): List<BlockData>
}

/**
 * Interface for wallet storage
 */
interface WalletStorage {
    suspend fun getLastSyncedHeight(): Long
    suspend fun setLastSyncedHeight(height: Long)
    suspend fun getBlockHash(height: Long): ByteArray?
    suspend fun setBlockHash(height: Long, hash: ByteArray)
    suspend fun saveOutput(output: OwnedOutput)
    suspend fun markOutputSpent(keyImage: ByteArray)
    suspend fun rollbackToHeight(height: Long)
}

/**
 * Manages blockchain synchronization for a wallet.
 * 
 * Coordinates block fetching, transaction scanning, and state management.
 */
class SyncManager(
    private val scanner: ViewKeyScanner,
    private val blockProvider: BlockProvider,
    private val storage: WalletStorage,
    private val config: SyncConfig = SyncConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<SyncEvent>(replay = 0, extraBufferCapacity = 100)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()
    
    private var syncJob: Job? = null
    private var retryCount = 0
    
    /**
     * Start synchronization from the last synced height
     */
    fun start(scope: CoroutineScope) {
        if (syncJob?.isActive == true) return
        
        syncJob = scope.launch(dispatcher) {
            try {
                sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e, scope)
            }
        }
    }
    
    /**
     * Stop synchronization
     */
    fun stop() {
        syncJob?.cancel()
        syncJob = null
        _state.value = SyncState.Idle
    }
    
    /**
     * Check if currently syncing
     */
    val isSyncing: Boolean get() = _state.value is SyncState.Syncing
    
    private suspend fun sync() {
        val startHeight = storage.getLastSyncedHeight() + 1
        val targetHeight = blockProvider.getHeight()
        
        if (startHeight > targetHeight) {
            _state.value = SyncState.Synced(targetHeight)
            return
        }
        
        val startTime = System.currentTimeMillis()
        var blocksProcessed = 0L
        var currentHeight = startHeight
        
        _state.value = SyncState.Syncing(currentHeight, targetHeight, blocksProcessed, startTime)
        
        while (currentHeight <= targetHeight) {
            // Check for reorg before processing
            if (currentHeight > 0) {
                val reorgHeight = checkForReorg(currentHeight - 1)
                if (reorgHeight != null) {
                    currentHeight = handleReorg(reorgHeight)
                    continue
                }
            }
            
            // Fetch batch of blocks
            val endHeight = minOf(currentHeight + config.batchSize - 1, targetHeight)
            val blocks = blockProvider.getBlocksByRange(currentHeight, endHeight)
            
            // Process each block
            for (block in blocks) {
                processBlock(block)
                blocksProcessed++
                currentHeight = block.height + 1
                
                _state.value = SyncState.Syncing(currentHeight, targetHeight, blocksProcessed, startTime)
                _events.emit(SyncEvent.ProgressUpdate(currentHeight, targetHeight))
            }
            
            // Update target height (blockchain may have grown)
            val newTargetHeight = blockProvider.getHeight()
            if (newTargetHeight > targetHeight) {
                _state.value = SyncState.Syncing(currentHeight, newTargetHeight, blocksProcessed, startTime)
            }
            
            // Small delay between batches to avoid overwhelming the node
            if (config.batchDelayMs > 0 && currentHeight <= targetHeight) {
                delay(config.batchDelayMs)
            }
        }
        
        retryCount = 0
        _state.value = SyncState.Synced(currentHeight - 1)
    }
    
    private suspend fun processBlock(block: BlockData) {
        // Save block hash for reorg detection
        storage.setBlockHash(block.height, block.hash)
        
        // Scan each transaction
        for (tx in block.transactions) {
            val ownedOutputs = scanner.scanTransaction(tx)
            
            if (ownedOutputs.isNotEmpty()) {
                _events.emit(SyncEvent.TransactionFound(tx))
                
                for (scanned in ownedOutputs) {
                    if (scanned.isOwned) {
                        val owned = OwnedOutput(
                            txHash = tx.hash,
                            outputIndex = scanned.output.index,
                            globalIndex = scanned.output.globalIndex,
                            amount = scanned.amount ?: 0L,
                            publicKey = scanned.output.target.key,
                            blockHeight = block.height,
                            timestamp = block.timestamp,
                            subaddressMajor = scanned.subaddressIndex?.first ?: 0,
                            subaddressMinor = scanned.subaddressIndex?.second ?: 0
                        )
                        storage.saveOutput(owned)
                        _events.emit(SyncEvent.OutputReceived(owned))
                    }
                }
            }
            
            // Check for spent outputs (key images in inputs)
            for (input in tx.inputs) {
                storage.markOutputSpent(input.keyImage)
                _events.emit(SyncEvent.OutputSpent(input.keyImage))
            }
        }
        
        // Update last synced height
        storage.setLastSyncedHeight(block.height)
        _events.emit(SyncEvent.BlockProcessed(block.height, block.hash))
    }
    
    /**
     * Check for blockchain reorganization
     * Returns the height where reorg occurred, or null if no reorg
     */
    private suspend fun checkForReorg(height: Long): Long? {
        val storedHash = storage.getBlockHash(height) ?: return null
        val block = blockProvider.getBlockByHeight(height)
        
        if (!block.hash.contentEquals(storedHash)) {
            // Reorg detected, find the fork point
            var checkHeight = height - 1
            while (checkHeight > 0) {
                val stored = storage.getBlockHash(checkHeight) ?: break
                val current = blockProvider.getBlockByHeight(checkHeight)
                if (current.hash.contentEquals(stored)) {
                    return checkHeight + 1 // Fork point
                }
                checkHeight--
            }
            return 0 // Complete reorg from genesis
        }
        
        return null
    }
    
    /**
     * Handle blockchain reorganization
     * Returns the height to restart syncing from
     */
    private suspend fun handleReorg(reorgHeight: Long): Long {
        val currentHeight = storage.getLastSyncedHeight()
        _events.emit(SyncEvent.ReorgDetected(reorgHeight, currentHeight))
        
        // Rollback storage to before the reorg
        storage.rollbackToHeight(reorgHeight - 1)
        
        return reorgHeight
    }
    
    private suspend fun handleError(e: Exception, scope: CoroutineScope) {
        _state.value = SyncState.Error(e.message ?: "Unknown error", e)
        
        if (config.autoRetry && retryCount < config.maxRetries) {
            retryCount++
            delay(config.retryDelayMs)
            start(scope)
        }
    }
}
