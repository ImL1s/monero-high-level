/// Transaction and output data models for Monero wallet.
library;

import 'dart:typed_data';

/// RingCT signature types
enum RctType {
  /// No RingCT (pre-RingCT transaction)
  none(0),
  /// Full RingCT (all amounts hidden)
  full(1),
  /// Simple RingCT
  simple(2),
  /// Bulletproofs
  bulletproof(3),
  /// Bulletproofs 2
  bulletproof2(4),
  /// CLSAG
  clsag(5),
  /// Bulletproofs+
  bulletproofPlus(6);

  final int value;
  const RctType(this.value);

  static RctType fromValue(int value) {
    return RctType.values.firstWhere(
      (e) => e.value == value,
      orElse: () => RctType.none,
    );
  }
}

/// Transaction output target (stealth address)
class TxOutTarget {
  /// Output public key (32 bytes)
  final Uint8List key;
  
  /// Optional view tag for fast scanning (1 byte)
  final int? viewTag;

  TxOutTarget({required this.key, this.viewTag});

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TxOutTarget &&
          _bytesEqual(key, other.key) &&
          viewTag == other.viewTag;

  @override
  int get hashCode => Object.hash(key, viewTag);
}

/// A single transaction output
class TxOutput {
  /// Output index within the transaction
  final int index;
  
  /// Amount in atomic units (0 for RingCT, real amount is encrypted)
  final int amount;
  
  /// Output target containing the public key
  final TxOutTarget target;
  
  /// Global output index on the blockchain
  final int globalIndex;

  TxOutput({
    required this.index,
    required this.amount,
    required this.target,
    this.globalIndex = -1,
  });
}

/// Key image used to prevent double-spending
class KeyImage {
  final Uint8List bytes;

  KeyImage(this.bytes) {
    if (bytes.length != 32) {
      throw ArgumentError('Key image must be 32 bytes');
    }
  }

  factory KeyImage.fromHex(String hex) {
    if (hex.length != 64) {
      throw ArgumentError('Key image hex must be 64 characters');
    }
    return KeyImage(_hexToBytes(hex));
  }

  String toHex() => _bytesToHex(bytes);

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is KeyImage && _bytesEqual(bytes, other.bytes);

  @override
  int get hashCode => Object.hashAll(bytes);
}

/// A transaction input (spending a previous output)
class TxInput {
  /// Amount being spent (0 for RingCT)
  final int amount;
  
  /// Key offsets for ring members (relative offsets)
  final List<int> keyOffsets;
  
  /// Key image to prevent double-spending
  final Uint8List keyImage;

  TxInput({
    required this.amount,
    required this.keyOffsets,
    required this.keyImage,
  });
}

/// ECDH info for encrypted amount recovery
class EcdhInfo {
  /// Encrypted mask
  final Uint8List mask;
  
  /// Encrypted amount
  final Uint8List amount;

  EcdhInfo({required this.mask, required this.amount});
}

/// RingCT signature data
class RctSignature {
  /// RingCT type
  final RctType type;
  
  /// Transaction fee in atomic units
  final int txnFee;
  
  /// ECDH info for each output (encrypted amounts)
  final List<EcdhInfo> ecdhInfo;
  
  /// Output commitments
  final List<Uint8List> outPk;

  RctSignature({
    required this.type,
    required this.txnFee,
    required this.ecdhInfo,
    required this.outPk,
  });
}

/// Transaction extra field parsed components
class TxExtra {
  /// Transaction public key
  final Uint8List? txPubKey;
  
  /// Additional public keys (for subaddresses)
  final List<Uint8List> additionalPubKeys;
  
  /// Payment ID (encrypted or plain)
  final Uint8List? paymentId;
  
  /// Raw extra bytes
  final Uint8List raw;

  TxExtra({
    this.txPubKey,
    this.additionalPubKeys = const [],
    this.paymentId,
    required this.raw,
  });
}

/// Complete Monero transaction
class Transaction {
  /// Transaction hash
  final Uint8List hash;
  
  /// Transaction version
  final int version;
  
