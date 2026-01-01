/// Offline Transaction Signing
///
/// Supports cold-signing workflow for view-only and offline wallets.
library;

import 'dart:convert';
import 'dart:typed_data';

/// Exportable unsigned transaction data.
class UnsignedTxExport {
  /// Version of this export format
  final int version;

  /// Serialized transaction prefix (hex)
  final String txPrefixHex;

  /// Prefix hash used for signing (hex, 32 bytes)
  final String prefixHashHex;

  /// Per-input ring data
  final List<UnsignedInputData> inputs;

  /// Per-output data (amounts, masks)
  final List<UnsignedOutputData> outputs;

  /// RCT type value
  final int rctType;

  /// Fee in atomic units
  final BigInt fee;

  /// Change amount (informational)
  final BigInt change;

  UnsignedTxExport({
    this.version = 1,
    required this.txPrefixHex,
    required this.prefixHashHex,
    required this.inputs,
    required this.outputs,
    required this.rctType,
    required this.fee,
    required this.change,
  });

  Map<String, dynamic> toJson() => {
        'version': version,
        'txPrefixHex': txPrefixHex,
        'prefixHashHex': prefixHashHex,
        'inputs': inputs.map((i) => i.toJson()).toList(),
        'outputs': outputs.map((o) => o.toJson()).toList(),
        'rctType': rctType,
        'fee': fee.toString(),
        'change': change.toString(),
      };

  factory UnsignedTxExport.fromJson(Map<String, dynamic> json) {
    return UnsignedTxExport(
      version: json['version'] as int,
      txPrefixHex: json['txPrefixHex'] as String,
      prefixHashHex: json['prefixHashHex'] as String,
      inputs: (json['inputs'] as List)
          .map((i) => UnsignedInputData.fromJson(i as Map<String, dynamic>))
          .toList(),
      outputs: (json['outputs'] as List)
          .map((o) => UnsignedOutputData.fromJson(o as Map<String, dynamic>))
          .toList(),
      rctType: json['rctType'] as int,
      fee: BigInt.parse(json['fee'] as String),
      change: BigInt.parse(json['change'] as String),
    );
  }

  String encode() => jsonEncode(toJson());

  static UnsignedTxExport decode(String data) =>
      UnsignedTxExport.fromJson(jsonDecode(data) as Map<String, dynamic>);
}

/// Per-input data for unsigned transaction.
class UnsignedInputData {
  /// Global index of the real output being spent
  final int realGlobalIndex;

  /// Index of real output within the ring (0-based)
  final int realIndexInRing;

  /// Ring member public keys (hex, 32 bytes each)
  final List<String> ringPubKeysHex;

  /// Ring member commitments (hex, 32 bytes each)
  final List<String> ringCommitmentsHex;

  /// Key image for this input (hex, 32 bytes)
  final String keyImageHex;

  UnsignedInputData({
    required this.realGlobalIndex,
    required this.realIndexInRing,
    required this.ringPubKeysHex,
    required this.ringCommitmentsHex,
    required this.keyImageHex,
  });

  Map<String, dynamic> toJson() => {
        'realGlobalIndex': realGlobalIndex,
        'realIndexInRing': realIndexInRing,
        'ringPubKeysHex': ringPubKeysHex,
        'ringCommitmentsHex': ringCommitmentsHex,
        'keyImageHex': keyImageHex,
      };

  factory UnsignedInputData.fromJson(Map<String, dynamic> json) {
    return UnsignedInputData(
      realGlobalIndex: json['realGlobalIndex'] as int,
      realIndexInRing: json['realIndexInRing'] as int,
      ringPubKeysHex: (json['ringPubKeysHex'] as List).cast<String>(),
      ringCommitmentsHex: (json['ringCommitmentsHex'] as List).cast<String>(),
      keyImageHex: json['keyImageHex'] as String,
    );
  }
}

/// Per-output data for unsigned transaction.
class UnsignedOutputData {
  /// Output index
  final int index;

  /// Amount in atomic units
  final BigInt amount;

  /// Blinding mask (hex, 32 bytes)
  final String maskHex;

  /// Commitment (hex, 32 bytes)
  final String commitmentHex;

  UnsignedOutputData({
    required this.index,
    required this.amount,
    required this.maskHex,
    required this.commitmentHex,
  });

  Map<String, dynamic> toJson() => {
        'index': index,
        'amount': amount.toString(),
        'maskHex': maskHex,
        'commitmentHex': commitmentHex,
      };

  factory UnsignedOutputData.fromJson(Map<String, dynamic> json) {
    return UnsignedOutputData(
      index: json['index'] as int,
      amount: BigInt.parse(json['amount'] as String),
      maskHex: json['maskHex'] as String,
      commitmentHex: json['commitmentHex'] as String,
    );
  }
}

/// Signed transaction export.
class SignedTxExport {
  /// Version of this export format
  final int version;

  /// Fully serialized signed transaction (hex)
  final String txBlobHex;

  /// Transaction hash (hex, 32 bytes)
  final String txHashHex;

  /// Per-input key images (for spend tracking)
  final List<String> keyImagesHex;

  /// Fee in atomic units
  final BigInt fee;

  SignedTxExport({
    this.version = 1,
    required this.txBlobHex,
    required this.txHashHex,
    required this.keyImagesHex,
    required this.fee,
  });

  Map<String, dynamic> toJson() => {
        'version': version,
        'txBlobHex': txBlobHex,
        'txHashHex': txHashHex,
        'keyImagesHex': keyImagesHex,
        'fee': fee.toString(),
      };

  factory SignedTxExport.fromJson(Map<String, dynamic> json) {
    return SignedTxExport(
      version: json['version'] as int,
      txBlobHex: json['txBlobHex'] as String,
      txHashHex: json['txHashHex'] as String,
      keyImagesHex: (json['keyImagesHex'] as List).cast<String>(),
      fee: BigInt.parse(json['fee'] as String),
    );
  }

  String encode() => jsonEncode(toJson());

  static SignedTxExport decode(String data) =>
      SignedTxExport.fromJson(jsonDecode(data) as Map<String, dynamic>);
}

/// Offline signing workflow manager.
class OfflineSigning {
  OfflineSigning._();

  /// Export unsigned transaction for offline signing.
  static String exportUnsigned(UnsignedTxExport tx) {
    return tx.encode();
  }

  /// Import unsigned transaction.
  static UnsignedTxExport importUnsigned(String data) {
    return UnsignedTxExport.decode(data);
  }

  /// Export signed transaction for relay.
  static String exportSigned(SignedTxExport tx) {
    return tx.encode();
  }

  /// Import signed transaction.
  static SignedTxExport importSigned(String data) {
    return SignedTxExport.decode(data);
  }

  /// Convert transaction blob to Uint8List for relay.
  static Uint8List signedTxToBlob(SignedTxExport tx) {
    return _hexToBytes(tx.txBlobHex);
  }

  static Uint8List _hexToBytes(String hex) {
    final length = hex.length ~/ 2;
    final bytes = Uint8List(length);
    for (var i = 0; i < length; i++) {
      bytes[i] = int.parse(hex.substring(i * 2, i * 2 + 2), radix: 16);
    }
    return bytes;
  }
}
