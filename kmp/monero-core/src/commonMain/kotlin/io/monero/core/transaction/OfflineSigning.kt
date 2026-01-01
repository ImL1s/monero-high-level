package io.monero.core.transaction

import io.monero.crypto.Keccak
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline transaction signing workflow.
 *
 * Monero supports cold-signing where:
 * 1. **Watch-only wallet** exports an unsigned transaction (contains rings, destinations, etc.)
 * 2. **Offline wallet** (with spend key) signs and exports a signed transaction
 * 3. **Watch-only wallet** relays the signed transaction to the network
 *
 * This file provides serialization/deserialization helpers for these formats.
 */
object OfflineSigning {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unsigned Transaction Export
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exportable unsigned transaction data.
     *
     * Contains everything the offline wallet needs to produce a signed tx:
     * - Transaction prefix (inputs, outputs, extra)
     * - Per-input ring member data (for CLSAG signing)
     * - Per-output amount and mask (for commitment verification)
     */
    @Serializable
    data class UnsignedTxExport(
        /** Version of this export format. */
        val version: Int = 1,
        /** Serialized transaction prefix (hex). */
        val txPrefixHex: String,
        /** Prefix hash used for signing (hex, 32 bytes). */
        val prefixHashHex: String,
        /** Per-input ring data. */
        val inputs: List<UnsignedInputData>,
        /** Per-output data (amounts, masks). */
        val outputs: List<UnsignedOutputData>,
        /** RCT type value. */
        val rctType: Int,
        /** Fee in atomic units. */
        val fee: Long,
        /** Change amount (informational). */
        val change: Long
    )

    @Serializable
    data class UnsignedInputData(
        /** Global index of the real output being spent. */
        val realGlobalIndex: Long,
        /** Index of real output within the ring (0-based). */
        val realIndexInRing: Int,
        /** Ring member public keys (hex, 32 bytes each). */
        val ringPubKeysHex: List<String>,
        /** Ring member commitments (hex, 32 bytes each). */
        val ringCommitmentsHex: List<String>,
        /** Key image for this input (hex, 32 bytes). */
        val keyImageHex: String
    )

    @Serializable
    data class UnsignedOutputData(
        /** Output index. */
        val index: Int,
        /** Amount in atomic units. */
        val amount: Long,
        /** Blinding mask (hex, 32 bytes). */
        val maskHex: String,
        /** Commitment (hex, 32 bytes). */
        val commitmentHex: String
    )

    /**
     * Export an unsigned transaction for offline signing.
     */
    fun exportUnsigned(
        builtTx: TxBuilder.BuiltTx,
        outputMasks: List<ByteArray>,
        outputAmounts: List<Long>
    ): String {
        require(builtTx.rings.size == builtTx.tx.inputs.size) { "Ring count must match input count" }
        require(outputMasks.size == builtTx.tx.outputs.size) { "Mask count must match output count" }
        require(outputAmounts.size == builtTx.tx.outputs.size) { "Amount count must match output count" }

        val prefixBytes = TransactionSerializer.serializePrefix(builtTx.tx)
        val prefixHash = Keccak.hash256(prefixBytes)

        val inputs = builtTx.rings.mapIndexed { idx, ring ->
            UnsignedInputData(
                realGlobalIndex = ring.realGlobalIndex,
                realIndexInRing = ring.realIndex,
                ringPubKeysHex = ring.ringMembers.map { it.publicKey.toHex() },
                ringCommitmentsHex = ring.ringMembers.map { (it.commitment ?: ByteArray(32)).toHex() },
                keyImageHex = builtTx.tx.inputs[idx].keyImage.toHex()
            )
        }

        val rctSig = builtTx.tx.rctSignature
            ?: throw IllegalStateException("RctSignature required for unsigned export")

        val outputs = builtTx.tx.outputs.mapIndexed { idx, _ ->
            UnsignedOutputData(
                index = idx,
                amount = outputAmounts[idx],
                maskHex = outputMasks[idx].toHex(),
                commitmentHex = rctSig.outPk.getOrNull(idx)?.toHex() ?: ""
            )
        }

        val export = UnsignedTxExport(
            version = 1,
            txPrefixHex = prefixBytes.toHex(),
            prefixHashHex = prefixHash.toHex(),
            inputs = inputs,
            outputs = outputs,
            rctType = rctSig.type.value,
            fee = rctSig.txnFee,
            change = builtTx.change
        )

        return json.encodeToString(export)
    }

    /**
     * Parse an unsigned transaction export.
     */
    fun parseUnsigned(jsonStr: String): UnsignedTxExport {
        return json.decodeFromString(jsonStr)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Signed Transaction Export
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exportable signed transaction ready for relay.
     */
    @Serializable
    data class SignedTxExport(
        /** Version of this export format. */
        val version: Int = 1,
        /** Full signed transaction blob (hex). */
        val txBlobHex: String,
        /** Transaction hash (hex, 32 bytes). */
        val txHashHex: String,
        /** Fee in atomic units. */
        val fee: Long
    )

    /**
     * Export a signed transaction for relay.
     */
    fun exportSigned(
        txBlob: ByteArray,
        txHash: ByteArray,
        fee: Long
    ): String {
        val export = SignedTxExport(
            version = 1,
            txBlobHex = txBlob.toHex(),
            txHashHex = txHash.toHex(),
            fee = fee
        )
        return json.encodeToString(export)
    }

    /**
     * Parse a signed transaction export.
     */
    fun parseSigned(jsonStr: String): SignedTxExport {
        return json.decodeFromString(jsonStr)
    }

    /**
     * Convert signed export back to raw bytes for relay.
     */
    fun signedExportToBlob(export: SignedTxExport): ByteArray {
        return export.txBlobHex.hexToBytes()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Relay helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Data class for relay result.
     */
    data class RelayResult(
        val txHash: String,
        val success: Boolean,
        val errorMessage: String? = null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Hex utilities
    // ─────────────────────────────────────────────────────────────────────────

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