  /// Unlock time (block height or timestamp)
  final int unlockTime;
  
  /// Transaction inputs
  final List<TxInput> inputs;
  
  /// Transaction outputs
  final List<TxOutput> outputs;
  
  /// Extra field
  final TxExtra extra;
  
  /// RingCT signature (null for pre-RingCT)
  final RctSignature? rctSignature;
  
  /// Block height (-1 if in mempool)
  final int blockHeight;
  
  /// Block timestamp
  final int timestamp;
  
  /// Whether this is a coinbase transaction
  final bool isCoinbase;

  Transaction({
    required this.hash,
    required this.version,
    required this.unlockTime,
    required this.inputs,
    required this.outputs,
    required this.extra,
    this.rctSignature,
    this.blockHeight = -1,
    this.timestamp = 0,
    this.isCoinbase = false,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Transaction && _bytesEqual(hash, other.hash);

  @override
  int get hashCode => Object.hashAll(hash);
}

/// A wallet-owned output (decrypted and verified)
class OwnedOutput {
  /// Transaction hash
  final Uint8List txHash;
  
  /// Output index within transaction
  final int outputIndex;
  
  /// Global output index on blockchain
  final int globalIndex;
  
  /// Decrypted amount
  final int amount;
  
  /// Output public key
  final Uint8List publicKey;
  
  /// Key image (null if not yet computed)
  final Uint8List? keyImage;
  
  /// Block height
  final int blockHeight;
  
  /// Block timestamp
  final int timestamp;
  
  /// Whether output is spent
  final bool isSpent;
  
  /// Whether output is unlocked (spendable)
  final bool isUnlocked;
  
  /// Subaddress major index (account)
  final int subaddressMajor;
  
  /// Subaddress minor index
  final int subaddressMinor;
  
  /// Output commitment
  final Uint8List? commitment;
  
  /// Output mask (for RingCT)
  final Uint8List? mask;

  OwnedOutput({
    required this.txHash,
    required this.outputIndex,
    required this.globalIndex,
    required this.amount,
    required this.publicKey,
    this.keyImage,
    required this.blockHeight,
    required this.timestamp,
    this.isSpent = false,
    this.isUnlocked = false,
    this.subaddressMajor = 0,
    this.subaddressMinor = 0,
    this.commitment,
    this.mask,
  });

  /// Unique identifier for this output
  String get outpoint => '${_bytesToHex(txHash)}:$outputIndex';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OwnedOutput &&
          _bytesEqual(txHash, other.txHash) &&
          outputIndex == other.outputIndex;

  @override
  int get hashCode => Object.hash(Object.hashAll(txHash), outputIndex);
}

/// Subaddress index
class SubaddressIndex {
  /// Account index (0 = main account)
  final int major;
  
  /// Address index within account (0 = main address)
  final int minor;

  const SubaddressIndex(this.major, this.minor);

  static const main = SubaddressIndex(0, 0);

  bool get isMainAddress => major == 0 && minor == 0;
  bool get isSubaddress => !isMainAddress;

  @override
  String toString() => '($major,$minor)';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SubaddressIndex && major == other.major && minor == other.minor;

  @override
  int get hashCode => Object.hash(major, minor);
}

/// Transaction output with ownership info
class ScannedOutput {
  /// The transaction output
  final TxOutput output;
  
  /// Decrypted amount (if owned)
  final int? amount;
  
  /// Whether this output belongs to the wallet
  final bool isOwned;
  
  /// Subaddress index if owned
  final SubaddressIndex? subaddressIndex;

  ScannedOutput({
    required this.output,
    this.amount,
    required this.isOwned,
    this.subaddressIndex,
  });
}

// Utility functions
bool _bytesEqual(Uint8List a, Uint8List b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}

String _bytesToHex(Uint8List bytes) {
  return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
}

Uint8List _hexToBytes(String hex) {
  final result = Uint8List(hex.length ~/ 2);
  for (var i = 0; i < result.length; i++) {
    result[i] = int.parse(hex.substring(i * 2, i * 2 + 2), radix: 16);
  }
  return result;
}
