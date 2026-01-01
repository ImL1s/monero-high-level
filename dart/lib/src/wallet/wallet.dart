/// Monero Wallet Interface
///
/// High-level wallet API for all Monero operations.
library;

import 'dart:typed_data';

/// Network type enumeration
enum Network { mainnet, stagenet, testnet }

/// Transaction priority levels
enum TxPriority {
  default_(0),
  low(1),
  medium(2),
  high(3),
  highest(4);

  final int value;
  const TxPriority(this.value);
}

/// Synchronization status
sealed class SyncStatus {
  const SyncStatus();
}

class SyncNotStarted extends SyncStatus {
  const SyncNotStarted();
}

class Syncing extends SyncStatus {
  final int currentHeight;
  final int targetHeight;
  const Syncing(this.currentHeight, this.targetHeight);

  double get progress => targetHeight > 0 ? currentHeight / targetHeight : 0;
}

class Synced extends SyncStatus {
  const Synced();
}

class SyncError extends SyncStatus {
  final String message;
  const SyncError(this.message);
}

/// Wallet balance
class WalletBalance {
  final BigInt balance;
  final BigInt unlockedBalance;
  final BigInt pendingBalance;

  const WalletBalance({
    required this.balance,
    required this.unlockedBalance,
    required this.pendingBalance,
  });

  static final zero = WalletBalance(
    balance: BigInt.zero,
    unlockedBalance: BigInt.zero,
    pendingBalance: BigInt.zero,
  );
}

/// Wallet configuration
class WalletConfig {
  final String path;
  final String password;
  final Network network;
  final String daemonAddress;

  const WalletConfig({
    required this.path,
    required this.password,
    this.network = Network.mainnet,
    this.daemonAddress = 'localhost:18081',
  });
}

/// Transaction destination
class Destination {
  final String address;
  final BigInt amount;

  const Destination({required this.address, required this.amount});
}

/// Transaction configuration
class TxConfig {
  final List<Destination> destinations;
  final TxPriority priority;
  final int accountIndex;
  final List<int>? subaddressIndices;
  final Uint8List? paymentId;
  final bool sweepAll;

  const TxConfig({
    required this.destinations,
    this.priority = TxPriority.default_,
    this.accountIndex = 0,
    this.subaddressIndices,
    this.paymentId,
    this.sweepAll = false,
  });
}

/// Pending transaction
class PendingTransaction {
  final String hash;
  final BigInt fee;
  final BigInt amount;
  final Uint8List blob;
  final bool signed;

  const PendingTransaction({
    required this.hash,
    required this.fee,
    required this.amount,
    required this.blob,
    required this.signed,
  });
}

/// Subaddress info
class SubaddressInfo {
  final int accountIndex;
  final int addressIndex;
  final String address;
  final String label;
  final BigInt balance;
  final BigInt unlockedBalance;
  final bool used;

  const SubaddressInfo({
    required this.accountIndex,
    required this.addressIndex,
    required this.address,
    required this.label,
    required this.balance,
    required this.unlockedBalance,
    required this.used,
  });
}

/// Transaction info
class TransactionInfo {
  final String hash;
  final int? height;
  final int timestamp;
  final BigInt fee;
  final BigInt amount;
  final bool incoming;
  final int confirmations;
  final int accountIndex;
  final List<int> subaddressIndices;
  final String? paymentId;
  final String? note;

  const TransactionInfo({
    required this.hash,
    this.height,
    required this.timestamp,
    required this.fee,
    required this.amount,
    required this.incoming,
    required this.confirmations,
    required this.accountIndex,
    required this.subaddressIndices,
    this.paymentId,
    this.note,
  });

  bool get isConfirmed => height != null;
}

/// Output info
class OutputInfo {
  final Uint8List keyImage;
  final Uint8List publicKey;
  final BigInt amount;
  final int globalIndex;
  final int height;
  final int accountIndex;
  final int subaddressIndex;
  final bool spent;
  final bool frozen;
  final int unlockTime;

  const OutputInfo({
    required this.keyImage,
    required this.publicKey,
    required this.amount,
    required this.globalIndex,
    required this.height,
    required this.accountIndex,
    required this.subaddressIndex,
    required this.spent,
    required this.frozen,
    required this.unlockTime,
  });
}

