package io.monero.wallet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow


/**
 * Main wallet interface for Monero operations.
 *
 * Provides high-level API for:
 * - Wallet creation and restoration
 * - Synchronization
 * - Balance queries
 * - Transaction building and submission
 */
interface MoneroWallet {
    /**
     * Primary wallet address
     */
    val primaryAddress: String

    /**
     * Wallet balance as observable flow
     */
    val balance: StateFlow<WalletBalance>

    /**
     * Sync status as observable flow
     */
    val syncStatus: StateFlow<SyncStatus>

    /**
     * Current sync height
     */
    val syncHeight: StateFlow<Long>

    /**
     * Network type (mainnet, stagenet, testnet)
     */
    val network: Network

    /**
     * Start synchronization
     *
     * @param startHeight Optional start height (default: wallet restore height or 0)
     */
    suspend fun sync(startHeight: Long? = null)

    /**
     * Stop synchronization
     */
    fun stopSync()

    /**
     * Refresh wallet (quick sync for new blocks only)
     */
    suspend fun refresh()

    /**
     * Create a new transaction
     *
     * @param config Transaction configuration
     * @return Pending transaction ready for submission
     */
    suspend fun createTransaction(config: TxConfig): PendingTransaction

    /**
     * Submit a signed transaction to the network
     *
     * @param tx Signed pending transaction
     * @return Transaction hash
     */
    suspend fun submitTransaction(tx: PendingTransaction): String

    /**
     * Get address for account and index
     *
     * @param accountIndex Account index (default: 0)
     * @param addressIndex Address index within account (default: 0)
     * @return Address string
     */
    fun getAddress(accountIndex: Int = 0, addressIndex: Int = 0): String

    /**
     * Get all addresses for an account
     */
    fun getAddresses(accountIndex: Int = 0): List<SubaddressInfo>

    /**
     * Create new subaddress
     *
     * @param accountIndex Account index
     * @param label Optional label
     * @return New subaddress info
     */
    suspend fun createSubaddress(accountIndex: Int = 0, label: String = ""): SubaddressInfo

    /**
     * Get transaction history
     *
     * @param accountIndex Optional filter by account
     * @param subaddressIndex Optional filter by subaddress
     * @param pending Include pending (unconfirmed) transactions
     */
    suspend fun getTransactions(
        accountIndex: Int? = null,
        subaddressIndex: Int? = null,
        pending: Boolean = true
    ): List<TransactionInfo>

    /**
     * Get outputs (UTXOs)
     *
     * @param spent Include spent outputs
     * @param frozen Include frozen outputs
     */
    suspend fun getOutputs(spent: Boolean = false, frozen: Boolean = true): List<OutputInfo>

    /**
     * Freeze an output (exclude from spending)
     */
    suspend fun freezeOutput(keyImage: ByteArray)

    /**
     * Thaw a frozen output (allow spending)
     */
    suspend fun thawOutput(keyImage: ByteArray)

    /**
     * Export outputs for offline signing
     */
    suspend fun exportOutputs(all: Boolean = false): ByteArray

    /**
     * Import outputs from offline wallet
     */
    suspend fun importOutputs(data: ByteArray): Int

    /**
     * Export key images
     */
    suspend fun exportKeyImages(all: Boolean = false): List<KeyImageExport>

    /**
     * Import key images
     */
    suspend fun importKeyImages(keyImages: List<KeyImageExport>): KeyImageImportResult

    /**
     * Close wallet and release resources
     */
    suspend fun close()

    /**
     * Save wallet state
     */
    suspend fun save()

    /**
     * Check if this is a view-only wallet
     */
    val isViewOnly: Boolean

    companion object {
        /**
         * Create a new wallet
         */
        suspend fun create(config: WalletConfig): MoneroWallet = TODO()

        /**
         * Restore wallet from mnemonic
         */
        suspend fun restore(
            config: WalletConfig,
            mnemonic: List<String>,
            restoreHeight: Long = 0
        ): MoneroWallet = TODO()

        /**
         * Open existing wallet
         */
        suspend fun open(config: WalletConfig): MoneroWallet = TODO()
    }
}

/**
 * Network type
 */
enum class Network {
    MAINNET,
    STAGENET,
    TESTNET
}

/**
 * Wallet configuration
 */
data class WalletConfig(
    val path: String,
    val password: String,
    val network: Network = Network.MAINNET,
    val daemonAddress: String = "localhost:18081"
)

/**
 * Wallet balance
 */
data class WalletBalance(
    val balance: Long,
    val unlockedBalance: Long,
    val pendingBalance: Long
) {
    companion object {
        val ZERO = WalletBalance(0L, 0L, 0L)
    }
}

/**
 * Synchronization status
 */
sealed class SyncStatus {
    data object NotStarted : SyncStatus()
    data class Syncing(val currentHeight: Long, val targetHeight: Long) : SyncStatus()
    data object Synced : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

/**
 * Transaction configuration
 */
data class TxConfig(
    val destinations: List<Destination>,
    val priority: TxPriority = TxPriority.DEFAULT,
    val accountIndex: Int = 0,
    val subaddressIndices: List<Int>? = null,
    val paymentId: ByteArray? = null,
    val sweepAll: Boolean = false
) {
    data class Destination(val address: String, val amount: Long)
}

/**
 * Transaction priority
 */
enum class TxPriority(val value: Int) {
    DEFAULT(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    HIGHEST(4)
}

/**
 * Pending (unsigned or signed) transaction
 */
interface PendingTransaction {
    val hash: String
    val fee: Long
    val amount: Long
    val blob: ByteArray
    val signed: Boolean
}

/**
 * Subaddress info
 */
data class SubaddressInfo(
    val accountIndex: Int,
    val addressIndex: Int,
    val address: String,
    val label: String,
    val balance: Long,
    val unlockedBalance: Long,
    val used: Boolean
)

/**
 * Transaction info
 */
data class TransactionInfo(
    val hash: String,
    val height: Long?,
    val timestamp: Long,
    val fee: Long,
    val amount: Long,
    val incoming: Boolean,
    val confirmations: Long,
    val accountIndex: Int,
    val subaddressIndices: List<Int>,
    val paymentId: String?,
    val note: String?
)

/**
 * Output info
 */
data class OutputInfo(
    val keyImage: ByteArray,
    val publicKey: ByteArray,
    val amount: Long,
    val globalIndex: Long,
    val height: Long,
    val accountIndex: Int,
    val subaddressIndex: Int,
    val spent: Boolean,
    val frozen: Boolean,
    val unlockTime: Long
)

/**
 * Key image export data
 */
data class KeyImageExport(
    val keyImage: ByteArray,
    val signature: ByteArray
)

/**
 * Key image import result
 */
data class KeyImageImportResult(
    val height: Long,
    val spent: Long,
    val unspent: Long
)
