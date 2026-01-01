package io.monero.wallet.internal

import io.monero.core.KeyDerivation
import io.monero.core.Mnemonic
import io.monero.core.MoneroAddress
import io.monero.core.generateSecureRandom
import io.monero.wallet.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigInteger

/**
 * Internal wallet state containing keys and derived data.
 */
internal data class WalletState(
    /** Wallet configuration */
    val config: WalletConfig,
    /** Wallet type */
    val type: WalletType,
    /** Private spend key (null for view-only) */
    val privateSpendKey: ByteArray?,
    /** Private view key */
    val privateViewKey: ByteArray,
    /** Public spend key */
    val publicSpendKey: ByteArray,
    /** Public view key */
    val publicViewKey: ByteArray,
    /** Seed for mnemonic derivation (null for view-only/key-restored) */
    val seed: ByteArray?,
    /** Restore height */
    var restoreHeight: Long,
    /** Accounts (mutable) */
    val accounts: MutableList<AccountState> = mutableListOf()
) {
    init {
        // Ensure at least one account exists
        if (accounts.isEmpty()) {
            accounts.add(AccountState(0, "Primary account"))
        }
    }

    /** Primary address */
    val primaryAddress: MoneroAddress by lazy {
        MoneroAddress.fromKeys(
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey,
            network = config.network.toMoneroNetwork()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalletState) return false
        return publicSpendKey.contentEquals(other.publicSpendKey)
    }

    override fun hashCode(): Int = publicSpendKey.contentHashCode()
}

/**
 * Account state within a wallet.
 */
internal data class AccountState(
    val index: Int,
    var label: String,
    val subaddresses: MutableList<SubaddressState> = mutableListOf()
) {
    init {
        // Ensure primary subaddress exists
        if (subaddresses.isEmpty()) {
            subaddresses.add(SubaddressState(0, ""))
        }
    }
}

/**
 * Subaddress state.
 */
internal data class SubaddressState(
    val index: Int,
    var label: String,
    var used: Boolean = false
)

/**
 * Extension to convert wallet Network to MoneroAddress.Network
 */
internal fun Network.toMoneroNetwork(): MoneroAddress.Network = when (this) {
    Network.MAINNET -> MoneroAddress.Network.MAINNET
    Network.STAGENET -> MoneroAddress.Network.STAGENET
    Network.TESTNET -> MoneroAddress.Network.TESTNET
}

/**
 * In-memory wallet implementation.
 *
 * This is a minimal implementation for K5.0 that stores wallet state in memory.
 * Persistent storage will be added in K5.1.
 */
internal class InMemoryWallet(
    internal val state: WalletState
) : MoneroWallet {

    private val _balance = MutableStateFlow(WalletBalance.ZERO)
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.NotStarted)
    private val _syncHeight = MutableStateFlow(state.restoreHeight)

    override val primaryAddress: String
        get() = state.primaryAddress.rawAddress

    override val balance: StateFlow<WalletBalance>
        get() = _balance.asStateFlow()

    override val syncStatus: StateFlow<SyncStatus>
        get() = _syncStatus.asStateFlow()

    override val syncHeight: StateFlow<Long>
        get() = _syncHeight.asStateFlow()

    override val network: Network
        get() = state.config.network

    override val isViewOnly: Boolean
        get() = state.type == WalletType.VIEW_ONLY

    override suspend fun sync(startHeight: Long?) {
        val height = startHeight ?: state.restoreHeight
        _syncStatus.value = SyncStatus.Syncing(height, height)
        // Actual sync implementation will be in SyncManager integration
        _syncStatus.value = SyncStatus.Synced
    }

    override fun stopSync() {
        if (_syncStatus.value is SyncStatus.Syncing) {
            _syncStatus.value = SyncStatus.NotStarted
        }
    }

    override suspend fun refresh() {
        // Quick refresh - just update from current height
        sync(_syncHeight.value)
    }

    override suspend fun createTransaction(config: TxConfig): PendingTransaction {
        if (state.type == WalletType.VIEW_ONLY) {
            throw WalletException.ViewOnlyWallet("create transaction")
        }
        // Will be integrated with TxBuilder
        TODO("Transaction creation to be implemented with TxBuilder integration")
    }

    override suspend fun submitTransaction(tx: PendingTransaction): String {
        if (state.type == WalletType.OFFLINE) {
            throw WalletException.OfflineWallet("submit transaction")
        }
        // Will be integrated with DaemonClient
        TODO("Transaction submission to be implemented")
    }

    override fun getAddress(accountIndex: Int, addressIndex: Int): String {
        val account = state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        if (addressIndex >= account.subaddresses.size) {
            throw WalletException.SubaddressNotFound(accountIndex, addressIndex)
        }

        return deriveAddress(accountIndex, addressIndex)
    }

    override fun getAddresses(accountIndex: Int): List<SubaddressInfo> {
        val account = state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        return account.subaddresses.map { sub ->
            SubaddressInfo(
                accountIndex = accountIndex,
                addressIndex = sub.index,
                address = deriveAddress(accountIndex, sub.index),
                label = sub.label,
                balance = BigInteger.ZERO, // Will be computed from UTXOs
                unlockedBalance = BigInteger.ZERO,
                used = sub.used
            )
        }
    }

    override suspend fun createSubaddress(accountIndex: Int, label: String): SubaddressInfo {
        val account = state.accounts.getOrNull(accountIndex)
            ?: throw WalletException.AccountNotFound(accountIndex)

        val newIndex = account.subaddresses.size
        val newSub = SubaddressState(newIndex, label)
        account.subaddresses.add(newSub)

        return SubaddressInfo(
            accountIndex = accountIndex,
            addressIndex = newIndex,
            address = deriveAddress(accountIndex, newIndex),
            label = label,
            balance = BigInteger.ZERO,
            unlockedBalance = BigInteger.ZERO,
            used = false
        )
    }

    override suspend fun getTransactions(
        accountIndex: Int?,
        subaddressIndex: Int?,
        pending: Boolean
    ): List<TransactionInfo> {
        // Will be implemented with storage
        return emptyList()
    }

    override suspend fun getOutputs(spent: Boolean, frozen: Boolean): List<OutputInfo> {
        // Will be implemented with storage
        return emptyList()
    }

    override suspend fun freezeOutput(keyImage: ByteArray) {
        // Will be implemented with storage
    }

    override suspend fun thawOutput(keyImage: ByteArray) {
        // Will be implemented with storage
    }

    override suspend fun exportOutputs(all: Boolean): ByteArray {
        // Will be implemented for offline signing
        return ByteArray(0)
    }

    override suspend fun importOutputs(data: ByteArray): Int {
        return 0
    }

    override suspend fun exportKeyImages(all: Boolean): List<KeyImageExport> {
        return emptyList()
    }

    override suspend fun importKeyImages(keyImages: List<KeyImageExport>): KeyImageImportResult {
        return KeyImageImportResult(0, BigInteger.ZERO, BigInteger.ZERO)
    }

    override suspend fun close() {
        save()
        // Release resources
    }

    override suspend fun save() {
        // Will be implemented with storage (K5.1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun deriveAddress(accountIndex: Int, addressIndex: Int): String {
        if (accountIndex == 0 && addressIndex == 0) {
            return state.primaryAddress.rawAddress
        }

        val keys = KeyDerivation.WalletKeys(
            seed = state.seed ?: ByteArray(32),
            privateSpendKey = state.privateSpendKey ?: ByteArray(32),
            privateViewKey = state.privateViewKey,
            publicSpendKey = state.publicSpendKey,
            publicViewKey = state.publicViewKey
        )

        return KeyDerivation.deriveSubaddressAddress(
            keys = keys,
            account = accountIndex,
            index = addressIndex,
            network = state.config.network.toMoneroNetwork()
        ).rawAddress
    }

    internal fun updateBalance(balance: WalletBalance) {
        _balance.value = balance
    }

    internal fun updateSyncHeight(height: Long) {
        _syncHeight.value = height
    }
}

