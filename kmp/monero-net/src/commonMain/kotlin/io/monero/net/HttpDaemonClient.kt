package io.monero.net

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * HTTP implementation of DaemonClient using Ktor.
 */
class HttpDaemonClient(
    private val config: DaemonConfig,
    httpClient: HttpClient? = null
) : DaemonClient {
    
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

    override suspend fun getInfo(): DaemonInfo {
        val response = rpcCall<GetInfoResponse>("get_info")
        return DaemonInfo(
            height = response.height,
            targetHeight = response.target_height,
            difficulty = response.difficulty,
            txCount = response.tx_count,
            txPoolSize = response.tx_pool_size,
            altBlocksCount = response.alt_blocks_count,
            outgoingConnectionsCount = response.outgoing_connections_count,
            incomingConnectionsCount = response.incoming_connections_count,
            whitePoolSize = response.white_peerlist_size,
            greyPoolSize = response.grey_peerlist_size,
            mainnet = response.mainnet,
            testnet = response.testnet,
            stagenet = response.stagenet,
            topBlockHash = response.top_block_hash,
            synchronized = response.synchronized,
            version = response.version
        )
    }

    override suspend fun getHeight(): Long {
        val response = rpcCall<GetHeightResponse>("get_height")
        return response.height
    }

    override suspend fun getBlockByHeight(height: Long): BlockInfo {
        val params = buildJsonObject { put("height", height) }
        val response = rpcCallWithParams<GetBlockResponse>("get_block", params)
        return BlockInfo(
            height = response.block_header.height,
            hash = response.block_header.hash,
            timestamp = response.block_header.timestamp,
            prevHash = response.block_header.prev_hash,
            nonce = response.block_header.nonce,
            txHashes = response.tx_hashes ?: emptyList(),
            minerTx = response.miner_tx_hash
        )
    }

    override suspend fun getBlocks(startHeight: Long, endHeight: Long): List<BlockInfo> {
        val blocks = mutableListOf<BlockInfo>()
        for (h in startHeight..endHeight) {
            blocks.add(getBlockByHeight(h))
        }
        return blocks
    }

    override suspend fun getOutputs(offsets: List<Long>): List<OutputInfo> {
        val params = buildJsonObject {
            putJsonArray("outputs") {
                for (offset in offsets) {
                    addJsonObject {
                        put("amount", 0L)
                        put("index", offset)
                    }
                }
            }
            put("get_txid", true)
        }
        val response = rpcCallWithParams<GetOutsResponse>("get_outs", params)
        return response.outs.map { out ->
            OutputInfo(
                height = out.height,
                key = out.key,
                mask = out.mask,
                txid = out.txid,
                unlocked = out.unlocked
            )
        }
    }

    override suspend fun sendRawTransaction(txBlob: ByteArray): TxSubmitResult {
        val hexBlob = txBlob.joinToString("") { "%02x".format(it) }
        val body = buildJsonObject {
            put("tx_as_hex", hexBlob)
            put("do_not_relay", false)
        }
        val response = client.post("$baseUrl/sendrawtransaction") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<SendRawTxResponse>()
        
        return TxSubmitResult(
            success = response.status == "OK",
            reason = response.reason,
            doubleSpend = response.double_spend,
            feeTooLow = response.fee_too_low,
            invalidInput = response.invalid_input,
            invalidOutput = response.invalid_output,
            tooBig = response.too_big,
            overspend = response.overspend,
            txExtraTooBig = response.tx_extra_too_big
        )
    }

    override suspend fun getFeeEstimate(): FeeEstimate {
        val response = rpcCall<GetFeeEstimateResponse>("get_fee_estimate")
        return FeeEstimate(
            feePerByte = response.fee,
            quantizationMask = response.quantization_mask,
            fees = response.fees ?: listOf(response.fee)
        )
    }

    override suspend fun getTransactionPool(): List<PoolTransaction> {
        val response = client.post("$baseUrl/get_transaction_pool") {
            contentType(ContentType.Application.Json)
            setBody(JsonObject(emptyMap()))
        }.body<GetPoolResponse>()
        
        return response.transactions?.map { tx ->
            PoolTransaction(
                txHash = tx.id_hash,
                blobSize = tx.blob_size,
                fee = tx.fee,
                receivedTime = tx.receive_time,
                keptByBlock = tx.kept_by_block
            )
        } ?: emptyList()
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
        val result = jsonResponse["result"] ?: throw DaemonException("No result in response")
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
        val result = jsonResponse["result"] ?: throw DaemonException("No result in response")
        return json.decodeFromJsonElement(result)
    }
}

class DaemonException(message: String) : Exception(message)

// --- Internal RPC DTOs ---

@Serializable
private data class GetInfoResponse(
    val height: Long,
    val target_height: Long = 0,
    val difficulty: Long = 0,
    val tx_count: Long = 0,
    val tx_pool_size: Int = 0,
    val alt_blocks_count: Int = 0,
    val outgoing_connections_count: Int = 0,
    val incoming_connections_count: Int = 0,
    val white_peerlist_size: Int = 0,
    val grey_peerlist_size: Int = 0,
    val mainnet: Boolean = false,
    val testnet: Boolean = false,
    val stagenet: Boolean = false,
    val top_block_hash: String = "",
    val synchronized: Boolean = false,
    val version: String = ""
)

@Serializable
private data class GetHeightResponse(
    val height: Long,
    val status: String = ""
)

@Serializable
private data class GetBlockResponse(
    val block_header: BlockHeader,
    val tx_hashes: List<String>? = null,
    val miner_tx_hash: String? = null
)

@Serializable
private data class BlockHeader(
    val height: Long,
    val hash: String,
    val timestamp: Long,
    val prev_hash: String,
    val nonce: Long
)

@Serializable
private data class GetOutsResponse(
    val outs: List<OutEntry>
)

@Serializable
private data class OutEntry(
    val height: Long,
    val key: String,
    val mask: String,
    val txid: String,
    val unlocked: Boolean
)

@Serializable
private data class SendRawTxResponse(
    val status: String,
    val reason: String? = null,
    val double_spend: Boolean = false,
    val fee_too_low: Boolean = false,
    val invalid_input: Boolean = false,
    val invalid_output: Boolean = false,
    val too_big: Boolean = false,
    val overspend: Boolean = false,
    val tx_extra_too_big: Boolean = false
)

@Serializable
private data class GetFeeEstimateResponse(
    val fee: Long,
    val quantization_mask: Long = 1,
    val fees: List<Long>? = null
)

@Serializable
private data class GetPoolResponse(
    val transactions: List<PoolTxEntry>? = null
)

@Serializable
private data class PoolTxEntry(
    val id_hash: String,
    val blob_size: Int,
    val fee: Long,
    val receive_time: Long,
    val kept_by_block: Boolean
)
