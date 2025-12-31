/// View key scanner for detecting owned outputs in transactions.
library;

import 'dart:typed_data';

import '../crypto/ed25519.dart';
import '../crypto/keccak.dart';
import 'models.dart';

/// Scans transactions for outputs belonging to a wallet.
class ViewKeyScanner {
  /// Wallet's public view key
  final Uint8List viewPublicKey;
  
  /// Wallet's private view key
  final Uint8List viewSecretKey;
  
  /// Wallet's public spend key
  final Uint8List spendPublicKey;
  
  /// Precomputed subaddress lookup table
  final SubaddressTable _subaddressTable = SubaddressTable();

  ViewKeyScanner({
    required this.viewPublicKey,
    required this.viewSecretKey,
    required this.spendPublicKey,
  }) {
    if (viewPublicKey.length != 32) {
      throw ArgumentError('View public key must be 32 bytes');
    }
    if (viewSecretKey.length != 32) {
      throw ArgumentError('View secret key must be 32 bytes');
    }
    if (spendPublicKey.length != 32) {
      throw ArgumentError('Spend public key must be 32 bytes');
    }
  }

  /// Precompute subaddress public keys for fast lookup
  void precomputeSubaddresses({int majorMax = 0, int minorMax = 0}) {
    for (var major = 0; major <= majorMax; major++) {
      for (var minor = 0; minor <= minorMax; minor++) {
        final subaddressPubKey = _deriveSubaddressPublicKey(major, minor);
        _subaddressTable.add(subaddressPubKey, SubaddressIndex(major, minor));
      }
    }
  }

  /// Scan a transaction for owned outputs
  List<ScannedOutput> scanTransaction(Transaction tx) {
    final txPubKey = tx.extra.txPubKey;
    if (txPubKey == null) return [];
    
    final additionalPubKeys = tx.extra.additionalPubKeys;
    final results = <ScannedOutput>[];
    
    for (var i = 0; i < tx.outputs.length; i++) {
      final result = scanOutput(
        output: tx.outputs[i],
        outputIndex: i,
        txPubKey: txPubKey,
        additionalTxPubKey: i < additionalPubKeys.length ? additionalPubKeys[i] : null,
      );
      if (result != null) {
        results.add(result);
      }
    }
    
    return results;
  }

  /// Scan a single output for ownership
  ScannedOutput? scanOutput({
    required TxOutput output,
    required int outputIndex,
    required Uint8List txPubKey,
    Uint8List? additionalTxPubKey,
  }) {
    // Try with main tx public key first
    final derivedPubKey = _deriveOutputPublicKey(txPubKey, outputIndex);
    
    // Check view tag first if available (fast rejection)
    if (output.target.viewTag != null) {
      final expectedViewTag = _computeViewTag(txPubKey, outputIndex);
      if (output.target.viewTag != expectedViewTag) {
        // Try additional public key if view tag doesn't match
        if (additionalTxPubKey != null) {
          return _tryAdditionalKey(output, outputIndex, additionalTxPubKey);
        }
        return null;
      }
    }
    
    // Check if output belongs to main address
    if (_bytesEqual(derivedPubKey, output.target.key)) {
      return ScannedOutput(
        output: output,
        amount: null, // Will be decrypted separately
        isOwned: true,
        subaddressIndex: SubaddressIndex.main,
      );
    }
    
    // Check subaddresses
    final subaddressMatch = _checkSubaddress(derivedPubKey, output.target.key);
    if (subaddressMatch != null) {
      return ScannedOutput(
        output: output,
        amount: null,
        isOwned: true,
        subaddressIndex: subaddressMatch,
      );
    }
    
    // Try with additional public key (for subaddress outputs)
    if (additionalTxPubKey != null) {
      return _tryAdditionalKey(output, outputIndex, additionalTxPubKey);
    }
    
    return null;
  }

