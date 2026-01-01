package io.monero.examples

import io.monero.core.*
import io.monero.core.sync.*
import io.monero.core.scanner.*
import io.monero.core.transaction.*
import io.monero.net.*
import io.monero.wallet.*
import io.monero.wallet.storage.*

/**
 * Complete example demonstrating the Monero KMP wallet workflow.
 * 
 * This example shows:
 * 1. Creating/restoring a wallet
 * 2. Connecting to a daemon
 * 3. Syncing the blockchain  
 * 4. Checking balance
 * 5. Building a transaction
 * 
 * Note: This is example code - do not run against mainnet with real funds!
 */
object WalletExample {

    /**
     * Example: Create a new wallet
     */
    fun createNewWallet(): KeyDerivation.WalletKeys {
        // Generate secure random entropy
        val entropy = ByteArray(32)
        java.security.SecureRandom().nextBytes(entropy)
        
        // Convert to mnemonic
        val mnemonic = Mnemonic.entropyToMnemonic(entropy)
        println("=== NEW WALLET ===")
        println("Mnemonic (SAVE THIS!):")
        println(mnemonic.joinToString(" "))
        println()
        
        // Derive wallet keys
        val keys = KeyDerivation.deriveWalletKeys(entropy)
        val address = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.STAGENET)
        
        println("Primary Address: ${address.rawAddress}")
        println("Private View Key: ${keys.privateViewKey.toHex()}")
        println("Public Spend Key: ${keys.publicSpendKey.toHex()}")
        
        return keys
    }

    /**
     * Example: Restore wallet from mnemonic
     */
    fun restoreFromMnemonic(words: List<String>): KeyDerivation.WalletKeys {
        // Validate mnemonic
        require(Mnemonic.validate(words)) { "Invalid mnemonic" }
        
        // Derive keys
        val keys = KeyDerivation.deriveFromMnemonic(words)
        val address = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.STAGENET)
        
        println("=== RESTORED WALLET ===")
        println("Address: ${address.rawAddress}")
        
        return keys
    }

    /**
     * Example: Connect to daemon and get info
     */
    suspend fun connectToDaemon(): HttpDaemonClient {
        val config = DaemonConfig(
            host = "stagenet.xmr-tw.org",
            port = 38081,
            useSsl = false,
            timeoutMs = 30_000
        )
        
        val client = HttpDaemonClient(config)
        
        // Test connection
        val info = client.getInfo()
        println("=== DAEMON INFO ===")
        println("Height: ${info.height}")
        println("Network: ${if (info.stagenet) "STAGENET" else "MAINNET"}")
        println("Synchronized: ${info.synchronized}")
        println("Tx Pool Size: ${info.txPoolSize}")
        
        return client
    }

    /**
     * Example: Get fee estimate
     */
    suspend fun getFeeEstimate(client: DaemonClient) {
        val fee = client.getFeeEstimate()
        
        println("=== FEE ESTIMATE ===")
        println("Fee per byte: ${fee.feePerByte} atomic units")
        println("Fees by priority:")
        fee.fees.forEachIndexed { i, f ->
            val priority = when (i) {
                0 -> "Default"
                1 -> "Low"
                2 -> "Medium"
                3 -> "High"
                else -> "Priority $i"
            }
            println("  $priority: $f")
        }
    }

    /**
     * Example: Generate subaddresses
     */
    fun generateSubaddresses(keys: KeyDerivation.WalletKeys, count: Int) {
        println("=== SUBADDRESSES ===")
        
        // Primary address (0, 0)
        val primary = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.STAGENET)
        println("[0,0] Primary: ${primary.rawAddress}")
        
        // Generate subaddresses
        for (minor in 1..count) {
            val subKeys = KeyDerivation.deriveSubaddress(keys, 0, minor)
            println("[0,$minor] Spend Key: ${subKeys.publicSpendKey.toHex().take(16)}...")
        }
    }

    /**
     * Example: Scan transaction for owned outputs
     */
    fun scanTransaction(keys: KeyDerivation.WalletKeys, tx: Transaction): List<ScannedOutput> {
        val scanner = ViewKeyScanner(
            viewPublicKey = keys.publicViewKey,
            viewSecretKey = keys.privateViewKey,
            spendPublicKey = keys.publicSpendKey
        )
        
        // Precompute subaddress table
        scanner.precomputeSubaddresses(majorMax = 5, minorMax = 50)
        
        // Scan
        val found = scanner.scanTransaction(tx)
        
        if (found.isNotEmpty()) {
            println("=== FOUND OUTPUTS ===")
            for (output in found) {
                val amountXmr = (output.amount ?: 0L) / 1_000_000_000_000.0
                val major = output.subaddressIndex?.first ?: 0
                val minor = output.subaddressIndex?.second ?: 0
                println("Amount: $amountXmr XMR")
                println("Subaddress: [$major,$minor]")
            }
        }
        
        return found
    }

    /**
     * Example: Build a transaction  
     * 
     * Note: This is a simplified example. In production you would:
     * - Use SpendableOutput instead of OwnedOutput
     * - Configure proper output/decoy providers
     * - Handle fee estimation properly
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun buildTransactionExample(
        client: DaemonClient,
        keys: KeyDerivation.WalletKeys,
        recipient: MoneroAddress,
        amount: Long
    ) {
        val fee = client.getFeeEstimate()
        val changeAddress = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.STAGENET)
        
        println("=== TRANSACTION BUILD EXAMPLE ===")
        println("To build a transaction you need:")
        println("1. SpendableOutput list with key images")
        println("2. OutputProvider for decoy selection")
        println("3. SelectionConfig for input selection")
        println()
        println("Recipient: ${recipient.rawAddress}")
        println("Amount: ${amount / 1_000_000_000_000.0} XMR")
        println("Fee per byte: ${fee.feePerByte} atomic units")
        println("Change address: ${changeAddress.rawAddress}")
        println()
        println("See TxBuilder for full transaction building API")
    }

    /**
     * Example: Full wallet workflow
     */
    suspend fun fullWorkflow() {
        println("========================================")
        println("        MONERO KMP WALLET EXAMPLE      ")
        println("========================================\n")
        
        // 1. Create wallet
        val keys = createNewWallet()
        println()
        
        // 2. Generate subaddresses
        generateSubaddresses(keys, 5)
        println()
        
        // 3. Connect to daemon
        val client = connectToDaemon()
        println()
        
        // 4. Get fee estimate
        getFeeEstimate(client)
        println()
        
        // 5. (Would sync and scan here in real implementation)
        println("=== NEXT STEPS ===")
        println("1. Fund the wallet with stagenet XMR")
        println("2. Sync blockchain to find incoming transactions")
        println("3. Scan transactions to discover owned outputs")
        println("4. Build and send transactions")
        
        // Cleanup
        client.close()
    }

    // Helper extension
    private fun ByteArray.toHex(): String = 
        joinToString("") { "%02x".format(it) }
}

/**
 * Run the example
 */
suspend fun main() {
    WalletExample.fullWorkflow()
}
