package io.monero.wallet

import java.math.BigInteger

/**
 * Wallet type indicating available operations.
 */
enum class WalletType {
    /** Full wallet with spend key - can sign and send transactions */
    FULL,
    /** View-only wallet - can see incoming transactions but cannot spend */
    VIEW_ONLY,
    /** Offline wallet - has spend key but no network access, for cold signing */
    OFFLINE
}

/**
 * Account information within a wallet.
 */
data class AccountInfo(
    /** Account index (0-based) */
    val index: Int,
    /** Account label */
    val label: String,
    /** Primary address for this account */
    val primaryAddress: String,
    /** Total balance (confirmed + unconfirmed) */
    val balance: BigInteger,
    /** Unlocked (spendable) balance */
    val unlockedBalance: BigInteger,
    /** Number of subaddresses in this account */
    val subaddressCount: Int
) {
    companion object {
        fun empty(index: Int) = AccountInfo(
            index = index,
            label = if (index == 0) "Primary account" else "Account #$index",
            primaryAddress = "",
            balance = BigInteger.ZERO,
            unlockedBalance = BigInteger.ZERO,
            subaddressCount = 1
        )
    }
}

/**
 * Extended wallet interface with account and lifecycle management.
 *
 * This extends the base [MoneroWallet] with:
 * - Account creation and management
 * - Subaddress labels
 * - Wallet type detection
 * - Restore height management
 */
interface WalletManager {

    // ─────────────────────────────────────────────────────────────────────────
    // Wallet Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Create a new wallet with a fresh seed.
     *
     * @param config Wallet configuration (path, password, network)
     * @return Created wallet with generated mnemonic
     */
    suspend fun createWallet(config: WalletConfig): WalletCreationResult

    /**
     * Restore wallet from mnemonic seed.
     *
     * @param config Wallet configuration
     * @param mnemonic 25-word mnemonic seed
     * @param restoreHeight Block height to start scanning from
     * @return Restored wallet
     */
    suspend fun restoreFromMnemonic(
        config: WalletConfig,
        mnemonic: List<String>,
        restoreHeight: Long = 0
    ): MoneroWallet

    /**
     * Restore wallet from keys (view-only or full).
     *
     * @param config Wallet configuration
     * @param address Primary address
     * @param viewKey Private view key (hex)
     * @param spendKey Private spend key (hex, null for view-only)
     * @param restoreHeight Block height to start scanning from
     * @return Restored wallet
     */
    suspend fun restoreFromKeys(
        config: WalletConfig,
        address: String,
        viewKey: String,
        spendKey: String? = null,
        restoreHeight: Long = 0
    ): MoneroWallet

    /**
     * Open an existing wallet file.
     *
     * @param config Wallet configuration with path and password
     * @return Opened wallet
     * @throws WalletException if wallet doesn't exist or password is wrong
     */
    suspend fun openWallet(config: WalletConfig): MoneroWallet

    /**
     * Close wallet and release resources.
     * Saves state before closing.
     */
    suspend fun closeWallet(wallet: MoneroWallet)

    /**
     * Check if a wallet file exists at the given path.
     */
    fun walletExists(path: String): Boolean

    // ─────────────────────────────────────────────────────────────────────────
    // Wallet Info
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Get wallet type (full, view-only, offline).
     */
    fun getWalletType(wallet: MoneroWallet): WalletType

    /**
     * Get the mnemonic seed (only for full wallets).
     *
     * @throws WalletException if wallet is view-only
     */
    suspend fun getMnemonic(wallet: MoneroWallet): List<String>

    /**
     * Get wallet restore height.
     */
    fun getRestoreHeight(wallet: MoneroWallet): Long

    /**
     * Set wallet restore height (for re-scanning).
     */
    suspend fun setRestoreHeight(wallet: MoneroWallet, height: Long)
}

/**
 * Account management operations.
 */
interface AccountManager {

    /**
     * Get all accounts in the wallet.
     */
    suspend fun getAccounts(wallet: MoneroWallet): List<AccountInfo>

    /**
     * Get a specific account.
     */
    suspend fun getAccount(wallet: MoneroWallet, accountIndex: Int): AccountInfo

    /**
     * Create a new account.
     *
     * @param label Optional label for the account
     * @return New account info
     */
    suspend fun createAccount(wallet: MoneroWallet, label: String = ""): AccountInfo

    /**
     * Set account label.
     */
    suspend fun setAccountLabel(wallet: MoneroWallet, accountIndex: Int, label: String)

    /**
     * Get balance for a specific account.
     */
    suspend fun getAccountBalance(wallet: MoneroWallet, accountIndex: Int): WalletBalance

    /**
     * Get total wallet balance (sum of all accounts).
     */
    suspend fun getTotalBalance(wallet: MoneroWallet): WalletBalance
}

/**
 * Subaddress management operations.
 */
interface SubaddressManager {

    /**
     * Get all subaddresses for an account.
     */
    suspend fun getSubaddresses(wallet: MoneroWallet, accountIndex: Int): List<SubaddressInfo>

    /**
     * Create a new subaddress.
     *
     * @param accountIndex Account to create subaddress in
     * @param label Optional label
     * @return New subaddress info
     */
    suspend fun createSubaddress(
        wallet: MoneroWallet,
        accountIndex: Int,
        label: String = ""
    ): SubaddressInfo

    /**
     * Set subaddress label.
     */
    suspend fun setSubaddressLabel(
        wallet: MoneroWallet,
        accountIndex: Int,
        addressIndex: Int,
        label: String
    )

    /**
     * Get subaddress by indices.
     */
    suspend fun getSubaddress(
        wallet: MoneroWallet,
        accountIndex: Int,
        addressIndex: Int
    ): SubaddressInfo

    /**
     * Find which account/subaddress an address belongs to.
     *
     * @return Pair of (accountIndex, addressIndex) or null if not found
     */
    suspend fun findSubaddress(wallet: MoneroWallet, address: String): Pair<Int, Int>?

    /**
     * Get the number of subaddresses in an account.
     */
    suspend fun getSubaddressCount(wallet: MoneroWallet, accountIndex: Int): Int
}

/**
 * Result of wallet creation.
 */
data class WalletCreationResult(
    val wallet: MoneroWallet,
    val mnemonic: List<String>,
    val primaryAddress: String
)

/**
 * Wallet-related exceptions.
 */
sealed class WalletException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class WalletNotFound(path: String) : WalletException("Wallet not found at: $path")
    class InvalidPassword : WalletException("Invalid wallet password")
    class InvalidMnemonic(reason: String) : WalletException("Invalid mnemonic: $reason")
    class ViewOnlyWallet(operation: String) : WalletException("Cannot $operation with view-only wallet")
    class OfflineWallet(operation: String) : WalletException("Cannot $operation with offline wallet")
    class AccountNotFound(index: Int) : WalletException("Account not found: $index")
    class SubaddressNotFound(account: Int, address: Int) : WalletException("Subaddress not found: $account/$address")
    class StorageError(message: String, cause: Throwable? = null) : WalletException(message, cause)
}