/**
 * Factory for creating wallets.
 */
internal object WalletFactory {

    /**
     * Create a new wallet with fresh seed.
     */
    fun createNew(config: WalletConfig): Pair<InMemoryWallet, List<String>> {
        val seed = generateSecureRandom(32)
        val mnemonic = Mnemonic.entropyToMnemonic(seed)
        val keys = KeyDerivation.deriveWalletKeys(seed)

        val state = WalletState(
            config = config,
            type = WalletType.FULL,
            privateSpendKey = keys.privateSpendKey,
            privateViewKey = keys.privateViewKey,
            publicSpendKey = keys.publicSpendKey,
            publicViewKey = keys.publicViewKey,
            seed = seed,
            restoreHeight = 0
        )

        return InMemoryWallet(state) to mnemonic
    }

    /**
     * Restore wallet from mnemonic.
     */
    fun restoreFromMnemonic(
        config: WalletConfig,
        mnemonic: List<String>,
        restoreHeight: Long
    ): InMemoryWallet {
        val seed = Mnemonic.mnemonicToEntropy(mnemonic)
        val keys = KeyDerivation.deriveWalletKeys(seed)

        val state = WalletState(
            config = config,
            type = WalletType.FULL,
            privateSpendKey = keys.privateSpendKey,
            privateViewKey = keys.privateViewKey,
            publicSpendKey = keys.publicSpendKey,
            publicViewKey = keys.publicViewKey,
            seed = seed,
            restoreHeight = restoreHeight
        )

        return InMemoryWallet(state)
    }

    /**
     * Restore wallet from keys.
     */
    fun restoreFromKeys(
        config: WalletConfig,
        publicSpendKey: ByteArray,
        publicViewKey: ByteArray,
        privateViewKey: ByteArray,
        privateSpendKey: ByteArray?,
        restoreHeight: Long
    ): InMemoryWallet {
        val type = if (privateSpendKey != null) WalletType.FULL else WalletType.VIEW_ONLY

        val state = WalletState(
            config = config,
            type = type,
            privateSpendKey = privateSpendKey,
            privateViewKey = privateViewKey,
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey,
            seed = null,
            restoreHeight = restoreHeight
        )

        return InMemoryWallet(state)
    }

    /**
     * Create an offline wallet (has spend key but no network).
     */
    fun createOffline(
        config: WalletConfig,
        mnemonic: List<String>
    ): InMemoryWallet {
        val seed = Mnemonic.mnemonicToEntropy(mnemonic)
        val keys = KeyDerivation.deriveWalletKeys(seed)

        val state = WalletState(
            config = config.copy(daemonAddress = ""), // No daemon
            type = WalletType.OFFLINE,
            privateSpendKey = keys.privateSpendKey,
            privateViewKey = keys.privateViewKey,
            publicSpendKey = keys.publicSpendKey,
            publicViewKey = keys.publicViewKey,
            seed = seed,
            restoreHeight = 0
        )

        return InMemoryWallet(state)
    }
}