  ScannedOutput? _tryAdditionalKey(
    TxOutput output,
    int outputIndex,
    Uint8List additionalTxPubKey,
  ) {
    final derivedPubKey = _deriveOutputPublicKey(additionalTxPubKey, outputIndex);
    
    // Check view tag
    if (output.target.viewTag != null) {
      final expectedViewTag = _computeViewTag(additionalTxPubKey, outputIndex);
      if (output.target.viewTag != expectedViewTag) {
        return null;
      }
    }
    
    // Check subaddresses
    final subaddressMatch = _checkSubaddress(derivedPubKey, output.target.key);
    if (subaddressMatch != null) {
      return ScannedOutput(
        output: output,
        amount: null,
        isOwned: true,
        subaddressIndex: subaddressMatch,
      );
    }
    
    return null;
  }

  /// Derive output public key: P = Hs(a*R || i)*G + B
  Uint8List _deriveOutputPublicKey(Uint8List txPubKey, int outputIndex) {
    // Compute shared secret: a * R
    final sharedSecret = Ed25519.scalarMultBytes(viewSecretKey, txPubKey);
    
    // Compute derivation: Hs(a*R || i)
    final derivationData = Uint8List.fromList(sharedSecret + _toVarint(outputIndex));
    final hash = Keccak.hash256(derivationData);
    final scalar = Ed25519.scalarReduce(hash);
    
    // Compute P = Hs(...)*G + B
    final scalarTimesG = Ed25519.scalarMultBaseBytes(scalar);
    return Ed25519.pointAddBytes(scalarTimesG, spendPublicKey);
  }

  /// Compute view tag for fast rejection
  int _computeViewTag(Uint8List txPubKey, int outputIndex) {
    final sharedSecret = Ed25519.scalarMultBytes(viewSecretKey, txPubKey);
    final prefix = Uint8List.fromList([0x76, 0x69, 0x65, 0x77, 0x5f, 0x74, 0x61, 0x67]); // "view_tag"
    final data = Uint8List.fromList(prefix + sharedSecret + _toVarint(outputIndex));
    final hash = Keccak.hash256(data);
    return hash[0];
  }

  /// Check if the derived key matches any known subaddress
  SubaddressIndex? _checkSubaddress(Uint8List derivedPubKey, Uint8List outputPubKey) {
    // Compute: D = P_out - derived = subaddress offset
    final diff = Ed25519.pointSubBytes(outputPubKey, derivedPubKey);
    return _subaddressTable.lookup(diff);
  }

  /// Derive subaddress public key
  Uint8List _deriveSubaddressPublicKey(int major, int minor) {
    if (major == 0 && minor == 0) {
      return spendPublicKey;
    }
    
    final prefix = Uint8List.fromList('SubAddr'.codeUnits);
    final data = Uint8List.fromList(
      prefix + viewSecretKey + _toLEBytes(major) + _toLEBytes(minor)
    );
    final hash = Keccak.hash256(data);
    final scalar = Ed25519.scalarReduce(hash);
    final scalarG = Ed25519.scalarMultBaseBytes(scalar);
    return Ed25519.pointAddBytes(spendPublicKey, scalarG);
  }

  Uint8List _toVarint(int value) {
    final result = <int>[];
    var v = value;
    while (v >= 0x80) {
      result.add((v & 0x7F) | 0x80);
      v >>= 7;
    }
    result.add(v);
    return Uint8List.fromList(result);
  }

  Uint8List _toLEBytes(int value) {
    return Uint8List.fromList([
      value & 0xFF,
      (value >> 8) & 0xFF,
      (value >> 16) & 0xFF,
      (value >> 24) & 0xFF,
    ]);
  }
}

/// Lookup table for subaddress public keys
class SubaddressTable {
  final Map<String, SubaddressIndex> _table = {};

  void add(Uint8List publicKey, SubaddressIndex index) {
    _table[_bytesToHex(publicKey)] = index;
  }

  SubaddressIndex? lookup(Uint8List publicKey) {
    return _table[_bytesToHex(publicKey)];
  }

  void clear() {
    _table.clear();
  }

  int get size => _table.length;
}

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
