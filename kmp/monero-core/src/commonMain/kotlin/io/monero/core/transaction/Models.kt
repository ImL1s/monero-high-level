package io.monero.core.transaction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Monero transaction output types
 */
enum class OutputType {
    /** Standard output (pre-RingCT) */
    TXOUT_TO_KEY,
    /** RingCT output with encrypted amount */
    TXOUT_TO_TAGGED_KEY
}

/**
 * Represents a transaction output's public key component
 */
@Serializable
data class TxOutTarget(
    /** Output public key (stealth address) */
    val key: ByteArray,
    /** Optional view tag (1 byte) for faster scanning */
    val viewTag: Byte? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TxOutTarget
        return key.contentEquals(other.key) && viewTag == other.viewTag
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + (viewTag?.hashCode() ?: 0)
        return result
    }
}

/**
 * A single transaction output
 */
@Serializable
data class TxOutput(
    /** Output index within the transaction */
    val index: Int,
    /** Amount in atomic units (0 for RingCT outputs, real amount is encrypted) */
    val amount: Long,
    /** Output target containing the public key */
    val target: TxOutTarget,
    /** Global output index on the blockchain */
    val globalIndex: Long = -1
)

/**
 * Key image used to prevent double-spending
 */
@Serializable
@JvmInline
value class KeyImage(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "Key image must be 32 bytes" }
    }
    
    fun toHex(): String = bytes.joinToString("") { "%02x".format(it) }
    
    companion object {
        fun fromHex(hex: String): KeyImage {
            require(hex.length == 64) { "Key image hex must be 64 characters" }
            return KeyImage(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }
    }
}

/**
 * A transaction input (spending a previous output)
 */
@Serializable
data class TxInput(
    /** Amount being spent (0 for RingCT) */
    val amount: Long,
    /** Key offsets for ring members (relative offsets) */
    val keyOffsets: List<Long>,
    /** Key image to prevent double-spending */
    val keyImage: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TxInput
        return amount == other.amount && 
               keyOffsets == other.keyOffsets && 
               keyImage.contentEquals(other.keyImage)
    }

    override fun hashCode(): Int {
        var result = amount.hashCode()
        result = 31 * result + keyOffsets.hashCode()
        result = 31 * result + keyImage.contentHashCode()
        return result
    }
}

/**
 * RingCT signature types
 */
enum class RctType(val value: Int) {
    /** No RingCT (pre-RingCT transaction) */
    Null(0),
    /** Full RingCT (all amounts hidden) */
    Full(1),
    /** Simple RingCT */
    Simple(2),
    /** Bulletproofs */
    Bulletproof(3),
    /** Bulletproofs 2 */
    Bulletproof2(4),
    /** CLSAG */
    CLSAG(5),
    /** Bulletproofs+ */
    BulletproofPlus(6);
    
    companion object {
        fun fromValue(value: Int): RctType = 
            entries.find { it.value == value } ?: Null
    }
}

/**
 * ECDH info for encrypted amount recovery
 */
@Serializable
data class EcdhInfo(
    /** Encrypted mask */
    val mask: ByteArray,
    /** Encrypted amount */
    val amount: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as EcdhInfo
        return mask.contentEquals(other.mask) && amount.contentEquals(other.amount)
    }

    override fun hashCode(): Int {
        var result = mask.contentHashCode()
        result = 31 * result + amount.contentHashCode()
        return result
    }
}

/**
 * RingCT signature data
 */
