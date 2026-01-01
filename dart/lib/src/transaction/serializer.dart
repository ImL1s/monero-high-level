/// Transaction serializer for Monero's binary transaction format.
library;

import 'dart:typed_data';

import '../crypto/keccak.dart';
import 'models.dart';

// Output type tags
const int _txoutToKey = 0x02;
const int _txoutToTaggedKey = 0x03;

// Input type tags
const int _txinGen = 0xFF;
const int _txinToKey = 0x02;

// Extra field tags
const int _txExtraTagPubkey = 0x01;
const int _txExtraTagNonce = 0x02;
const int _txExtraTagAdditionalPubkeys = 0x04;
const int _txExtraNonceEncryptedPaymentId = 0x01;

/// Serializer for Monero transaction binary format.
///
/// Matches the high-level format used by the Kotlin implementation in this repo.
class TransactionSerializer {
  TransactionSerializer._();

  /// Serialize a transaction to binary format.
  static Uint8List serialize(Transaction tx) {
    final writer = BinaryWriter();

    // Version
    writer.writeVarint(tx.version);

    // Unlock time
    writer.writeVarint(tx.unlockTime);

    // Inputs
    writer.writeVarint(tx.inputs.length);
    for (final input in tx.inputs) {
      _serializeInput(writer, input, tx.isCoinbase);
    }

    // Outputs
    writer.writeVarint(tx.outputs.length);
    for (final output in tx.outputs) {
      _serializeOutput(writer, output);
    }

    // Extra
    final extraBytes = serializeExtra(tx.extra);
    writer.writeVarint(extraBytes.length);
    writer.writeBytes(extraBytes);

    // RingCT signature (for version 2+)
    if (tx.version >= 2 && !tx.isCoinbase && tx.rctSignature != null) {
      _serializeRctSignature(writer, tx.rctSignature!, tx.outputs.length);
    }

    return writer.toBytes();
  }

  /// Serialize transaction prefix only (for signing).
  static Uint8List serializePrefix(Transaction tx) {
    final writer = BinaryWriter();

    // Version
    writer.writeVarint(tx.version);

    // Unlock time
    writer.writeVarint(tx.unlockTime);

    // Inputs
    writer.writeVarint(tx.inputs.length);
    for (final input in tx.inputs) {
      _serializeInput(writer, input, tx.isCoinbase);
    }

    // Outputs
    writer.writeVarint(tx.outputs.length);
    for (final output in tx.outputs) {
      _serializeOutput(writer, output);
    }

    // Extra
    final extraBytes = serializeExtra(tx.extra);
    writer.writeVarint(extraBytes.length);
    writer.writeBytes(extraBytes);

    return writer.toBytes();
  }

  /// Calculate transaction prefix hash (for CLSAG signing).
  static Uint8List prefixHash(Transaction tx) {
    return Keccak.hash256(serializePrefix(tx));
  }

  /// Serialize to hex string.
  static String serializeToHex(Transaction tx) {
    return serialize(tx).map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }

  static void _serializeInput(BinaryWriter writer, TxInput input, bool isCoinbase) {
    if (isCoinbase) {
      writer.writeByte(_txinGen);
      // Coinbase height isn't modeled in TxInput here; keep 0 for now.
      writer.writeVarint(0);
      return;
    }

    writer.writeByte(_txinToKey);
    writer.writeVarint(input.amount);
    writer.writeVarint(input.keyOffsets.length);
    for (final offset in input.keyOffsets) {
      writer.writeVarint(offset);
    }
    writer.writeBytes(input.keyImage);
  }

  static void _serializeOutput(BinaryWriter writer, TxOutput output) {
    writer.writeVarint(output.amount);

    final target = output.target;
    if (target.viewTag != null) {
      writer.writeByte(_txoutToTaggedKey);
      writer.writeBytes(target.key);
      writer.writeByte(target.viewTag!);
    } else {
      writer.writeByte(_txoutToKey);
      writer.writeBytes(target.key);
    }
  }

