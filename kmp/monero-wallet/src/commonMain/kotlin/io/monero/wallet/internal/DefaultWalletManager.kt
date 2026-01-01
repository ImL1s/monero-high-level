package io.monero.wallet.internal

import io.monero.core.Mnemonic
import io.monero.core.MoneroAddress
import io.monero.wallet.*


/**
 * Default implementation of WalletManager, AccountManager, and SubaddressManager.
 *
 * This implementation uses in-memory storage for K5.0.
 * Persistent storage will be added in K5.1.
 */
class DefaultWalletManager : WalletManager, AccountManager, SubaddressManager {

    // Track open wallets
    private val openWallets = mutableMapOf<String, InMemoryWallet>()

    // ─────────────────────────────────────────────────────────────────────────
    // WalletManager implementation
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun createWallet(config: WalletConfig): WalletCreationResult {
        val (wallet, mnemonic) = WalletFactory.createNew(config)
        openWallets[config.path] = wallet

        return WalletCreationResult(
            wallet = wallet,
            mnemonic = mnemonic,
            primaryAddress = wallet.primaryAddress
        )
    }

    override suspend fun restoreFromMnemonic(
        config: WalletConfig,
        mnemonic: List<String>,
        restoreHeight: Long
    ): MoneroWallet {
        // Validate mnemonic
        if (!Mnemonic.validate(mnemonic)) {
            throw WalletException.InvalidMnemonic("Invalid mnemonic words or checksum")
        }

        val wallet = WalletFactory.restoreFromMnemonic(config, mnemonic, restoreHeight)
        openWallets[config.path] = wallet
        return wallet
    }

    override suspend fun restoreFromKeys(
        config: WalletConfig,
        address: String,
        viewKey: String,
        spendKey: String?,
        restoreHeight: Long
    ): MoneroWallet {
        // Parse address to get public keys
        val parsedAddress = try {
            MoneroAddress.parse(address)
        } catch (e: Exception) {
            throw WalletException.InvalidMnemonic("Invalid address: ${e.message}")
        }

        val privateViewKey = viewKey.hexToBytes()
        val privateSpendKey = spendKey?.hexToBytes()

        val wallet = WalletFactory.restoreFromKeys(
            config = config,
            publicSpendKey = parsedAddress.publicSpendKey,
            publicViewKey = parsedAddress.publicViewKey,
            privateViewKey = privateViewKey,
            privateSpendKey = privateSpendKey,
            restoreHeight = restoreHeight
        )

        openWallets[config.path] = wallet
        return wallet
    }

    override suspend fun openWallet(config: WalletConfig): MoneroWallet {
        // Check if already open
        openWallets[config.path]?.let { return it }

        // For in-memory implementation, we can't actually "open" a saved wallet
        // This will be implemented properly in K5.1 with persistent storage
        throw WalletException.WalletNotFound(config.path)
    }

    override suspend fun closeWallet(wallet: MoneroWallet) {
        wallet.close()
        val inMemory = wallet as? InMemoryWallet ?: return
        openWallets.remove(inMemory.state.config.path)
    }

    override fun walletExists(path: String): Boolean {
        // For in-memory implementation, check if wallet is open
        // Persistent storage check will be added in K5.1
        return openWallets.containsKey(path)
    }

    override fun getWalletType(wallet: MoneroWallet): WalletType {
        val inMemory = wallet as? InMemoryWallet
            ?: return if (wallet.isViewOnly) WalletType.VIEW_ONLY else WalletType.FULL
        return inMemory.state.type
    }

    override suspend fun getMnemonic(wallet: MoneroWallet): List<String> {
        val inMemory = wallet as? InMemoryWallet
            ?: throw WalletException.ViewOnlyWallet("get mnemonic")

        if (inMemory.state.type == WalletType.VIEW_ONLY) {
            throw WalletException.ViewOnlyWallet("get mnemonic")
        }

        val seed = inMemory.state.seed
            ?: throw WalletException.ViewOnlyWallet("get mnemonic (no seed)")

        return Mnemonic.entropyToMnemonic(seed)
    }

    override fun getRestoreHeight(wallet: MoneroWallet): Long {
        val inMemory = wallet as? InMemoryWallet ?: return 0
        return inMemory.state.restoreHeight
    }

