package io.monero.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Monero Wallet RPC client interface.
 *
 * This wraps `monero-wallet-rpc` JSON-RPC methods.
 */
interface WalletRpcClient {
    /** Get transaction secret key from transaction id. */
    suspend fun getTxKey(txid: String): String

    /** Check a transaction in the blockchain with its secret key. */
    suspend fun checkTxKey(txid: String, txKey: String, address: String): CheckTxKeyResult

    /** Get transaction signature to prove it. */
    suspend fun getTxProof(txid: String, address: String, message: String? = null): String

    /** Prove a transaction by checking its signature. */
    suspend fun checkTxProof(
        txid: String,
        address: String,
        signature: String,
        message: String? = null
    ): CheckTxProofResult

    /** Generate a signature to prove an available amount in a wallet. */
    suspend fun getReserveProof(
        all: Boolean = true,
        accountIndex: Int? = null,
        amount: Long? = null,
        message: String? = null
    ): String

    /** Proves a wallet has a disposable reserve using a signature. */
    suspend fun checkReserveProof(address: String, signature: String, message: String? = null): CheckReserveProofResult

    /** Sign an arbitrary string. */
    suspend fun sign(data: String): String

    /** Verify a signature on a string. */
    suspend fun verify(data: String, address: String, signature: String): Boolean

    /** Check if a wallet is a multisig one. */
    suspend fun isMultisig(): IsMultisigResult

    /** Prepare a wallet for multisig by generating a multisig string to share with peers. */
    suspend fun prepareMultisig(): PrepareMultisigResult

    /** Make a wallet multisig by importing peers multisig string. */
    suspend fun makeMultisig(multisigInfo: List<String>, threshold: Int, password: String? = null): MakeMultisigResult

    /** Performs extra multisig keys exchange rounds. */
    suspend fun exchangeMultisigKeys(
        password: String,
        multisigInfo: String,
        forceUpdateUseWithCaution: Boolean? = null
    ): ExchangeMultisigKeysResult

    /** Sign a transaction in multisig. */
    suspend fun signMultisig(txDataHex: String): SignMultisigResult

    /** Close connection */
    fun close()
}

/** Wallet RPC connection configuration */
data class WalletRpcConfig(
    val host: String = "localhost",
    val port: Int = 18082,
    val useSsl: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val timeoutMs: Long = 30_000
)

@Serializable
data class CheckTxKeyResult(
    val confirmations: Long,
    @SerialName("in_pool") val inPool: Boolean,
    val received: Long
)

@Serializable
data class CheckTxProofResult(
    val confirmations: Long,
    val good: Boolean,
    @SerialName("in_pool") val inPool: Boolean,
    val received: Long
)

@Serializable
data class CheckReserveProofResult(
    val good: Boolean,
    val spent: Long = 0,
    val total: Long = 0
)

@Serializable
data class IsMultisigResult(
    val multisig: Boolean,
    val ready: Boolean,
    val threshold: Int,
    val total: Int
)

@Serializable
data class PrepareMultisigResult(
    @SerialName("multisig_info") val multisigInfo: String
)

@Serializable
data class MakeMultisigResult(
    val address: String,
    @SerialName("multisig_info") val multisigInfo: String
)

@Serializable
data class ExchangeMultisigKeysResult(
    val address: String,
    @SerialName("multisig_info") val multisigInfo: String
)

@Serializable
data class SignMultisigResult(
    @SerialName("tx_data_hex") val txDataHex: String,
    @SerialName("tx_hash_list") val txHashList: List<String>
)
