# Monero KMP Library

A pure Kotlin Multiplatform implementation of Monero wallet functionality.

## Supported Platforms

- **JVM** (Android, Desktop)
- **macOS ARM64**
- **iOS** (coming soon)

## Features

- ✅ Mnemonic generation and validation
- ✅ Key derivation (spend key, view key)
- ✅ Address generation (standard, subaddress, integrated)
- ✅ Transaction building (RingCT, Bulletproofs+, CLSAG)
- ✅ Transaction scanning (view-only wallet support)
- ✅ Daemon RPC client
- ✅ Blockchain sync
- ✅ Wallet storage with encryption

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.monero:monero-wallet:0.1.0")
}
```

## Quick Start

### 1. Create a New Wallet

```kotlin
import io.monero.core.*

// Generate a new mnemonic
val entropy = ByteArray(32).also { SecureRandom.nextBytes(it) }
val mnemonic = Mnemonic.entropyToMnemonic(entropy)
println("Your mnemonic: ${mnemonic.joinToString(" ")}")

// Derive wallet keys
val keys = KeyDerivation.deriveWalletKeys(entropy)
val address = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.MAINNET)
println("Your address: ${address.rawAddress}")
```

### 2. Restore from Mnemonic

```kotlin
import io.monero.core.*

val mnemonic = listOf(
    "sequence", "atlas", "unveil", "summon", "pebbles",
    // ... remaining words
)

// Derive keys from mnemonic
val keys = KeyDerivation.deriveFromMnemonic(mnemonic)
val address = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.STAGENET)
println("Restored address: ${address.rawAddress}")
```

### 3. Connect to Daemon

```kotlin
import io.monero.net.*

val config = DaemonConfig(
    host = "stagenet.xmr-tw.org",
    port = 38081,
    useSsl = false,
    timeoutMs = 30_000
)

val client = HttpDaemonClient(config)

// Get daemon info
val info = client.getInfo()
println("Height: ${info.height}")
println("Network: ${if (info.stagenet) "STAGENET" else "MAINNET"}")

// Get fee estimate
val fee = client.getFeeEstimate()
println("Fee per byte: ${fee.feePerByte}")

client.close()
```

### 4. Sync Blockchain

```kotlin
import io.monero.core.sync.*
import kotlinx.coroutines.flow.collect

val syncManager = DefaultSyncManager(client)

// Start sync
syncManager.startSync(restoreHeight = 100000)

// Monitor progress
syncManager.state.collect { state ->
    when (state) {
        is SyncState.Syncing -> {
            println("Progress: ${state.currentHeight}/${state.targetHeight}")
        }
        is SyncState.Synced -> {
            println("Synced at height ${state.height}!")
        }
        is SyncState.Error -> {
            println("Error: ${state.message}")
        }
    }
}
```

### 5. Scan for Incoming Transactions

```kotlin
import io.monero.core.scanner.*

val scanner = ViewKeyScanner(
    viewPublicKey = keys.publicViewKey,
    viewSecretKey = keys.privateViewKey,
    spendPublicKey = keys.publicSpendKey
)

// Precompute subaddress table for faster scanning
scanner.precomputeSubaddresses(majorMax = 10, minorMax = 100)

// Scan a transaction
val ownedOutputs = scanner.scanTransaction(transaction)
for (output in ownedOutputs) {
    println("Found: ${output.amount} piconero at subaddress [${output.subaddressIndex}]")
}
```

### 6. Build and Send Transaction

```kotlin
import io.monero.core.transaction.*

// Prepare inputs (from your owned outputs)
val inputs = listOf(
    TxInput(
        amount = 1_000_000_000L,  // 1 XMR
        keyImage = "...",
        ringMembers = getRingMembers(client),
        // ...
    )
)

// Build transaction
val builder = TxBuilder(
    inputs = inputs,
    destinations = listOf(
        TxDestination(
            address = recipientAddress,
            amount = 500_000_000L  // 0.5 XMR
        )
    ),
    changeAddress = myAddress,
    feePerByte = fee.feePerByte
)

val transaction = builder.build()
println("Transaction size: ${transaction.blob.size} bytes")
println("Fee: ${transaction.fee}")

// Send transaction
val txHash = client.sendRawTransaction(transaction.blob)
println("Sent! TX Hash: $txHash")
```

### 7. Generate Subaddresses

```kotlin
import io.monero.core.*

// Generate subaddresses for account 0
for (minor in 1..10) {
    val subKeys = KeyDerivation.deriveSubaddress(keys, 0, minor)
    val subAddress = MoneroAddress.createSubaddress(
        subKeys.publicSpendKey,
        subKeys.publicViewKey,
        MoneroAddress.Network.MAINNET
    )
    println("Subaddress [0,$minor]: $subAddress")
}
```

### 8. View-Only Wallet

```kotlin
import io.monero.wallet.*

// Create view-only wallet (no spend key)
val viewOnlyWallet = WalletFactory.restoreFromKeys(
    privateViewKey = keys.privateViewKey,
    publicSpendKey = keys.publicSpendKey,
    network = NetworkType.MAINNET,
    restoreHeight = 100000
)

// Can scan transactions but cannot spend
val balance = viewOnlyWallet.getBalance()
println("Balance: ${balance.total} (${balance.unlocked} unlocked)")
```

## Module Structure

```
monero-crypto/    # Cryptographic primitives (Keccak, Ed25519, Base58)
monero-core/      # Core wallet logic (keys, addresses, transactions)
monero-net/       # Network client (daemon RPC)
monero-wallet/    # High-level wallet API
monero-storage/   # Wallet persistence
```

## Platform-Specific Notes

### Android

Add internet permission:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### iOS

Requires CocoaPods integration for crypto primitives.

### Web (WASM)

Currently not supported. Crypto operations require native implementations.

## Security

⚠️ **IMPORTANT**: This library handles sensitive cryptographic material.

- Never log or expose private keys
- Store mnemonics securely
- Use encrypted storage for wallet data
- Validate all addresses before sending

## Testing

```bash
# Run unit tests
./gradlew jvmTest

# Run integration tests (requires network)
./gradlew jvmTest --tests "*IntegrationTest"
```

## License

MIT License - see LICENSE file.

## Contributing

Contributions welcome! Please read CONTRIBUTING.md first.
