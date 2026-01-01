/// Pure Dart implementation of Monero wallet functionality.
///
/// This library provides:
/// - Cryptographic primitives (Keccak, Ed25519, Base58)
/// - Address handling (standard, subaddress, integrated)
/// - Mnemonic seed generation and recovery
/// - Transaction building and signing
/// - Wallet synchronization
library monero_dart;

// Constants
export 'src/constants.dart';

// Crypto
export 'src/crypto/keccak.dart';
export 'src/crypto/ed25519.dart';
export 'src/crypto/base58.dart';
export 'src/crypto/chacha20.dart';
export 'src/crypto/clsag.dart' hide RingMember;
export 'src/crypto/pedersen.dart';
export 'src/crypto/bulletproof_plus.dart';

// Core
export 'src/core/address.dart';
export 'src/core/mnemonic.dart';
export 'src/core/keys.dart';

// Network
export 'src/network/daemon_client.dart';
export 'src/network/rpc_utils.dart';

// Storage
export 'src/storage/wallet_storage.dart' hide WalletStorage;

// Transaction
export 'src/transaction/builder.dart';
export 'src/transaction/decoy_selection.dart';
export 'src/transaction/input_selection.dart';
export 'src/transaction/models.dart' hide TxInput, TxOutput;
export 'src/transaction/scanner.dart';
export 'src/transaction/offline_signing.dart';
export 'src/transaction/serializer.dart';
export 'src/transaction/tx_builder.dart';

// Sync
export 'src/sync/sync_manager.dart';

// Wallet
export 'src/wallet/wallet.dart' hide Network, OutputInfo;
