package io.monero.core.transaction

import io.monero.crypto.Keccak

/**
 * Parser for Monero transaction binary format
 */
object TransactionParser {
    
    // Extra field tags
    private const val TX_EXTRA_TAG_PUBKEY: Byte = 0x01
    private const val TX_EXTRA_TAG_NONCE: Byte = 0x02
    private const val TX_EXTRA_TAG_ADDITIONAL_PUBKEYS: Byte = 0x04
    private const val TX_EXTRA_NONCE_PAYMENT_ID: Byte = 0x00
    private const val TX_EXTRA_NONCE_ENCRYPTED_PAYMENT_ID: Byte = 0x01
    
    /**
     * Parse transaction from hex string
     */
    fun fromHex(hex: String): Transaction {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return parse(bytes)
    }
    
    /**
     * Parse transaction from binary blob
     */
    fun parse(data: ByteArray): Transaction {
        val reader = BinaryReader(data)
        
        // Version
        val version = reader.readVarint().toInt()
        
        // Unlock time
        val unlockTime = reader.readVarint()
        
        // Inputs
        val inputCount = reader.readVarint().toInt()
        val inputs = mutableListOf<TxInput>()
        var isCoinbase = false
        
        for (i in 0 until inputCount) {
            val inputType = reader.readByte()
            when (inputType.toInt()) {
                0xff -> {
                    // Coinbase input (gen)
                    isCoinbase = true
                    reader.readVarint() // height
                }
                0x02 -> {
                    // Key input (txin_to_key)
                    val amount = reader.readVarint()
                    val keyOffsetCount = reader.readVarint().toInt()
                    val keyOffsets = (0 until keyOffsetCount).map { reader.readVarint() }
                    val keyImage = reader.readBytes(32)
                    inputs.add(TxInput(amount, keyOffsets, keyImage))
                }
                else -> throw IllegalArgumentException("Unknown input type: $inputType")
            }
        }
        
        // Outputs
        val outputCount = reader.readVarint().toInt()
        val outputs = mutableListOf<TxOutput>()
        
        for (i in 0 until outputCount) {
            val amount = reader.readVarint()
            val outputType = reader.readByte()
            
            val target = when (outputType.toInt()) {
                0x02 -> {
                    // txout_to_key
                    val key = reader.readBytes(32)
                    TxOutTarget(key)
                }
                0x03 -> {
                    // txout_to_tagged_key (with view tag)
                    val key = reader.readBytes(32)
                    val viewTag = reader.readByte()
                    TxOutTarget(key, viewTag)
                }
                else -> throw IllegalArgumentException("Unknown output type: $outputType")
            }
            
            outputs.add(TxOutput(i, amount, target))
        }
        
        // Extra
        val extraSize = reader.readVarint().toInt()
        val extraBytes = reader.readBytes(extraSize)
        val extra = parseExtra(extraBytes)
        
        // RingCT (for version 2+)
        val rctSignature = if (version >= 2 && !isCoinbase && inputs.isNotEmpty()) {
            parseRctSignature(reader, outputs.size, inputs.size)
        } else {
            null
        }
        
        // Calculate transaction hash
        val hash = Keccak.hash256(data)
        
        return Transaction(
            hash = hash,
            version = version,
            unlockTime = unlockTime,
            inputs = inputs,
            outputs = outputs,
            extra = extra,
            rctSignature = rctSignature,
            isCoinbase = isCoinbase
        )
    }
    
    /**
     * Parse extra field
     */
    private fun parseExtra(data: ByteArray): TxExtra {
        var txPubKey: ByteArray? = null
        val additionalPubKeys = mutableListOf<ByteArray>()
        var paymentId: ByteArray? = null
        var nonce: ByteArray? = null
        
        val reader = BinaryReader(data)
        
        while (reader.hasMore()) {
            when (reader.readByte()) {
                TX_EXTRA_TAG_PUBKEY -> {
                    if (reader.remaining() >= 32) {
                        txPubKey = reader.readBytes(32)
                    }
                }
                TX_EXTRA_TAG_NONCE -> {
                    val nonceLen = reader.readVarint().toInt()
                    if (reader.remaining() >= nonceLen && nonceLen > 0) {
                        val nonceData = reader.readBytes(nonceLen)
                        nonce = nonceData
                        
                        // Check for payment ID within nonce
                        if (nonceLen >= 1) {
                            when (nonceData[0]) {
                                TX_EXTRA_NONCE_PAYMENT_ID -> {
                                    if (nonceLen >= 33) {
                                        paymentId = nonceData.sliceArray(1..32)
                                    }
                                }
                                TX_EXTRA_NONCE_ENCRYPTED_PAYMENT_ID -> {
                                    if (nonceLen >= 9) {
                                        paymentId = nonceData.sliceArray(1..8)
                                    }
                                }
                            }
                        }
                    }
                }
                TX_EXTRA_TAG_ADDITIONAL_PUBKEYS -> {
                    val count = reader.readVarint().toInt()
                    for (j in 0 until count) {
                        if (reader.remaining() >= 32) {
                            additionalPubKeys.add(reader.readBytes(32))
                        }
                    }
                }
                else -> {
                    // Unknown tag, try to skip
                    // This is a simplified approach; real implementation needs more care
                    break
                }
            }
        }
        
        return TxExtra(
            txPubKey = txPubKey,
            additionalPubKeys = additionalPubKeys,
            paymentId = paymentId,
            nonce = nonce,
            raw = data
        )
    }
    
    /**
     * Parse RingCT signature (base only, not full proof data)
     */
    private fun parseRctSignature(
        reader: BinaryReader,
        outputCount: Int,
        inputCount: Int
    ): RctSignature {
        val typeValue = reader.readByte().toInt() and 0xFF
        val type = RctType.fromValue(typeValue)
        
        if (type == RctType.Null) {
            return RctSignature(type, 0, emptyList(), emptyList())
        }
        
        val txnFee = reader.readVarint()
        
        // ECDH info
        val ecdhInfo = mutableListOf<EcdhInfo>()
        for (i in 0 until outputCount) {
            when (type) {
                RctType.Bulletproof2, RctType.CLSAG, RctType.BulletproofPlus -> {
                    // Compact format: 8-byte encrypted amount only
                    val amount = reader.readBytes(8)
                    ecdhInfo.add(EcdhInfo(ByteArray(32), amount))
                }
                else -> {
                    // Full format: 32-byte mask + 32-byte amount
                    val mask = reader.readBytes(32)
                    val amount = reader.readBytes(32)
                    ecdhInfo.add(EcdhInfo(mask, amount))
                }
            }
        }
        
        // Output public keys (commitments)
        val outPk = mutableListOf<ByteArray>()
        for (i in 0 until outputCount) {
            outPk.add(reader.readBytes(32))
        }
        
        return RctSignature(
            type = type,
            txnFee = txnFee,
            ecdhInfo = ecdhInfo,
            outPk = outPk
        )
    }
}

/**
 * Simple binary reader for parsing transaction data
 */
internal class BinaryReader(private val data: ByteArray) {
    private var position = 0
    
    fun hasMore(): Boolean = position < data.size
    
    fun remaining(): Int = data.size - position
    
    fun readByte(): Byte {
        check(position < data.size) { "End of data reached" }
        return data[position++]
    }
    
    fun readBytes(count: Int): ByteArray {
        check(position + count <= data.size) { "Not enough data: need $count, have ${remaining()}" }
        val result = data.sliceArray(position until position + count)
        position += count
        return result
    }
    
    /**
     * Read variable-length integer (varint)
     */
    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        
        while (true) {
            val byte = readByte().toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        
        return result
    }
}
