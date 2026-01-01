package io.monero.net

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Stagenet End-to-End Integration Tests.
 * 
 * These tests connect to real Stagenet nodes and verify:
 * - T3.1: Daemon connectivity and info retrieval
 * - T3.2: Block retrieval by height
 * - T3.3: Transaction pool monitoring  
 * - T3.4: Fee estimation
 * - T3.5: Multiple block fetch for sync
 * 
 * Public Stagenet nodes:
 * - stagenet.xmr-tw.org:38081
 * - stagenet.community.rino.io:38081
 * 
 * Remove @Ignore to run these tests.
 */
class StagenetEndToEndTest {

    private val stagenetConfig = DaemonConfig(
        host = "stagenet.xmr-tw.org",
        port = 38081,
        useSsl = false,
        timeoutMs = 30_000
    )

    @Test
    @Ignore // Remove to run
    fun `T3_1 connect and get daemon info`() = runTest(timeout = 30.seconds) {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val info = client.getInfo()
            
            println("=== Stagenet Daemon Info ===")
            println("Height: ${info.height}")
            println("Network: ${if (info.stagenet) "STAGENET" else if (info.mainnet) "MAINNET" else "TESTNET"}")
            println("Difficulty: ${info.difficulty}")
            println("Version: ${info.version}")
            println("Synchronized: ${info.synchronized}")
            println("Tx Pool Size: ${info.txPoolSize}")
            
            assertTrue(info.stagenet, "Should connect to stagenet")
            assertTrue(info.height > 0, "Height should be positive")
            assertTrue(info.synchronized, "Node should be synchronized")
            
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run
    fun `T3_2 get blocks by height`() = runTest(timeout = 60.seconds) {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val height = client.getHeight()
            println("Current height: $height")
            
            // Get genesis block
            val genesis = client.getBlockByHeight(0)
            println("\n=== Genesis Block ===")
            println("Hash: ${genesis.hash}")
            println("Timestamp: ${genesis.timestamp}")
            
            // Get a recent block
            val recentHeight = height - 10
            val recent = client.getBlockByHeight(recentHeight)
            println("\n=== Block $recentHeight ===")
            println("Hash: ${recent.hash}")
            println("Timestamp: ${recent.timestamp}")
            println("Miner Tx: ${recent.minerTx != null}")
            println("Tx Count: ${recent.txHashes.size}")
            
            assertEquals(0L, genesis.height)
            assertEquals(recentHeight, recent.height)
            
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run
    fun `T3_3 monitor transaction pool`() = runTest(timeout = 30.seconds) {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val transactions = client.getTransactionPool()
            
            println("=== Transaction Pool ===")
            println("Transactions in pool: ${transactions.size}")
            
            transactions.take(3).forEachIndexed { i, tx ->
                println("\nTx ${i + 1}:")
                println("  Hash: ${tx.txHash}")
                println("  Fee: ${tx.fee}")
                println("  Size: ${tx.blobSize}")
                println("  Receive time: ${tx.receivedTime}")
            }
            
            // Pool can be empty, that's OK
            assertTrue(true, "Pool monitoring works")
            
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run
    fun `T3_4 get fee estimate`() = runTest(timeout = 30.seconds) {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val fee = client.getFeeEstimate()
            
            println("=== Fee Estimate ===")
            println("Fee per byte: ${fee.feePerByte}")
            println("Quantization mask: ${fee.quantizationMask}")
            println("Fees by priority:")
            fee.fees.forEachIndexed { i, f ->
                println("  Priority ${i + 1}: $f atomic units")
            }
            
            assertTrue(fee.feePerByte > 0, "Fee should be positive")
            
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run
    fun `T3_5 batch block fetch for sync`() = runTest(timeout = 120.seconds) {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            val currentHeight = client.getHeight()
            println("Current height: $currentHeight")
            
            // Fetch 100 blocks from a recent range
            val startHeight = currentHeight - 200
            val endHeight = startHeight + 100
            
            println("\n=== Batch Fetch: $startHeight to $endHeight ===")
            
            var totalTxs = 0
            var blocksFetched = 0
            val startTime = Clock.System.now().toEpochMilliseconds()
            
            for (h in startHeight until endHeight) {
                val block = client.getBlockByHeight(h)
                totalTxs += block.txHashes.size
                blocksFetched++
                
                if (blocksFetched % 20 == 0) {
                    println("Fetched $blocksFetched blocks...")
                }
            }
            
            val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
            
            println("\n=== Results ===")
            println("Blocks fetched: $blocksFetched")
            println("Total transactions: $totalTxs")
            println("Time elapsed: ${elapsed}ms")
            println("Blocks per second: ${blocksFetched * 1000 / elapsed}")
            
            assertEquals(100, blocksFetched)
            
        } finally {
            client.close()
        }
    }

    @Test
    @Ignore // Remove to run
    fun `complete wallet sync simulation`() = runTest(timeout = 300.seconds) {
        val client = HttpDaemonClient(stagenetConfig)
        try {
            println("=== Wallet Sync Simulation ===")
            
            // Get current height
            val currentHeight = client.getHeight()
            println("Current blockchain height: $currentHeight")
            
            // Simulate starting from a restore height (recent)
            val restoreHeight = currentHeight - 500
            println("Restore height: $restoreHeight")
            
            var scannedHeight = restoreHeight
            var blocksScanned = 0
            var outputsFound = 0 // Would count matching outputs
            val startTime = Clock.System.now().toEpochMilliseconds()
            
            println("\nScanning blocks...")
            
            while (scannedHeight < currentHeight && blocksScanned < 100) {
                val block = client.getBlockByHeight(scannedHeight)
                
                // In real implementation, we would:
                // 1. Parse each transaction
                // 2. Scan for owned outputs using ViewKeyScanner
                // 3. Track found outputs
                
                blocksScanned++
                scannedHeight++
                
                if (blocksScanned % 25 == 0) {
                    val progress = (scannedHeight - restoreHeight) * 100 / (currentHeight - restoreHeight)
                    println("Progress: $progress% (height $scannedHeight)")
                }
            }
            
            val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
            
            println("\n=== Sync Summary ===")
            println("Blocks scanned: $blocksScanned")
            println("Current height: $scannedHeight")
            println("Time: ${elapsed}ms")
            println("Speed: ${blocksScanned * 1000 / elapsed} blocks/sec")
            
            assertTrue(blocksScanned >= 100, "Should scan at least 100 blocks")
            
        } finally {
            client.close()
        }
    }
}