@Serializable
data class RctSignature(
    /** RingCT type */
    val type: RctType,
    /** Transaction fee in atomic units */
    val txnFee: Long,
    /** ECDH info for each output (encrypted amounts) */
    val ecdhInfo: List<EcdhInfo>,
    /** Output commitments */
    val outPk: List<ByteArray>,
    /** Pseudo output commitments (for inputs) */
    val pseudoOuts: List<ByteArray> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as RctSignature
        if (type != other.type) return false
        if (txnFee != other.txnFee) return false
        if (ecdhInfo != other.ecdhInfo) return false
        if (outPk.size != other.outPk.size) return false
        for (i in outPk.indices) {
            if (!outPk[i].contentEquals(other.outPk[i])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + txnFee.hashCode()
        result = 31 * result + ecdhInfo.hashCode()
        result = 31 * result + outPk.fold(0) { acc, arr -> acc * 31 + arr.contentHashCode() }
        return result
    }
}

/**
 * Extra field parsed components
 */
@Serializable
data class TxExtra(
    /** Transaction public key */
    val txPubKey: ByteArray?,
    /** Additional public keys (for subaddresses) */
    val additionalPubKeys: List<ByteArray> = emptyList(),
    /** Payment ID (encrypted or plain) */
    val paymentId: ByteArray? = null,
    /** Nonce data */
    val nonce: ByteArray? = null,
    /** Raw extra bytes */
    val raw: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as TxExtra
        return txPubKey.contentEquals(other.txPubKey) && raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int {
        var result = txPubKey?.contentHashCode() ?: 0
        result = 31 * result + raw.contentHashCode()
        return result
    }
}

/**
 * Complete Monero transaction
 */
@Serializable
data class Transaction(
    /** Transaction hash */
    val hash: ByteArray,
    /** Transaction version */
    val version: Int,
    /** Unlock time (block height or timestamp) */
    val unlockTime: Long,
    /** Transaction inputs */
    val inputs: List<TxInput>,
    /** Transaction outputs */
    val outputs: List<TxOutput>,
    /** Extra field */
    val extra: TxExtra,
    /** RingCT signature (null for pre-RingCT) */
    val rctSignature: RctSignature?,
    /** Block height (-1 if in mempool) */
    val blockHeight: Long = -1,
    /** Block timestamp */
    val timestamp: Long = 0,
    /** Whether this is a coinbase transaction */
    val isCoinbase: Boolean = false
) {
    /** Transaction prefix hash (used for signing) */
    fun prefixHash(): ByteArray {
        // TODO: Implement proper prefix hash calculation
        return hash
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Transaction
        return hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int = hash.contentHashCode()
}

/**
 * A wallet-owned output (decrypted and verified)
 */
@Serializable
data class OwnedOutput(
    /** Transaction hash */
    val txHash: ByteArray,
    /** Output index within transaction */
    val outputIndex: Int,
    /** Global output index on blockchain */
    val globalIndex: Long,
    /** Decrypted amount */
    val amount: Long,
    /** Output public key */
    val publicKey: ByteArray,
    /** Key image (null if not yet computed) */
    val keyImage: ByteArray? = null,
    /** Block height */
    val blockHeight: Long,
    /** Block timestamp */
    val timestamp: Long,
    /** Whether output is spent */
    val isSpent: Boolean = false,
    /** Whether output is unlocked (spendable) */
    val isUnlocked: Boolean = false,
    /** Subaddress major index (account) */
    val subaddressMajor: Int = 0,
    /** Subaddress minor index */
    val subaddressMinor: Int = 0,
    /** Output commitment */
    val commitment: ByteArray? = null,
    /** Output mask (for RingCT) */
    val mask: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as OwnedOutput
        return txHash.contentEquals(other.txHash) && outputIndex == other.outputIndex
    }

    override fun hashCode(): Int {
        var result = txHash.contentHashCode()
        result = 31 * result + outputIndex
        return result
    }
    
    /** Unique identifier for this output */
    val outpoint: String
        get() = "${txHash.toHex()}:$outputIndex"
    
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/**
 * Transaction output with ownership info
 */
data class ScannedOutput(
    /** The transaction output */
    val output: TxOutput,
    /** Decrypted amount (if owned) */
    val amount: Long?,
    /** Whether this output belongs to the wallet */
    val isOwned: Boolean,
    /** Subaddress index if owned */
    val subaddressIndex: Pair<Int, Int>? = null,
    /** Output private key (for spending) */
    val outputPrivateKey: ByteArray? = null
)
