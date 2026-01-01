/// High-Level Transaction Builder
///
/// Integrates UTXO selection, decoy selection, and RingCT base fields.
library;

import 'dart:typed_data';

import '../constants.dart';
import '../core/address.dart';
import '../crypto/ed25519.dart';
import '../crypto/keccak.dart';
import '../crypto/pedersen.dart';
import 'input_selection.dart';
import 'models.dart';
import 'serializer.dart';

/// Transaction builder destination.
class TxDestination {
  /// Recipient address
  final MoneroAddress address;

  /// Amount in atomic units
  final int amount;

  const TxDestination({
    required this.address,
    required this.amount,
  });
}

/// Built transaction result.
class BuiltTx {
  /// The transaction
  final Transaction tx;

  /// Serialized transaction blob
  final Uint8List txBlob;

  /// Transaction hash (ID)
  final Uint8List txHash;

  /// Transaction fee
  final int fee;

  /// Change amount
  final int change;

  /// Per-input ring info
  final List<InputRing> rings;

  const BuiltTx({
    required this.tx,
    required this.txBlob,
    required this.txHash,
    required this.fee,
    required this.change,
    required this.rings,
  });

  /// Get hex-encoded transaction hash.
  String get txHashHex => txHash.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

  /// Get hex-encoded transaction blob.
  String get txBlobHex => txBlob.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
}

/// Ring info for a single input.
class InputRing {
  /// Global index of the real output
  final int realGlobalIndex;

  /// All ring members (sorted by global index)
  final List<RingMemberInfo> ringMembers;

  /// Index of real output within the ring
  final int realIndex;

  const InputRing({
    required this.realGlobalIndex,
    required this.ringMembers,
    required this.realIndex,
  });
}

/// Ring member info.
class RingMemberInfo {
  final int globalIndex;
  final Uint8List publicKey;
  final Uint8List commitment;

  const RingMemberInfo({
    required this.globalIndex,
    required this.publicKey,
    required this.commitment,
  });
}

/// Output provider interface for getting decoys.
abstract class OutputProvider {
  /// Get current blockchain height.
  Future<int> getCurrentHeight();

  /// Get random outputs for ring construction.
  Future<List<RingMemberInfo>> getRandomOutputs(int count, int amount);

  /// Get output distribution for gamma selection.
  Future<List<int>> getOutputDistribution(int fromHeight, int toHeight);
}

/// High-level transaction builder.
///
/// Integrates:
/// - UTXO selection (`InputSelector`)
/// - Decoy selection
/// - Basic RingCT base fields (type/fee/ecdh/outPk)
/// - Serialization (`TransactionSerializer`)
class TxBuilder {
  final OutputProvider _outputProvider;
  final SelectionConfig _selectionConfig;

  TxBuilder({
    required OutputProvider outputProvider,
    SelectionConfig? selectionConfig,
  }) : _outputProvider = outputProvider,
       _selectionConfig = selectionConfig ?? SelectionConfig();

