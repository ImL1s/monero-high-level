package io.monero.net

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.Ignore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.comparables.shouldBeGreaterThan

/**
 * Integration tests for DaemonClient against Stagenet.
 * 
 * These tests require network access and a running Stagenet node.
 * They are marked with @Ignore by default to avoid CI failures.
 * 
 * Public Stagenet nodes:
 * - stagenet.xmr-tw.org:38081
 * - stagenet.community.rino.io:38081
 */
class DaemonClientIntegrationTest {

    // Use a public stagenet node
    private val stagenetConfig = DaemonConfig(
        host = "stagenet.xmr-tw.org",
        port = 38081,
        useSsl = false,
        timeoutMs = 30_000
    )

    @Test
    @Ignore // Remove to run integration test
    fun `connect to stagenet and get info`() = runTest {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val info = client.getInfo()
            
            info.height shouldBeGreaterThan 0
            info.stagenet shouldBe true
            info.mainnet shouldBe false
            info.version shouldNotBe ""
            
            println("Stagenet height: ${info.height}")
            println("Version: ${info.version}")
            println("Synchronized: ${info.synchronized}")
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run integration test
    fun `get current height`() = runTest {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val height = client.getHeight()
            height shouldBeGreaterThan 0
            println("Current stagenet height: $height")
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run integration test  
    fun `get block by height`() = runTest {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val block = client.getBlockByHeight(1000)
            
            block.height shouldBe 1000
            block.hash shouldNotBe ""
            block.timestamp shouldBeGreaterThan 0
            
            println("Block 1000 hash: ${block.hash}")
            println("Timestamp: ${block.timestamp}")
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run integration test
    fun `get fee estimate`() = runTest {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val fee = client.getFeeEstimate()
            
            fee.feePerByte shouldBeGreaterThan 0
            println("Fee per byte: ${fee.feePerByte}")
            println("Fees by priority: ${fee.fees}")
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run integration test
    fun `get transaction pool`() = runTest {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val pool = client.getTransactionPool()
            println("Mempool size: ${pool.size}")
            pool.take(3).forEach { tx ->
                println("  TX: ${tx.txHash.take(16)}... fee=${tx.fee}")
            }
        } finally {
            client.close()
        }
    }
}
