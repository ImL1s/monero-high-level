package io.monero.storage

import kotlinx.coroutines.flow.Flow

/**
 * Wallet storage interface.
 *
 * Handles persistence of wallet data including:
 * - Keys (encrypted)
 * - Outputs (UTXOs)
 * - Transactions
 * - Address book
 * - Sync state
 */
interface WalletStorage {
    /**
     * Open or create wallet storage
     *
     * @param path File path for storage
     * @param password Encryption password
     * @param create If true, create new storage; if false, open existing
     */
    suspend fun open(path: String, password: String, create: Boolean = false)

    /**
     * Close storage and release resources
     */
    suspend fun close()

    /**
     * Check if storage is open
     */
    fun isOpen(): Boolean

    /**
     * Change encryption password
     */
    suspend fun changePassword(oldPassword: String, newPassword: String)

    // Keys
    suspend fun saveKeys(keys: EncryptedKeys)
    suspend fun loadKeys(): EncryptedKeys?
    suspend fun hasKeys(): Boolean

    // Sync state
    suspend fun getSyncHeight(): Long
    suspend fun setSyncHeight(height: Long)
    fun observeSyncHeight(): Flow<Long>

    // Outputs
    suspend fun saveOutput(output: StoredOutput)
    suspend fun getOutputs(spent: Boolean? = null): List<StoredOutput>
    suspend fun getOutput(keyImage: ByteArray): StoredOutput?
    suspend fun markOutputSpent(keyImage: ByteArray, spendingTxHash: ByteArray)
    suspend fun deleteOutput(keyImage: ByteArray)

    // Transactions
    suspend fun saveTransaction(tx: StoredTransaction)
    suspend fun getTransaction(hash: ByteArray): StoredTransaction?
    suspend fun getTransactions(accountIndex: Int? = null): List<StoredTransaction>
    suspend fun updateTransactionHeight(hash: ByteArray, height: Long)

    // Accounts & Subaddresses
    suspend fun getAccountCount(): Int
    suspend fun addAccount(label: String): Int
    suspend fun setAccountLabel(index: Int, label: String)
    suspend fun getSubaddressCount(accountIndex: Int): Int
    suspend fun addSubaddress(accountIndex: Int, label: String): Int
    suspend fun setSubaddressLabel(accountIndex: Int, addressIndex: Int, label: String)

    // Address book
    suspend fun addAddressBookEntry(entry: AddressBookEntry): Long
    suspend fun getAddressBook(): List<AddressBookEntry>
    suspend fun deleteAddressBookEntry(id: Long)

    // Transaction notes
    suspend fun setTxNote(txHash: ByteArray, note: String)
    suspend fun getTxNote(txHash: ByteArray): String?
}

/**
 * Encrypted keys container
 */
data class EncryptedKeys(
    val encryptedSpendKey: ByteArray,
    val encryptedViewKey: ByteArray,
    val publicSpendKey: ByteArray,
    val publicViewKey: ByteArray,
    val salt: ByteArray,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedKeys) return false
        return publicSpendKey.contentEquals(other.publicSpendKey) &&
                publicViewKey.contentEquals(other.publicViewKey)
    }

    override fun hashCode(): Int = publicSpendKey.contentHashCode()
}

/**
 * Stored output (UTXO)
 */
data class StoredOutput(
    val keyImage: ByteArray,
    val publicKey: ByteArray,
    val amount: Long,
    val globalIndex: Long,
    val txHash: ByteArray,
    val localIndex: Int,
    val height: Long,
    val accountIndex: Int,
    val subaddressIndex: Int,
    val spent: Boolean,
    val spendingTxHash: ByteArray?,
    val frozen: Boolean,
    val unlockTime: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredOutput) return false
        return keyImage.contentEquals(other.keyImage)
    }

    override fun hashCode(): Int = keyImage.contentHashCode()
}

/**
 * Stored transaction
 */
data class StoredTransaction(
    val hash: ByteArray,
    val height: Long?, // null if in mempool
    val timestamp: Long,
    val fee: Long,
    val incoming: Boolean,
    val accountIndex: Int,
    val subaddressIndices: List<Int>,
    val amount: Long,
    val paymentId: ByteArray?,
    val note: String?
) {
    val isConfirmed: Boolean get() = height != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredTransaction) return false
        return hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int = hash.contentHashCode()
}

/**
 * Address book entry
 */
data class AddressBookEntry(
    val id: Long = 0,
    val address: String,
    val paymentId: String?,
    val description: String
)
