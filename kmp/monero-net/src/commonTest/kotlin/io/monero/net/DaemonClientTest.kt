package io.monero.net

import kotlin.test.Test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

/**
 * DaemonClient unit tests.
 * Integration tests require a running monerod instance.
 */
class DaemonClientTest {

    @Test
    fun `DaemonConfig has sensible defaults`() {
        val config = DaemonConfig()
        config.host shouldBe "localhost"
        config.port shouldBe 18081
        config.useSsl shouldBe false
        config.timeoutMs shouldBe 30_000
    }

    @Test
    fun `DaemonConfig can be customized`() {
        val config = DaemonConfig(
            host = "node.example.com",
            port = 18089,
            useSsl = true,
            username = "user",
            password = "pass",
            timeoutMs = 60_000
        )
        config.host shouldBe "node.example.com"
        config.port shouldBe 18089
        config.useSsl shouldBe true
        config.username shouldBe "user"
        config.password shouldBe "pass"
        config.timeoutMs shouldBe 60_000
    }

    @Test
    fun `DaemonInfo data class works correctly`() {
        val info = DaemonInfo(
            height = 123456,
            targetHeight = 123456,
            difficulty = 1000000,
            txCount = 50000,
            txPoolSize = 10,
            altBlocksCount = 0,
            outgoingConnectionsCount = 8,
            incomingConnectionsCount = 4,
            whitePoolSize = 100,
            greyPoolSize = 200,
            mainnet = false,
            testnet = false,
            stagenet = true,
            topBlockHash = "abc123",
            synchronized = true,
            version = "0.18.3.0"
        )
        info.height shouldBe 123456
        info.stagenet shouldBe true
        info.synchronized shouldBe true
    }

    @Test
    fun `BlockInfo data class works correctly`() {
        val block = BlockInfo(
            height = 100,
            hash = "block_hash",
            timestamp = 1234567890,
            prevHash = "prev_hash",
            nonce = 42,
            txHashes = listOf("tx1", "tx2"),
            minerTx = "miner_tx_hash"
        )
        block.height shouldBe 100
        block.txHashes.size shouldBe 2
    }

    @Test
    fun `OutputInfo data class works correctly`() {
        val output = OutputInfo(
            height = 500,
            key = "output_key",
            mask = "output_mask",
            txid = "tx_id",
            unlocked = true
        )
        output.unlocked shouldBe true
    }

    @Test
    fun `TxSubmitResult captures success`() {
        val result = TxSubmitResult(success = true)
        result.success shouldBe true
        result.doubleSpend shouldBe false
    }

    @Test
    fun `TxSubmitResult captures failure reasons`() {
        val result = TxSubmitResult(
            success = false,
            reason = "Fee too low",
            feeTooLow = true
        )
        result.success shouldBe false
        result.feeTooLow shouldBe true
    }

    @Test
    fun `FeeEstimate data class works`() {
        val fee = FeeEstimate(
            feePerByte = 1000,
            quantizationMask = 1,
            fees = listOf(1000, 5000, 25000, 1000000)
        )
        fee.fees.size shouldBe 4
    }

    @Test
    fun `PoolTransaction data class works`() {
        val poolTx = PoolTransaction(
            txHash = "pool_tx_hash",
            blobSize = 1500,
            fee = 30000000,
            receivedTime = 1234567890,
            keptByBlock = false
        )
        poolTx.blobSize shouldBe 1500
    }

    @Test
    fun `HttpDaemonClient can be instantiated`() {
        val config = DaemonConfig(host = "localhost", port = 18081)
        val client = HttpDaemonClient(config)
        client shouldNotBe null
        client.close()
    }
}