    override suspend fun setRestoreHeight(wallet: MoneroWallet, height: Long) {
        val inMemory = wallet as? InMemoryWallet ?: return
        inMemory.state.restoreHeight = height
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AccountManager implementation
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun getAccounts(wallet: MoneroWallet): List<AccountInfo> {
        val inMemory = wallet as? InMemoryWallet
            ?: return listOf(AccountInfo.empty(0))

        return inMemory.state.accounts.map { account ->
            AccountInfo(
                index = account.index,
                label = account.label,
                primaryAddress = wallet.getAddress(account.index, 0),
                balance = 0L, // Will be computed from UTXOs
                unlockedBalance = 0L,
                subaddressCount = account.subaddresses.size
            )
        }
    }

    override suspend fun getAccount(wallet: MoneroWallet, accountIndex: Int): AccountInfo {
        val inMemory = wallet as? InMemoryWallet
            ?: throw WalletException.AccountNotFound(accountIndex)

        val account = inMemory.state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        return AccountInfo(
            index = account.index,
            label = account.label,
            primaryAddress = wallet.getAddress(accountIndex, 0),
            balance = 0L,
            unlockedBalance = 0L,
            subaddressCount = account.subaddresses.size
        )
    }

    override suspend fun createAccount(wallet: MoneroWallet, label: String): AccountInfo {
        val inMemory = wallet as? InMemoryWallet
            ?: throw IllegalStateException("Unsupported wallet type")

        val newIndex = inMemory.state.accounts.size
        val newLabel = label.ifEmpty { "Account #$newIndex" }
        val newAccount = AccountState(newIndex, newLabel)
        inMemory.state.accounts.add(newAccount)

        return AccountInfo(
            index = newIndex,
            label = newLabel,
            primaryAddress = wallet.getAddress(newIndex, 0),
            balance = 0L,
            unlockedBalance = 0L,
            subaddressCount = 1
        )
    }

    override suspend fun setAccountLabel(wallet: MoneroWallet, accountIndex: Int, label: String) {
        val inMemory = wallet as? InMemoryWallet ?: return

        val account = inMemory.state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        account.label = label
    }

    override suspend fun getAccountBalance(wallet: MoneroWallet, accountIndex: Int): WalletBalance {
        // Will be computed from UTXOs in K5.1
        return WalletBalance.ZERO
    }

    override suspend fun getTotalBalance(wallet: MoneroWallet): WalletBalance {
        return wallet.balance.value
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SubaddressManager implementation
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun getSubaddresses(wallet: MoneroWallet, accountIndex: Int): List<SubaddressInfo> {
        return wallet.getAddresses(accountIndex)
    }

    override suspend fun createSubaddress(
        wallet: MoneroWallet,
        accountIndex: Int,
        label: String
    ): SubaddressInfo {
        return wallet.createSubaddress(accountIndex, label)
    }

    override suspend fun setSubaddressLabel(
        wallet: MoneroWallet,
        accountIndex: Int,
        addressIndex: Int,
        label: String
    ) {
        val inMemory = wallet as? InMemoryWallet ?: return

        val account = inMemory.state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        val subaddress = account.subaddresses.getOrNull(addressIndex)
            ?: throw WalletException.SubaddressNotFound(accountIndex, addressIndex)

        subaddress.label = label
    }

    override suspend fun getSubaddress(
        wallet: MoneroWallet,
        accountIndex: Int,
        addressIndex: Int
    ): SubaddressInfo {
        val addresses = wallet.getAddresses(accountIndex)
        return addresses.getOrNull(addressIndex)
            ?: throw WalletException.SubaddressNotFound(accountIndex, addressIndex)
    }

    override suspend fun findSubaddress(wallet: MoneroWallet, address: String): Pair<Int, Int>? {
        val inMemory = wallet as? InMemoryWallet ?: return null

        for (account in inMemory.state.accounts) {
            for (sub in account.subaddresses) {
                val derivedAddress = wallet.getAddress(account.index, sub.index)
                if (derivedAddress == address) {
                    return account.index to sub.index
                }
            }
        }
        return null
    }

    override suspend fun getSubaddressCount(wallet: MoneroWallet, accountIndex: Int): Int {
        val inMemory = wallet as? InMemoryWallet
            ?: throw WalletException.AccountNotFound(accountIndex)

        val account = inMemory.state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        return account.subaddresses.size
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