  /// Serialize extra field from structured components.
  static Uint8List serializeExtra(TxExtra extra) {
    // If raw is already provided (eg. parsed tx), prefer it.
    if (extra.raw.isNotEmpty) return extra.raw;

    final writer = BinaryWriter();

    if (extra.txPubKey != null) {
      writer.writeByte(_txExtraTagPubkey);
      writer.writeBytes(extra.txPubKey!);
    }

    if (extra.additionalPubKeys.isNotEmpty) {
      writer.writeByte(_txExtraTagAdditionalPubkeys);
      writer.writeVarint(extra.additionalPubKeys.length);
      for (final pk in extra.additionalPubKeys) {
        writer.writeBytes(pk);
      }
    }

    if (extra.paymentId != null && extra.paymentId!.length == 8) {
      writer.writeByte(_txExtraTagNonce);
      writer.writeVarint(9); // 1 byte tag + 8 bytes payment ID
      writer.writeByte(_txExtraNonceEncryptedPaymentId);
      writer.writeBytes(extra.paymentId!);
    }

    return writer.toBytes();
  }

  static void _serializeRctSignature(
    BinaryWriter writer,
    RctSignature rct,
    int outputCount,
  ) {
    writer.writeByte(rct.type.value);

    if (rct.type == RctType.none) {
      return;
    }

    writer.writeVarint(rct.txnFee);

    // ECDH info
    for (var i = 0; i < outputCount; i++) {
      final ecdh = i < rct.ecdhInfo.length ? rct.ecdhInfo[i] : null;
      switch (rct.type) {
        case RctType.bulletproof2:
        case RctType.clsag:
        case RctType.bulletproofPlus:
          // Compact format: 8-byte encrypted amount only (placeholder)
          final amount = ecdh?.amount ?? Uint8List(8);
          writer.writeBytes(amount.sublist(0, amount.length < 8 ? amount.length : 8));
          break;
        default:
          // Full format: 32-byte mask + 32-byte amount
          final mask = ecdh?.mask ?? Uint8List(32);
          final amount = ecdh?.amount ?? Uint8List(32);
          writer.writeBytes(mask);
          writer.writeBytes(amount);
      }
    }

    // Output commitments
    for (var i = 0; i < outputCount; i++) {
      final commitment = i < rct.outPk.length ? rct.outPk[i] : Uint8List(32);
      writer.writeBytes(commitment);
    }
  }

  /// Build `TxExtra` from components.
  static TxExtra buildExtra({
    required Uint8List txPubKey,
    List<Uint8List> additionalPubKeys = const [],
    Uint8List? encryptedPaymentId,
  }) {
    final raw = () {
      final writer = BinaryWriter();

      writer.writeByte(_txExtraTagPubkey);
      writer.writeBytes(txPubKey);

      if (additionalPubKeys.isNotEmpty) {
        writer.writeByte(_txExtraTagAdditionalPubkeys);
        writer.writeVarint(additionalPubKeys.length);
        for (final pk in additionalPubKeys) {
          writer.writeBytes(pk);
        }
      }

      if (encryptedPaymentId != null && encryptedPaymentId.length == 8) {
        writer.writeByte(_txExtraTagNonce);
        writer.writeVarint(9);
        writer.writeByte(_txExtraNonceEncryptedPaymentId);
        writer.writeBytes(encryptedPaymentId);
      }

      return writer.toBytes();
    }();

    return TxExtra(
      txPubKey: txPubKey,
      additionalPubKeys: additionalPubKeys,
      paymentId: encryptedPaymentId,
      raw: raw,
    );
  }
}

/// Binary writer for transaction serialization.
class BinaryWriter {
  final BytesBuilder _buffer = BytesBuilder();

  /// Write a single byte.
  void writeByte(int b) {
    _buffer.addByte(b & 0xFF);
  }

  /// Write byte array.
  void writeBytes(Uint8List bytes) {
    _buffer.add(bytes);
  }

  /// Write variable-length integer (varint).
  /// Uses 7 bits per byte, with MSB as continuation flag.
  void writeVarint(int value) {
    var v = value;
    while (v >= 0x80) {
      _buffer.addByte(((v & 0x7F) | 0x80));
      v = v >> 7;
    }
    _buffer.addByte(v & 0x7F);
  }

  /// Get the serialized bytes.
  Uint8List toBytes() => _buffer.toBytes();

  /// Get the current size.
  int get size => _buffer.length;
}