  /// Build a standard RingCT transaction.
  ///
  /// [availableOutputs] All spendable outputs from wallet.
  /// [destinations] List of recipients with amounts.
  /// [changeAddress] Address for change output.
  /// [rctType] RingCT type (default: CLSAG).
  /// [paymentId] Optional 8-byte encrypted payment ID.
  /// [selectionStrategy] UTXO selection strategy.
  Future<BuiltTx> build({
    required List<SpendableOutput> availableOutputs,
    required List<TxDestination> destinations,
    required MoneroAddress changeAddress,
    RctType rctType = RctType.clsag,
    Uint8List? paymentId,
    SelectionStrategy selectionStrategy = SelectionStrategy.closestMatch,
  }) async {
    if (destinations.isEmpty) {
      throw ArgumentError('At least one destination required');
    }
    for (final dst in destinations) {
      if (dst.amount <= 0) {
        throw ArgumentError('Destination amount must be > 0');
      }
    }

    final sendAmount = destinations.fold<int>(0, (sum, d) => sum + d.amount);

    // Select inputs
    final currentHeight = await _outputProvider.getCurrentHeight();
    final selector = InputSelector(
      config: _selectionConfig.copyWith(
        currentHeight: currentHeight,
        numOutputs: destinations.length + 1,
      ),
    );
    final selection = selector.selectInputs(
      availableOutputs: availableOutputs,
      targetAmount: BigInt.from(sendAmount),
      strategy: selectionStrategy,
    );
    if (selection == null) {
      throw StateError('Insufficient funds');
    }

    final fee = selection.fee.toInt();
    final change = selection.change.toInt();

    // Build rings for each input
    final inputRings = <InputRing>[];
    final inputs = <TxInput>[];

    for (final real in selection.selectedOutputs) {
      if (real.keyImage == null) {
        throw StateError('Missing key image for selected output');
      }

      // Get decoys
      final ring = await _selectDecoys(real, currentHeight);
      final sorted = ring..sort((a, b) => a.globalIndex.compareTo(b.globalIndex));
      final realIndex = sorted.indexWhere((m) => m.globalIndex == real.globalIndex);
      if (realIndex < 0) {
        throw StateError('Real output missing from ring');
      }

      inputRings.add(InputRing(
        realGlobalIndex: real.globalIndex,
        ringMembers: sorted,
        realIndex: realIndex,
      ));

      final absolute = sorted.map((m) => m.globalIndex).toList();
      final relative = _toRelativeOffsets(absolute);

      inputs.add(TxInput(
        amount: 0, // RingCT
        keyOffsets: relative,
        keyImage: _hexToBytes(real.keyImage!),
      ));
    }

    // Generate tx keypair (R, r)
    final txSecretKey = Ed25519.randomScalar();
    final txPubKey = Ed25519.scalarMultBaseBytes(txSecretKey);

    // Build outputs
    final outputs = <TxOutput>[];
    final outputAmounts = <int>[];

    // Destinations
    for (var i = 0; i < destinations.length; i++) {
      final dst = destinations[i];
      final targetKey = _deriveOneTimeOutputKey(
        txSecretKey: txSecretKey,
        recipientViewKey: dst.address.publicViewKey,
        recipientSpendKey: dst.address.publicSpendKey,
        outputIndex: i,
      );
      final viewTag = _computeViewTag(
        txSecretKey: txSecretKey,
        recipientViewKey: dst.address.publicViewKey,
        outputIndex: i,
      );

      outputs.add(TxOutput(
        index: i,
        amount: 0,
        target: TxOutTarget(key: targetKey, viewTag: viewTag),
      ));
      outputAmounts.add(dst.amount);
    }

    // Change output
    if (change > 0) {
      final changeIndex = outputs.length;
      final changeKey = _deriveOneTimeOutputKey(
        txSecretKey: txSecretKey,
        recipientViewKey: changeAddress.publicViewKey,
        recipientSpendKey: changeAddress.publicSpendKey,
        outputIndex: changeIndex,
      );
      final changeViewTag = _computeViewTag(
        txSecretKey: txSecretKey,
        recipientViewKey: changeAddress.publicViewKey,
        outputIndex: changeIndex,
      );

      outputs.add(TxOutput(
        index: changeIndex,
        amount: 0,
        target: TxOutTarget(key: changeKey, viewTag: changeViewTag),
      ));
      outputAmounts.add(change);
    }

    // Build extra
    final extra = TransactionSerializer.buildExtra(
      txPubKey: txPubKey,
      encryptedPaymentId: paymentId,
    );

    // Build RingCT base fields
    final (ecdhInfo, outPk) = _buildRctOutputBase(outputAmounts);
    final rctSig = RctSignature(
      type: rctType,
      txnFee: fee,
      ecdhInfo: ecdhInfo,
      outPk: outPk,
    );

    // Build transaction
    final unsignedTx = Transaction(
      hash: Uint8List(32),
      version: 2,
      unlockTime: 0,
      inputs: inputs,
      outputs: outputs,
      extra: extra,
      rctSignature: rctSig,
    );

    // Serialize and hash
    final blob = TransactionSerializer.serialize(unsignedTx);
    final hash = Keccak.hash256(blob);

    final tx = Transaction(
      hash: hash,
      version: unsignedTx.version,
      unlockTime: unsignedTx.unlockTime,
      inputs: unsignedTx.inputs,
      outputs: unsignedTx.outputs,
      extra: unsignedTx.extra,
      rctSignature: unsignedTx.rctSignature,
    );

    return BuiltTx(
      tx: tx,
      txBlob: blob,
      txHash: hash,
      fee: fee,
      change: change,
      rings: inputRings,
    );
  }

