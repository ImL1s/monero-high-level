package io.monero.net

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import io.kotest.matchers.shouldBe

class WalletRpcClientTest {

    private fun makeClient(jsonResponse: String): WalletRpcClient {
        val engine = MockEngine { request ->
            request.url.encodedPath shouldBe "/json_rpc"
            respond(
                content = jsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        return HttpWalletRpcClient(WalletRpcConfig(host = "localhost", port = 18082), httpClient)
    }

    @Test
    fun `getTxKey parses tx_key`() = runTest {
        val client = makeClient("""{"jsonrpc":"2.0","id":"0","result":{"tx_key":"abc"}}""")
        client.getTxKey("txid") shouldBe "abc"
        client.close()
    }

    @Test
    fun `checkTxProof parses result`() = runTest {
        val client = makeClient(
            """{"jsonrpc":"2.0","id":"0","result":{"confirmations":482,"good":true,"in_pool":false,"received":1000}}"""
        )
        val result = client.checkTxProof(
            txid = "txid",
            address = "addr",
            signature = "sig",
            message = "msg"
        )
        result.good shouldBe true
        result.confirmations shouldBe 482
        result.inPool shouldBe false
        result.received shouldBe 1000
        client.close()
    }

    @Test
    fun `checkReserveProof parses result`() = runTest {
        val client = makeClient(
            """{"jsonrpc":"2.0","id":"0","result":{"good":true,"spent":0,"total":123}}"""
        )
        val result = client.checkReserveProof(address = "addr", signature = "sig")
        result.good shouldBe true
        result.total shouldBe 123
        client.close()
    }

    @Test
    fun `sign and verify parse results`() = runTest {
        val signClient = makeClient("""{"jsonrpc":"2.0","id":"0","result":{"signature":"SigV1xxx"}}""")
        signClient.sign("hello") shouldBe "SigV1xxx"
        signClient.close()

        val verifyClient = makeClient("""{"jsonrpc":"2.0","id":"0","result":{"good":true}}""")
        verifyClient.verify(data = "hello", address = "addr", signature = "SigV1xxx") shouldBe true
        verifyClient.close()
    }

    @Test
    fun `multisig responses parse`() = runTest {
        val isMultisigClient = makeClient(
            """{"jsonrpc":"2.0","id":"0","result":{"multisig":true,"ready":true,"threshold":2,"total":3}}"""
        )
        val status = isMultisigClient.isMultisig()
        status.multisig shouldBe true
        status.threshold shouldBe 2
        isMultisigClient.close()

        val prepareClient = makeClient(
            """{"jsonrpc":"2.0","id":"0","result":{"multisig_info":"MultisigV1..."}}"""
        )
        prepareClient.prepareMultisig().multisigInfo shouldBe "MultisigV1..."
        prepareClient.close()

        val signMultisigClient = makeClient(
            """{"jsonrpc":"2.0","id":"0","result":{"tx_data_hex":"deadbeef","tx_hash_list":["h1","h2"]}}"""
        )
        val signed = signMultisigClient.signMultisig("deadbeef")
        signed.txDataHex shouldBe "deadbeef"
        signed.txHashList.size shouldBe 2
        signMultisigClient.close()
    }
}
