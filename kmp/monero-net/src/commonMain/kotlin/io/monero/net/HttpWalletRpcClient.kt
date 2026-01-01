package io.monero.net

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * HTTP implementation of WalletRpcClient using Ktor.
 */
class HttpWalletRpcClient(
    private val config: WalletRpcConfig,
    httpClient: HttpClient? = null
) : WalletRpcClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: HttpClient = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeoutMs
            connectTimeoutMillis = config.timeoutMs
        }
    }

    private val baseUrl: String
        get() {
            val scheme = if (config.useSsl) "https" else "http"
            return "$scheme://${config.host}:${config.port}"
        }

    override suspend fun getTxKey(txid: String): String {
        val params = buildJsonObject { put("txid", txid) }
        val response = rpcCallWithParams<GetTxKeyResponse>("get_tx_key", params)
        return response.tx_key
    }

    override suspend fun checkTxKey(txid: String, txKey: String, address: String): CheckTxKeyResult {
        val params = buildJsonObject {
            put("txid", txid)
            put("tx_key", txKey)
            put("address", address)
        }
        return rpcCallWithParams("check_tx_key", params)
    }

    override suspend fun getTxProof(txid: String, address: String, message: String?): String {
        val params = buildJsonObject {
            put("txid", txid)
            put("address", address)
            if (message != null) put("message", message)
        }
        val response = rpcCallWithParams<GetTxProofResponse>("get_tx_proof", params)
        return response.signature
    }

    override suspend fun checkTxProof(
        txid: String,
        address: String,
        signature: String,
        message: String?
    ): CheckTxProofResult {
        val params = buildJsonObject {
            put("txid", txid)
            put("address", address)
            put("signature", signature)
            if (message != null) put("message", message)
        }
        return rpcCallWithParams("check_tx_proof", params)
    }

    override suspend fun getReserveProof(
        all: Boolean,
        accountIndex: Int?,
        amount: Long?,
        message: String?
    ): String {
        val params = buildJsonObject {
            put("all", all)
            if (!all) {
                if (accountIndex != null) put("account_index", accountIndex)
                if (amount != null) put("amount", amount)
            }
            if (message != null) put("message", message)
        }
        val response = rpcCallWithParams<GetReserveProofResponse>("get_reserve_proof", params)
        return response.signature
    }

    override suspend fun checkReserveProof(address: String, signature: String, message: String?): CheckReserveProofResult {
        val params = buildJsonObject {
            put("address", address)
            put("signature", signature)
            if (message != null) put("message", message)
        }
        return rpcCallWithParams("check_reserve_proof", params)
    }

    override suspend fun sign(data: String): String {
        val params = buildJsonObject { put("data", data) }
        val response = rpcCallWithParams<SignResponse>("sign", params)
        return response.signature
    }

    override suspend fun verify(data: String, address: String, signature: String): Boolean {
        val params = buildJsonObject {
            put("data", data)
            put("address", address)
            put("signature", signature)
        }
        val response = rpcCallWithParams<VerifyResponse>("verify", params)
        return response.good
    }

    override suspend fun isMultisig(): IsMultisigResult = rpcCall("is_multisig")

    override suspend fun prepareMultisig(): PrepareMultisigResult = rpcCall("prepare_multisig")

    override suspend fun makeMultisig(multisigInfo: List<String>, threshold: Int, password: String?): MakeMultisigResult {
        val params = buildJsonObject {
            putJsonArray("multisig_info") { multisigInfo.forEach { add(it) } }
            put("threshold", threshold)
            if (password != null) put("password", password)
        }
        return rpcCallWithParams("make_multisig", params)
    }

    override suspend fun exchangeMultisigKeys(
        password: String,
        multisigInfo: String,
        forceUpdateUseWithCaution: Boolean?
    ): ExchangeMultisigKeysResult {
        val params = buildJsonObject {
            put("password", password)
            put("multisig_info", multisigInfo)
            if (forceUpdateUseWithCaution != null) put("force_update_use_with_caution", forceUpdateUseWithCaution)
        }
        return rpcCallWithParams("exchange_multisig_keys", params)
    }

    override suspend fun signMultisig(txDataHex: String): SignMultisigResult {
        val params = buildJsonObject { put("tx_data_hex", txDataHex) }
        return rpcCallWithParams("sign_multisig", params)
    }

    override fun close() {
        client.close()
    }

    private suspend inline fun <reified T> rpcCall(method: String): T {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "0")
            put("method", method)
        }

        val response = client.post("$baseUrl/json_rpc") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val jsonResponse = response.body<JsonObject>()
        val result = jsonResponse["result"] ?: throw WalletRpcException("No result in response")
        return json.decodeFromJsonElement(result)
    }

    private suspend inline fun <reified T> rpcCallWithParams(method: String, params: JsonElement): T {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "0")
            put("method", method)
            put("params", params)
        }

        val response = client.post("$baseUrl/json_rpc") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val jsonResponse = response.body<JsonObject>()
        val result = jsonResponse["result"] ?: throw WalletRpcException("No result in response")
        return json.decodeFromJsonElement(result)
    }
}

class WalletRpcException(message: String) : Exception(message)

// --- Internal RPC DTOs ---

@Serializable
private data class GetTxKeyResponse(
    val tx_key: String
)

@Serializable
private data class GetTxProofResponse(
    val signature: String
)

@Serializable
private data class GetReserveProofResponse(
    val signature: String
)

@Serializable
private data class SignResponse(
    val signature: String
)

@Serializable
private data class VerifyResponse(
    val good: Boolean
)
