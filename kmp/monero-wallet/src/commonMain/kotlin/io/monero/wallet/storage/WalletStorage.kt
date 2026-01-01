package io.monero.wallet.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wallet storage interface for persisting wallet data.
 */
interface WalletStorage {
    /**
     * Check if a wallet file exists at the given path.
     */
    suspend fun exists(path: String): Boolean

    /**
     * Save wallet data to the given path.
     * @param path The wallet file path
     * @param data The wallet data to save
     * @param password Password for encryption (will be used in K5.2)
     */
    suspend fun save(path: String, data: WalletData, password: String)

    /**
     * Load wallet data from the given path.
     * @param path The wallet file path
     * @param password Password for decryption (will be used in K5.2)
     * @return The loaded wallet data
     * @throws WalletStorageException if loading fails
     */
    suspend fun load(path: String, password: String): WalletData

    /**
     * Delete wallet file at the given path.
     */
    suspend fun delete(path: String): Boolean

    /**
     * List all wallet files in a directory.
     */
    suspend fun listWallets(directory: String): List<String>
}

/**
 * Wallet data model for persistence.
 */
@Serializable
data class WalletData(
    val version: Int = CURRENT_VERSION,
    val network: NetworkType,
    val createdAt: Long,
    val restoreHeight: Long,
    val primaryAddress: String,
    val privateViewKey: String,         // Hex encoded
    val publicViewKey: String,          // Hex encoded
    val privateSpendKey: String?,       // Hex encoded, null for view-only
    val publicSpendKey: String,         // Hex encoded
    val mnemonic: List<String>?,        // null for view-only or imported from keys
    val accounts: List<AccountData>,
    val transactions: List<TransactionRecord> = emptyList(),
    val addressBook: List<AddressBookEntry> = emptyList(),
    val outputs: List<OutputRecord> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
enum class NetworkType {
    MAINNET, STAGENET, TESTNET
}

@Serializable
data class AccountData(
    val index: Int,
    val label: String,
    val subaddresses: List<SubaddressData>
)

@Serializable
data class SubaddressData(
    val addressIndex: Int,
    val address: String,
    val label: String,
    val isUsed: Boolean = false
)

@Serializable
data class TransactionRecord(
    val txHash: String,
    val blockHeight: Long?,             // null if unconfirmed
    val timestamp: Long,
    val amount: Long,                   // Positive for incoming, negative for outgoing
    val fee: Long,
    val direction: TransactionDirection,
    val accountIndex: Int,
    val subaddressIndices: List<Int>,   // Subaddresses involved
    val paymentId: String?,
    val note: String?,
    val isConfirmed: Boolean,
    val confirmations: Int
)

@Serializable
enum class TransactionDirection {
    INCOMING, OUTGOING
}

@Serializable
data class AddressBookEntry(
    val id: String,
    val address: String,
    val label: String,
    val description: String?,
    val paymentId: String?,
    val createdAt: Long
)

@Serializable
data class OutputRecord(
    val txHash: String,
    val outputIndex: Int,
    val amount: Long,
    val keyImage: String?,              // null until spent or key image generated
    val globalIndex: Long?,
    val accountIndex: Int,
    val subaddressIndex: Int,
    val isSpent: Boolean,
    val isFrozen: Boolean,
    val spentHeight: Long?,
    val unlockTime: Long
)

/**
 * Storage exceptions
 */
sealed class WalletStorageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileNotFound(path: String) : WalletStorageException("Wallet file not found: $path")
    class InvalidPassword(path: String) : WalletStorageException("Invalid password for wallet: $path")
    class CorruptedData(path: String, cause: Throwable? = null) : WalletStorageException("Wallet data corrupted: $path", cause)
    class WriteError(path: String, cause: Throwable) : WalletStorageException("Failed to write wallet: $path", cause)
    class ReadError(path: String, cause: Throwable) : WalletStorageException("Failed to read wallet: $path", cause)
}

/**
 * JSON configuration for wallet serialization.
 */
val walletJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
