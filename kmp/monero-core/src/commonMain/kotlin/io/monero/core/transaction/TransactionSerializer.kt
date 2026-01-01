package io.monero.core.transaction

import io.monero.crypto.Keccak

/**
 * Serializer for Monero transaction binary format.
 * 
 * Monero transactions use a custom binary format with:
 * - Varints for integers
 * - Fixed-size byte arrays for keys/hashes
 * - Specific type tags for inputs/outputs
 */
object TransactionSerializer {

    // Output type tags
    private const val TXOUT_TO_KEY: Byte = 0x02
    private const val TXOUT_TO_TAGGED_KEY: Byte = 0x03

    // Input type tags
    private const val TXIN_GEN: Byte = 0xFF.toByte()
    private const val TXIN_TO_KEY: Byte = 0x02

    // Extra field tags
    private const val TX_EXTRA_TAG_PUBKEY: Byte = 0x01
    private const val TX_EXTRA_TAG_NONCE: Byte = 0x02
    private const val TX_EXTRA_TAG_ADDITIONAL_PUBKEYS: Byte = 0x04
    private const val TX_EXTRA_NONCE_ENCRYPTED_PAYMENT_ID: Byte = 0x01

    /**
     * Serialize a transaction to binary format.
     */
    fun serialize(tx: Transaction): ByteArray {
        val writer = BinaryWriter()

        // Version
        writer.writeVarint(tx.version.toLong())

        // Unlock time
        writer.writeVarint(tx.unlockTime)

        // Inputs
        writer.writeVarint(tx.inputs.size.toLong())
        for (input in tx.inputs) {
            serializeInput(writer, input, tx.isCoinbase)
        }

        // Outputs
        writer.writeVarint(tx.outputs.size.toLong())
        for (output in tx.outputs) {
            serializeOutput(writer, output)
        }

        // Extra
        val extraBytes = serializeExtra(tx.extra)
        writer.writeVarint(extraBytes.size.toLong())
        writer.writeBytes(extraBytes)

        // RingCT signature (for version 2+)
        if (tx.version >= 2 && !tx.isCoinbase && tx.rctSignature != null) {
            serializeRctSignature(writer, tx.rctSignature, tx.outputs.size)
        }

        return writer.toByteArray()
    }

    /**
     * Serialize transaction prefix only (for signing).
     */
    fun serializePrefix(tx: Transaction): ByteArray {
        val writer = BinaryWriter()

        // Version
        writer.writeVarint(tx.version.toLong())

        // Unlock time
        writer.writeVarint(tx.unlockTime)

        // Inputs
        writer.writeVarint(tx.inputs.size.toLong())
        for (input in tx.inputs) {
            serializeInput(writer, input, tx.isCoinbase)
        }

        // Outputs
        writer.writeVarint(tx.outputs.size.toLong())
        for (output in tx.outputs) {
            serializeOutput(writer, output)
        }

        // Extra
        val extraBytes = serializeExtra(tx.extra)
        writer.writeVarint(extraBytes.size.toLong())
        writer.writeBytes(extraBytes)

        return writer.toByteArray()
    }

    /**
     * Calculate transaction prefix hash (for CLSAG signing).
     */
    fun prefixHash(tx: Transaction): ByteArray {
        val prefix = serializePrefix(tx)
        return Keccak.hash256(prefix)
    }

