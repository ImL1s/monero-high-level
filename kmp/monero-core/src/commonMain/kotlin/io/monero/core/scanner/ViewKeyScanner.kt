package io.monero.core.scanner

import io.monero.core.address.SubaddressIndex
import io.monero.core.transaction.OwnedOutput
import io.monero.core.transaction.ScannedOutput
import io.monero.core.transaction.Transaction
import io.monero.core.transaction.TxOutput
import io.monero.crypto.Ed25519
import io.monero.crypto.Keccak

/**
 * Scans transactions for outputs belonging to a wallet.
 * 
 * Uses the view key to derive output public keys and compare against
 * transaction outputs to determine ownership.
 */
class ViewKeyScanner(
    /** Wallet's public view key */
    private val viewPublicKey: ByteArray,
    /** Wallet's private view key */
    private val viewSecretKey: ByteArray,
    /** Wallet's public spend key */
    private val spendPublicKey: ByteArray,
    /** Precomputed subaddress lookup table */
    private val subaddressTable: SubaddressTable = SubaddressTable()
) {
    init {
        require(viewPublicKey.size == 32) { "View public key must be 32 bytes" }
        require(viewSecretKey.size == 32) { "View secret key must be 32 bytes" }
        require(spendPublicKey.size == 32) { "Spend public key must be 32 bytes" }
    }
    
    /**
     * Precompute subaddress public keys for fast lookup
     */
    fun precomputeSubaddresses(majorMax: Int, minorMax: Int) {
        for (major in 0..majorMax) {
            for (minor in 0..minorMax) {
                val subaddressPubKey = deriveSubaddressPublicKey(major, minor)
                subaddressTable.add(subaddressPubKey, SubaddressIndex(major, minor))
            }
        }
    }
    
    /**
     * Scan a transaction for owned outputs
     */
    fun scanTransaction(tx: Transaction): List<ScannedOutput> {
        val txPubKey = tx.extra.txPubKey ?: return emptyList()
        val additionalPubKeys = tx.extra.additionalPubKeys
        
        return tx.outputs.mapIndexedNotNull { index, output ->
            scanOutput(
                output = output,
                outputIndex = index,
                txPubKey = txPubKey,
                additionalTxPubKey = additionalPubKeys.getOrNull(index)
            )
        }
    }
    
    /**
     * Scan a single output for ownership
     */
    fun scanOutput(
        output: TxOutput,
        outputIndex: Int,
        txPubKey: ByteArray,
        additionalTxPubKey: ByteArray? = null
    ): ScannedOutput? {
        // Try with main tx public key first
        val derivedPubKey = deriveOutputPublicKey(txPubKey, outputIndex)
        
        // Check view tag first if available (fast rejection)
        if (output.target.viewTag != null) {
            val expectedViewTag = computeViewTag(txPubKey, outputIndex)
            if (output.target.viewTag != expectedViewTag) {
                // Try additional public key if view tag doesn't match
                if (additionalTxPubKey != null) {
                    return tryAdditionalKey(output, outputIndex, additionalTxPubKey)
                }
                return null
            }
        }
        
        // Check if output belongs to main address
        if (derivedPubKey.contentEquals(output.target.key)) {
            return ScannedOutput(
                output = output,
                amount = null, // Will be decrypted separately
                isOwned = true,
                subaddressIndex = 0 to 0
            )
        }
        
        // Check subaddresses
        val subaddressMatch = checkSubaddress(derivedPubKey, output.target.key)
        if (subaddressMatch != null) {
            return ScannedOutput(
                output = output,
                amount = null,
                isOwned = true,
                subaddressIndex = subaddressMatch.major to subaddressMatch.minor
            )
        }
        
        // Try with additional public key (for subaddress outputs)
        if (additionalTxPubKey != null) {
            return tryAdditionalKey(output, outputIndex, additionalTxPubKey)
        }
        
        return null
    }
    
    private fun tryAdditionalKey(
        output: TxOutput,
        outputIndex: Int,
        additionalTxPubKey: ByteArray
    ): ScannedOutput? {
        val derivedPubKey = deriveOutputPublicKey(additionalTxPubKey, outputIndex)
        
        // Check view tag
        if (output.target.viewTag != null) {
            val expectedViewTag = computeViewTag(additionalTxPubKey, outputIndex)
            if (output.target.viewTag != expectedViewTag) {
                return null
            }
        }
        
        // Check subaddresses
        val subaddressMatch = checkSubaddress(derivedPubKey, output.target.key)
        if (subaddressMatch != null) {
            return ScannedOutput(
                output = output,
                amount = null,
                isOwned = true,
                subaddressIndex = subaddressMatch.major to subaddressMatch.minor
            )
        }
        
        return null
    }
    
    /**
     * Derive output public key: P = Hs(a*R || i)*G + B
     * Where:
     * - a = view secret key
     * - R = tx public key
     * - i = output index
     * - B = spend public key
     */
    private fun deriveOutputPublicKey(txPubKey: ByteArray, outputIndex: Int): ByteArray {
        // Compute shared secret: a * R
        val sharedSecret = Ed25519.scalarMult(viewSecretKey, txPubKey)
        
        // Compute derivation: Hs(a*R || i)
        val derivationData = sharedSecret + outputIndex.toVarint()
        val hash = Keccak.hash256(derivationData)
        val scalar = Ed25519.scalarReduce64(hash + ByteArray(32)) // Pad to 64 bytes
        
        // Compute P = Hs(...)*G + B
        val scalarTimesG = Ed25519.scalarMultBase(scalar)
        return Ed25519.pointAdd(scalarTimesG, spendPublicKey)
    }
    
    /**
     * Compute view tag for fast rejection
     */
    private fun computeViewTag(txPubKey: ByteArray, outputIndex: Int): Byte {
        val sharedSecret = Ed25519.scalarMult(viewSecretKey, txPubKey)
        val data = byteArrayOf(0x76, 0x69, 0x65, 0x77, 0x5f, 0x74, 0x61, 0x67) + // "view_tag"
                   sharedSecret + 
                   outputIndex.toVarint()
        val hash = Keccak.hash256(data)
        return hash[0]
    }
    
    /**
     * Check if the derived key matches any known subaddress
     */
    private fun checkSubaddress(derivedPubKey: ByteArray, outputPubKey: ByteArray): SubaddressIndex? {
        // For main address (0,0), the derived key equals the output key directly
        // For subaddresses, we need to reverse the derivation
        
        // Compute: D = P_out - derived = subaddress offset
        val diff = Ed25519.pointSub(outputPubKey, derivedPubKey)
        
        // Look up in subaddress table
        return subaddressTable.lookup(diff)
    }
    
    /**
     * Derive subaddress public key
     * D = B + Hs("SubAddr" || a || major || minor) * G
     */
    private fun deriveSubaddressPublicKey(major: Int, minor: Int): ByteArray {
        if (major == 0 && minor == 0) {
            return spendPublicKey
        }
        
        val prefix = "SubAddr".encodeToByteArray()
        val data = prefix + viewSecretKey + major.toLEBytes() + minor.toLEBytes()
        val hash = Keccak.hash256(data)
        val scalar = Ed25519.scalarReduce64(hash + ByteArray(32))
        val scalarG = Ed25519.scalarMultBase(scalar)
        return Ed25519.pointAdd(spendPublicKey, scalarG)
    }
    
    private fun Int.toVarint(): ByteArray {
        val result = mutableListOf<Byte>()
        var value = this
        while (value >= 0x80) {
            result.add(((value and 0x7F) or 0x80).toByte())
            value = value shr 7
        }
        result.add(value.toByte())
        return result.toByteArray()
    }
    
    private fun Int.toLEBytes(): ByteArray {
        return byteArrayOf(
            (this and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 24) and 0xFF).toByte()
        )
    }
}

/**
 * Lookup table for subaddress public keys
 */
class SubaddressTable {
    private val table = mutableMapOf<String, SubaddressIndex>()
    
    fun add(publicKey: ByteArray, index: SubaddressIndex) {
        table[publicKey.toHex()] = index
    }
    
    fun lookup(publicKey: ByteArray): SubaddressIndex? {
        return table[publicKey.toHex()]
    }
    
    fun clear() {
        table.clear()
    }
    
    val size: Int get() = table.size
    
    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