  /// Sweep all outputs to a single destination (sweep_all).
  Future<BuiltTx> sweepAll({
    required List<SpendableOutput> availableOutputs,
    required MoneroAddress destinationAddress,
    RctType rctType = RctType.clsag,
    Uint8List? paymentId,
  }) async {
    if (availableOutputs.isEmpty) {
      throw ArgumentError('No outputs to sweep');
    }

    final currentHeight = await _outputProvider.getCurrentHeight();
    final selector = InputSelector(
      config: _selectionConfig.copyWith(currentHeight: currentHeight, numOutputs: 1),
    );
    final selection = selector.selectAll(availableOutputs: availableOutputs);
    if (selection == null) {
      throw StateError('Cannot sweep: insufficient funds after fee');
    }

    final fee = selection.fee.toInt();
    final inputRings = <InputRing>[];
    final inputs = <TxInput>[];

    for (final real in selection.selectedOutputs) {
      if (real.keyImage == null) {
        throw StateError('Missing key image for output');
      }

      final ring = await _selectDecoys(real, currentHeight);
      final sorted = ring..sort((a, b) => a.globalIndex.compareTo(b.globalIndex));
      final realIndex = sorted.indexWhere((m) => m.globalIndex == real.globalIndex);
      if (realIndex < 0) {
        throw StateError('Real output missing from ring');
      }

      inputRings.add(InputRing(
        realGlobalIndex: real.globalIndex,
        ringMembers: sorted,
        realIndex: realIndex,
      ));

      final relative = _toRelativeOffsets(sorted.map((m) => m.globalIndex).toList());
      inputs.add(TxInput(
        amount: 0,
        keyOffsets: relative,
        keyImage: _hexToBytes(real.keyImage!),
      ));
    }

    final txSecretKey = Ed25519.randomScalar();
    final txPubKey = Ed25519.scalarMultBaseBytes(txSecretKey);

    final targetKey = _deriveOneTimeOutputKey(
      txSecretKey: txSecretKey,
      recipientViewKey: destinationAddress.publicViewKey,
      recipientSpendKey: destinationAddress.publicSpendKey,
      outputIndex: 0,
    );
    final viewTag = _computeViewTag(
      txSecretKey: txSecretKey,
      recipientViewKey: destinationAddress.publicViewKey,
      outputIndex: 0,
    );

    final outputs = [
      TxOutput(
        index: 0,
        amount: 0,
        target: TxOutTarget(key: targetKey, viewTag: viewTag),
      ),
    ];
    final outputAmounts = [selection.sendAmount.toInt()];

    final extra = TransactionSerializer.buildExtra(
      txPubKey: txPubKey,
      encryptedPaymentId: paymentId,
    );

    final (ecdhInfo, outPk) = _buildRctOutputBase(outputAmounts);
    final rctSig = RctSignature(
      type: rctType,
      txnFee: fee,
      ecdhInfo: ecdhInfo,
      outPk: outPk,
    );

    final unsignedTx = Transaction(
      hash: Uint8List(32),
      version: 2,
      unlockTime: 0,
      inputs: inputs,
      outputs: outputs,
      extra: extra,
      rctSignature: rctSig,
    );

    final blob = TransactionSerializer.serialize(unsignedTx);
    final hash = Keccak.hash256(blob);

    final tx = Transaction(
      hash: hash,
      version: unsignedTx.version,
      unlockTime: unsignedTx.unlockTime,
      inputs: unsignedTx.inputs,
      outputs: unsignedTx.outputs,
      extra: unsignedTx.extra,
      rctSignature: unsignedTx.rctSignature,
    );

    return BuiltTx(
      tx: tx,
      txBlob: blob,
      txHash: hash,
      fee: fee,
      change: 0,
      rings: inputRings,
    );
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Private helpers
  // ─────────────────────────────────────────────────────────────────────────

  Future<List<RingMemberInfo>> _selectDecoys(
    SpendableOutput real,
    int currentHeight,
  ) async {
    final ringSize = MoneroConstants.ringSize;
    final decoys = await _outputProvider.getRandomOutputs(
      ringSize - 1,
      real.amount.toInt(),
    );

    // Include real output
    final ring = [
      RingMemberInfo(
        globalIndex: real.globalIndex,
        publicKey: _hexToBytes(real.publicKey),
        commitment: real.commitment == null
            ? Uint8List(32)
            : _hexToBytes(real.commitment!),
      ),
      ...decoys,
    ];

    // Ensure no duplicates
    final seen = <int>{};
    final unique = <RingMemberInfo>[];
    for (final m in ring) {
      if (!seen.contains(m.globalIndex)) {
        seen.add(m.globalIndex);
        unique.add(m);
      }
    }

    // If we don't have enough, this is a problem
    if (unique.length < ringSize) {
      throw StateError('Not enough unique ring members: ${unique.length}/$ringSize');
    }

    return unique.take(ringSize).toList();
  }

  (List<EcdhInfo>, List<Uint8List>) _buildRctOutputBase(List<int> amounts) {
    final ecdh = <EcdhInfo>[];
    final outPk = <Uint8List>[];

    for (final amount in amounts) {
      final (commitment, mask) = PedersenCommitment.commitWithRandomMask(BigInt.from(amount));
      outPk.add(commitment);

      // Compact format: 8-byte encrypted amount only
      final amountBytes = _int64ToLittleEndian(amount);
      ecdh.add(EcdhInfo(mask: mask, amount: amountBytes));
    }

    return (ecdh, outPk);
  }

  Uint8List _deriveOneTimeOutputKey({
    required Uint8List txSecretKey,
    required Uint8List recipientViewKey,
    required Uint8List recipientSpendKey,
    required int outputIndex,
  }) {
    // shared = r * A
    final shared = Ed25519.scalarMultBytes(txSecretKey, recipientViewKey);
    final derivationData = _concatBytes([shared, _intToVarint(outputIndex)]);
    final hash = Keccak.hash256(derivationData);

    // Reduce to scalar
    final padded = Uint8List(64)..setRange(0, 32, hash);
    final scalar = Ed25519.scalarReduce(padded);
    final scalarG = Ed25519.scalarMultBaseBytes(scalar);

    return Ed25519.pointAddBytes(scalarG, recipientSpendKey);
  }

  int _computeViewTag({
    required Uint8List txSecretKey,
    required Uint8List recipientViewKey,
    required int outputIndex,
  }) {
    final shared = Ed25519.scalarMultBytes(txSecretKey, recipientViewKey);
    final prefix = Uint8List.fromList('view_tag'.codeUnits);
    final data = _concatBytes([prefix, shared, _intToVarint(outputIndex)]);
    return Keccak.hash256(data)[0];
  }

  List<int> _toRelativeOffsets(List<int> sortedAbsolute) {
    if (sortedAbsolute.isEmpty) return [];
    final result = <int>[];
    var prev = 0;
    for (var i = 0; i < sortedAbsolute.length; i++) {
      final cur = sortedAbsolute[i];
      result.add(i == 0 ? cur : cur - prev);
      prev = cur;
    }
    return result;
  }

  Uint8List _intToVarint(int value) {
    final result = <int>[];
    var v = value;
    while (v >= 0x80) {
      result.add((v & 0x7F) | 0x80);
      v = v >> 7;
    }
    result.add(v & 0x7F);
    return Uint8List.fromList(result);
  }

  Uint8List _int64ToLittleEndian(int value) {
    final out = Uint8List(8);
    var v = value;
    for (var i = 0; i < 8; i++) {
      out[i] = v & 0xFF;
      v = v >> 8;
    }
    return out;
  }

  Uint8List _concatBytes(List<Uint8List> arrays) {
    final builder = BytesBuilder();
    for (final arr in arrays) {
      builder.add(arr);
    }
    return builder.toBytes();
  }

  Uint8List _hexToBytes(String hex) {
    var sanitized = hex.toLowerCase();
    if (sanitized.startsWith('0x')) sanitized = sanitized.substring(2);
    if (sanitized.length.isOdd) sanitized = '0$sanitized';

    final out = Uint8List(sanitized.length ~/ 2);
    for (var i = 0; i < out.length; i++) {
      final byteStr = sanitized.substring(i * 2, i * 2 + 2);
      out[i] = int.parse(byteStr, radix: 16);
    }
    return out;
  }
}