    /**
     * Serialize to hex string.
     */
    fun serializeToHex(tx: Transaction): String {
        return serialize(tx).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun serializeInput(writer: BinaryWriter, input: TxInput, isCoinbase: Boolean) {
        if (isCoinbase) {
            writer.writeByte(TXIN_GEN)
            // For coinbase, write height (but we don't have it in TxInput model)
            // This is a simplified version
            writer.writeVarint(0)
        } else {
            writer.writeByte(TXIN_TO_KEY)
            writer.writeVarint(input.amount)
            writer.writeVarint(input.keyOffsets.size.toLong())
            for (offset in input.keyOffsets) {
                writer.writeVarint(offset)
            }
            writer.writeBytes(input.keyImage)
        }
    }

    private fun serializeOutput(writer: BinaryWriter, output: TxOutput) {
        writer.writeVarint(output.amount)

        if (output.target.viewTag != null) {
            writer.writeByte(TXOUT_TO_TAGGED_KEY)
            writer.writeBytes(output.target.key)
            writer.writeByte(output.target.viewTag)
        } else {
            writer.writeByte(TXOUT_TO_KEY)
            writer.writeBytes(output.target.key)
        }
    }

    private fun serializeExtra(extra: TxExtra): ByteArray {
        val writer = BinaryWriter()

        // Transaction public key
        if (extra.txPubKey != null) {
            writer.writeByte(TX_EXTRA_TAG_PUBKEY)
            writer.writeBytes(extra.txPubKey)
        }

        // Additional public keys (for subaddresses)
        if (extra.additionalPubKeys.isNotEmpty()) {
            writer.writeByte(TX_EXTRA_TAG_ADDITIONAL_PUBKEYS)
            writer.writeVarint(extra.additionalPubKeys.size.toLong())
            for (pk in extra.additionalPubKeys) {
                writer.writeBytes(pk)
            }
        }

        // Nonce (may contain payment ID)
        if (extra.nonce != null) {
            writer.writeByte(TX_EXTRA_TAG_NONCE)
            writer.writeVarint(extra.nonce.size.toLong())
            writer.writeBytes(extra.nonce)
        } else if (extra.paymentId != null) {
            // Encrypted payment ID (8 bytes)
            if (extra.paymentId.size == 8) {
                writer.writeByte(TX_EXTRA_TAG_NONCE)
                writer.writeVarint(9) // 1 byte tag + 8 bytes payment ID
                writer.writeByte(TX_EXTRA_NONCE_ENCRYPTED_PAYMENT_ID)
                writer.writeBytes(extra.paymentId)
            }
        }

        return writer.toByteArray()
    }

    private fun serializeRctSignature(
        writer: BinaryWriter,
        rct: RctSignature,
        outputCount: Int
    ) {
        // RCT type
        writer.writeByte(rct.type.value.toByte())

        if (rct.type == RctType.Null) {
            return
        }

        // Fee
        writer.writeVarint(rct.txnFee)

        // ECDH info
        for (i in 0 until outputCount) {
            val ecdh = rct.ecdhInfo.getOrNull(i)
            when (rct.type) {
                RctType.Bulletproof2, RctType.CLSAG, RctType.BulletproofPlus -> {
                    // Compact format: 8-byte encrypted amount only
                    val amount = ecdh?.amount ?: ByteArray(8)
                    writer.writeBytes(amount.sliceArray(0 until minOf(8, amount.size)))
                }
                else -> {
                    // Full format: 32-byte mask + 32-byte amount
                    val mask = ecdh?.mask ?: ByteArray(32)
                    val amount = ecdh?.amount ?: ByteArray(32)
                    writer.writeBytes(mask)
                    writer.writeBytes(amount)
                }
            }
        }

        // Output commitments
        for (i in 0 until outputCount) {
            val commitment = rct.outPk.getOrNull(i) ?: ByteArray(32)
            writer.writeBytes(commitment)
        }
    }

    /**
     * Build TxExtra from components.
     */
    fun buildExtra(
        txPubKey: ByteArray,
        additionalPubKeys: List<ByteArray> = emptyList(),
        encryptedPaymentId: ByteArray? = null
    ): TxExtra {
        val nonce = if (encryptedPaymentId != null && encryptedPaymentId.size == 8) {
            byteArrayOf(TX_EXTRA_NONCE_ENCRYPTED_PAYMENT_ID) + encryptedPaymentId
        } else {
            null
        }

        return TxExtra(
            txPubKey = txPubKey,
            additionalPubKeys = additionalPubKeys,
            paymentId = encryptedPaymentId,
            nonce = nonce,
            raw = ByteArray(0) // Will be computed during serialization
        )
    }
}

/**
 * Binary writer for transaction serialization.
 */
internal class BinaryWriter {
    private val buffer = mutableListOf<Byte>()

    fun writeByte(b: Byte) {
        buffer.add(b)
    }

    fun writeBytes(bytes: ByteArray) {
        bytes.forEach { buffer.add(it) }
    }

    /**
     * Write variable-length integer (varint).
     * Uses 7 bits per byte, with MSB as continuation flag.
     */
    fun writeVarint(value: Long) {
        var v = value
        while (v >= 0x80) {
            buffer.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buffer.add((v and 0x7F).toByte())
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(buffer.size)
        for (i in buffer.indices) {
            result[i] = buffer[i]
        }
        return result
    }

    fun size(): Int = buffer.size
}