/// Key image export data
class KeyImageExport {
  final Uint8List keyImage;
  final Uint8List signature;

  const KeyImageExport({required this.keyImage, required this.signature});
}

/// Key image import result
class KeyImageImportResult {
  final int height;
  final BigInt spent;
  final BigInt unspent;

  const KeyImageImportResult({
    required this.height,
    required this.spent,
    required this.unspent,
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Proof & Multisig Result Types (D5.7 / D5.3)
// ─────────────────────────────────────────────────────────────────────────────

/// Transaction proof verification result.
class TxProofResult {
  /// Whether the proof is valid
  final bool good;

  /// Amount received (in atomic units)
  final BigInt received;

  /// Number of confirmations (0 if in pool)
  final int confirmations;

  /// Whether transaction is in the mempool
  final bool inPool;

  const TxProofResult({
    required this.good,
    required this.received,
    required this.confirmations,
    required this.inPool,
  });
}

/// Reserve proof verification result.
class ReserveProofResult {
  /// Whether the proof is valid
  final bool good;

  /// Total amount proven (in atomic units)
  final BigInt total;

  /// Amount already spent from the proven outputs
  final BigInt spent;

  const ReserveProofResult({
    required this.good,
    required this.total,
    required this.spent,
  });
}

/// Multisig wallet status.
class MultisigStatus {
  /// Whether wallet is multisig
  final bool isMultisig;

  /// Whether multisig setup is complete and ready
  final bool isReady;

  /// Threshold (M in M-of-N)
  final int threshold;

  /// Total participants (N in M-of-N)
  final int total;

  const MultisigStatus({
    required this.isMultisig,
    required this.isReady,
    required this.threshold,
    required this.total,
  });
}

/// Result of makeMultisig or exchangeMultisigKeys.
class MultisigMakeResult {
  /// Multisig wallet address (may be empty if more rounds needed)
  final String address;

  /// Multisig info for next round (empty if setup complete)
  final String multisigInfo;

  const MultisigMakeResult({
    required this.address,
    required this.multisigInfo,
  });
}

/// Result of signing a multisig transaction.
class MultisigSignResult {
  /// Signed transaction data (hex) for submission or next signer
  final String txDataHex;

  /// Transaction hashes if fully signed
  final List<String> txHashList;

  const MultisigSignResult({
    required this.txDataHex,
    required this.txHashList,
  });
}

/// Main wallet interface for Monero operations.
abstract class MoneroWallet {
  /// Primary wallet address
  String get primaryAddress;

  /// Wallet balance stream
  Stream<WalletBalance> get balanceStream;

  /// Sync status stream
  Stream<SyncStatus> get syncStatusStream;

  /// Current sync height stream
  Stream<int> get syncHeightStream;

  /// Network type
  Network get network;

  /// Check if this is a view-only wallet
  bool get isViewOnly;

  /// Start synchronization
  Future<void> sync({int? startHeight});

  /// Stop synchronization
  void stopSync();

  /// Refresh wallet (quick sync)
  Future<void> refresh();

  /// Create a new transaction
  Future<PendingTransaction> createTransaction(TxConfig config);

  /// Submit a signed transaction
  Future<String> submitTransaction(PendingTransaction tx);

  /// Get address for account and index
  String getAddress({int accountIndex = 0, int addressIndex = 0});

  /// Get all addresses for an account
  List<SubaddressInfo> getAddresses({int accountIndex = 0});

  /// Create new subaddress
  Future<SubaddressInfo> createSubaddress({
    int accountIndex = 0,
    String label = '',
  });

  /// Get transaction history
  Future<List<TransactionInfo>> getTransactions({
    int? accountIndex,
    int? subaddressIndex,
    bool pending = true,
  });

  /// Get outputs (UTXOs)
  Future<List<OutputInfo>> getOutputs({
    bool spent = false,
    bool frozen = true,
  });

  /// Freeze an output
  Future<void> freezeOutput(Uint8List keyImage);

  /// Thaw a frozen output
  Future<void> thawOutput(Uint8List keyImage);

  /// Export outputs for offline signing
  Future<Uint8List> exportOutputs({bool all = false});

  /// Import outputs from offline wallet
  Future<int> importOutputs(Uint8List data);

  /// Export key images
  Future<List<KeyImageExport>> exportKeyImages({bool all = false});

  /// Import key images
  Future<KeyImageImportResult> importKeyImages(List<KeyImageExport> keyImages);

  // ─────────────────────────────────────────────────────────────────────────
  // Proof Tools (D5.7)
  // ─────────────────────────────────────────────────────────────────────────

  /// Get a proof for a transaction sent to a specific address.
  ///
  /// [txHash] Transaction hash
  /// [address] Destination address
  /// [message] Optional message to include in proof
  /// Returns proof signature string
  Future<String> getTxProof(String txHash, String address, {String? message});

  /// Verify a transaction proof.
  ///
  /// [txHash] Transaction hash
  /// [address] Address the proof is for
  /// [signature] Proof signature
  /// [message] Optional message (must match what was used to generate proof)
  Future<TxProofResult> checkTxProof(
    String txHash,
    String address,
    String signature, {
    String? message,
  });

  /// Get a reserve proof showing wallet owns a certain amount.
  ///
  /// [all] If true, proves entire balance; if false, prove specific amount
  /// [accountIndex] Account index (used when all=false)
  /// [amount] Amount to prove (used when all=false)
  /// [message] Optional message to include
  Future<String> getReserveProof({
    bool all = true,
    int? accountIndex,
    BigInt? amount,
    String? message,
  });

  /// Verify a reserve proof.
  ///
  /// [address] Address that generated the proof
  /// [signature] Proof signature
  /// [message] Optional message (must match what was used to generate proof)
  Future<ReserveProofResult> checkReserveProof(
    String address,
    String signature, {
    String? message,
  });

  /// Sign an arbitrary message with wallet's spend key.
  Future<String> signMessage(String message);

  /// Verify a message signature.
  ///
  /// [message] Original message
  /// [address] Address that signed the message
  /// [signature] Signature to verify
  Future<bool> verifyMessage(String message, String address, String signature);

  // ─────────────────────────────────────────────────────────────────────────
  // Multisig (D5.3)
  // ─────────────────────────────────────────────────────────────────────────

  /// Check if wallet is multisig.
  Future<MultisigStatus> isMultisig();

  /// Prepare wallet for multisig. First step of multisig setup.
  /// Returns multisig info string to share with other participants.
  Future<String> prepareMultisig();

  /// Make wallet multisig using info from other participants.
  ///
  /// [multisigInfos] Multisig info strings from all participants
  /// [threshold] Number of signatures required
  Future<MultisigMakeResult> makeMultisig(
    List<String> multisigInfos,
    int threshold,
  );

  /// Exchange multisig keys (for N-of-N or additional rounds).
  ///
  /// [multisigInfo] Multisig info from other participant
  /// [force] Force update (use with caution)
  Future<MultisigMakeResult> exchangeMultisigKeys(
    String multisigInfo, {
    bool force = false,
  });

  /// Export multisig info for syncing with other participants.
  Future<String> exportMultisigInfo();

  /// Import multisig info from other participants.
  /// Returns number of outputs imported.
  Future<int> importMultisigInfo(List<String> infos);

  /// Sign a multisig transaction.
  ///
  /// [txDataHex] Transaction data from another participant
  Future<MultisigSignResult> signMultisigTx(String txDataHex);

  /// Submit a multisig transaction that has enough signatures.
  ///
  /// [txDataHex] Fully signed transaction data
  /// Returns list of transaction hashes.
  Future<List<String>> submitMultisigTx(String txDataHex);

  /// Close wallet
  Future<void> close();

  /// Save wallet state
  Future<void> save();

  /// Create a new wallet
  static Future<MoneroWallet> create(WalletConfig config) async {
    throw UnimplementedError('MoneroWallet.create not implemented');
  }

  /// Restore wallet from mnemonic
  static Future<MoneroWallet> restore(
    WalletConfig config,
    List<String> mnemonic, {
    int restoreHeight = 0,
  }) async {
    throw UnimplementedError('MoneroWallet.restore not implemented');
  }

  /// Open existing wallet
  static Future<MoneroWallet> open(WalletConfig config) async {
    throw UnimplementedError('MoneroWallet.open not implemented');
  }
}
