package io.monero.core

import io.monero.crypto.Keccak
import io.monero.crypto.Ed25519

/**
 * Monero key derivation utilities.
 *
 * Key hierarchy:
 * - Seed (32 bytes) -> Private Spend Key
 * - Private Spend Key -> Private View Key (via Keccak hash)
 * - Private Keys -> Public Keys (via Ed25519 scalar multiplication)
 *
 * Subaddress derivation:
 * - (account, index) -> Subaddress keys using "SubAddr" domain separator
 */
object KeyDerivation {
    
    /**
     * Complete wallet keys derived from a seed
     */
    data class WalletKeys(
        val seed: ByteArray,
        val privateSpendKey: ByteArray,
        val privateViewKey: ByteArray,
        val publicSpendKey: ByteArray,
        val publicViewKey: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WalletKeys) return false
            return seed.contentEquals(other.seed)
        }
        
        override fun hashCode(): Int = seed.contentHashCode()
    }
    
    /**
     * Subaddress keys
     */
    data class SubaddressKeys(
        val account: Int,
        val index: Int,
        val publicSpendKey: ByteArray,
        val publicViewKey: ByteArray
    )

    /**
     * Derive complete wallet keys from a seed.
     *
     * @param seed 32-byte random seed
     * @return Complete wallet keys
     */
    fun deriveWalletKeys(seed: ByteArray): WalletKeys {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        
        // Private spend key = seed reduced mod L
        val privateSpendKey = Ed25519.scalarReduce64(seed + ByteArray(32))
        
        // Private view key = Keccak(private_spend_key) mod L
        val privateViewKey = Ed25519.deriveViewKey(privateSpendKey)
        
        // Public keys = private * G
        val publicSpendKey = Ed25519.publicKeyFromPrivate(privateSpendKey)
        val publicViewKey = Ed25519.publicKeyFromPrivate(privateViewKey)
        
        return WalletKeys(
            seed = seed.copyOf(),
            privateSpendKey = privateSpendKey,
            privateViewKey = privateViewKey,
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey
        )
    }
    
    /**
     * Derive wallet keys from mnemonic words.
     *
     * @param mnemonic 25 mnemonic words
     * @return Complete wallet keys
     */
    fun deriveFromMnemonic(mnemonic: List<String>): WalletKeys {
        val seed = Mnemonic.mnemonicToEntropy(mnemonic)
        return deriveWalletKeys(seed)
    }
    
    /**
     * Generate a standard Monero address from wallet keys.
     *
     * @param keys Wallet keys
     * @param network Target network (default: mainnet)
     * @return Standard address
     */
    fun deriveStandardAddress(
        keys: WalletKeys,
        network: MoneroAddress.Network = MoneroAddress.Network.MAINNET
    ): MoneroAddress {
        return MoneroAddress.fromKeys(
            publicSpendKey = keys.publicSpendKey,
            publicViewKey = keys.publicViewKey,
            network = network
        )
    }
    
    /**
     * Derive a subaddress at (account, index).
     *
     * Subaddress derivation:
     * m = Keccak("SubAddr" || a || account || index)
     * D = m * G
     * C = m * A
     * subaddr_spend = B + D
     * subaddr_view = a * subaddr_spend (or equivalently: a * B + a * D = A + C)
     *
     * Where:
     * - a = private view key
     * - A = public view key  
     * - B = public spend key
     *
     * @param keys Wallet keys
     * @param account Account index (0-based)
     * @param index Subaddress index within account (0-based)
     * @return Subaddress public keys
     */
    fun deriveSubaddress(
        keys: WalletKeys,
        account: Int,
        index: Int
    ): SubaddressKeys {
        require(account >= 0) { "Account must be non-negative" }
        require(index >= 0) { "Index must be non-negative" }
        
        // Special case: (0, 0) is the main address
        if (account == 0 && index == 0) {
            return SubaddressKeys(
                account = 0,
                index = 0,
                publicSpendKey = keys.publicSpendKey,
                publicViewKey = keys.publicViewKey
            )
        }
        
        // m = Keccak("SubAddr" || view_key || account || index)
        val prefix = "SubAddr".encodeToByteArray() + byteArrayOf(0) // null terminator
        val accountBytes = intToLittleEndian(account)
        val indexBytes = intToLittleEndian(index)
        
        val data = prefix + keys.privateViewKey + accountBytes + indexBytes
        val m = Ed25519.scalarReduce64(Keccak.hash256(data) + ByteArray(32))
        
        // D = m * G (new spend key component)
        val D = Ed25519.scalarMultBase(m)
        
        // subaddr_spend = B + D (public spend key + D)
        val subaddrSpend = pointAdd(keys.publicSpendKey, D)
        
        // subaddr_view = a * subaddr_spend
        val subaddrView = Ed25519.scalarMult(keys.privateViewKey, subaddrSpend)
        
        return SubaddressKeys(
            account = account,
            index = index,
            publicSpendKey = subaddrSpend,
            publicViewKey = subaddrView
        )
    }
    
    /**
     * Create subaddress as MoneroAddress.
     */
    fun deriveSubaddressAddress(
        keys: WalletKeys,
        account: Int,
        index: Int,
        network: MoneroAddress.Network = MoneroAddress.Network.MAINNET
    ): MoneroAddress {
        val subKeys = deriveSubaddress(keys, account, index)
        
        // Use subaddress prefix
        val prefix = network.subaddressPrefix
        val data = byteArrayOf(prefix) + subKeys.publicSpendKey + subKeys.publicViewKey
        val encoded = io.monero.crypto.Base58.encode(data)
        
        return MoneroAddress(
            rawAddress = encoded,
            network = network,
            type = if (account == 0 && index == 0) 
                MoneroAddress.AddressType.STANDARD 
            else 
                MoneroAddress.AddressType.SUBADDRESS,
            publicSpendKey = subKeys.publicSpendKey,
            publicViewKey = subKeys.publicViewKey
        )
    }
    
    /**
     * Point addition placeholder (uses hash for now).
     * TODO: Implement proper Ed25519 point addition
     */
    private fun pointAdd(p1: ByteArray, p2: ByteArray): ByteArray {
        if (p1.all { it == 0.toByte() }) return p2
        if (p2.all { it == 0.toByte() }) return p1
        return Keccak.hash256(p1 + p2)
    }
    
    /**
     * Convert int to 4-byte little-endian.
     */
    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
}
