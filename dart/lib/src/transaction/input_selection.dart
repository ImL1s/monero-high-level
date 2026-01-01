/// Input Selection Algorithm
///
/// Selects UTXOs for transaction construction.
library;

import 'dart:math';

import '../constants.dart';

/// Spendable output (UTXO) in the wallet.
class SpendableOutput {
  /// Transaction hash containing this output
  final String txHash;

  /// Output index within the transaction
  final int outputIndex;

  /// Global output index on the blockchain
  final int globalIndex;

  /// Amount in atomic units
  final BigInt amount;

  /// One-time public key (stealth address)
  final String publicKey;

  /// Output commitment (optional, hex-encoded)
  ///
  /// For RingCT outputs this is the Pedersen commitment used in ring members.
  final String? commitment;

  /// Block height when confirmed
  final int blockHeight;

  /// Subaddress account index
  final int subaddressMajor;

  /// Subaddress index within account
  final int subaddressMinor;

  /// Key image for this output (derived from spend key)
  final String? keyImage;

  /// Whether this output is unlocked and spendable
  final bool isUnlocked;

  /// Whether this output is frozen
  final bool isFrozen;

  SpendableOutput({
    required this.txHash,
    required this.outputIndex,
    required this.globalIndex,
    required this.amount,
    required this.publicKey,
    this.commitment,
    required this.blockHeight,
    this.subaddressMajor = 0,
    this.subaddressMinor = 0,
    this.keyImage,
    this.isUnlocked = true,
    this.isFrozen = false,
  });

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is SpendableOutput && other.globalIndex == globalIndex;
  }

  @override
  int get hashCode => globalIndex.hashCode;
}

/// Result of input selection algorithm.
class InputSelectionResult {
  /// Selected outputs to spend
  final List<SpendableOutput> selectedOutputs;

  /// Total amount of selected outputs
  final BigInt totalAmount;

  /// Amount to send (excluding fee)
  final BigInt sendAmount;

  /// Estimated fee
  final BigInt fee;

  /// Change amount (totalAmount - sendAmount - fee)
  final BigInt change;

  InputSelectionResult({
    required this.selectedOutputs,
    required this.totalAmount,
    required this.sendAmount,
    required this.fee,
    required this.change,
  });

  /// Whether selection is valid (enough funds)
  bool get isValid =>
      totalAmount >= sendAmount + fee && change >= BigInt.zero;
}

/// Strategy for selecting inputs (UTXOs) for a transaction.
enum SelectionStrategy {
  /// Select smallest outputs first (consolidation)
  smallestFirst,

  /// Select largest outputs first (minimize inputs)
  largestFirst,

  /// Select outputs closest to target amount
  closestMatch,

  /// Random selection for privacy
  random,
}

/// Configuration for input selection.
class SelectionConfig {
  /// Maximum number of inputs per transaction
  final int maxInputs;

  /// Minimum confirmations required
  final int minConfirmations;

  /// Current blockchain height
  final int currentHeight;

  /// Fee per byte in atomic units
  final BigInt feePerByte;

  /// Ring size (number of decoys + 1)
  final int ringSize;

  /// Number of outputs in the transaction
  final int numOutputs;

  SelectionConfig({
    this.maxInputs = 16,
    this.minConfirmations = 10,
    this.currentHeight = 0,
    BigInt? feePerByte,
    this.ringSize = MoneroConstants.ringSize,
    this.numOutputs = 2,
  }) : feePerByte = feePerByte ?? BigInt.from(20000);

  SelectionConfig copyWith({
    int? maxInputs,
    int? minConfirmations,
    int? currentHeight,
    BigInt? feePerByte,
    int? ringSize,
    int? numOutputs,
  }) {
    return SelectionConfig(
      maxInputs: maxInputs ?? this.maxInputs,
      minConfirmations: minConfirmations ?? this.minConfirmations,
      currentHeight: currentHeight ?? this.currentHeight,
      feePerByte: feePerByte ?? this.feePerByte,
      ringSize: ringSize ?? this.ringSize,
      numOutputs: numOutputs ?? this.numOutputs,
    );
  }
}

/// Selects transaction inputs (UTXOs) based on various strategies.
class InputSelector {
  /// Estimated transaction size overhead (bytes)
  static const int txOverheadSize = 100;

  /// Estimated size per input (bytes) - includes ring signature
  static const int inputSize = 2500;

  /// Estimated size per output (bytes)
  static const int outputSize = 150;

  /// Monero unlock time in blocks
  static const int unlockTimeBlocks = 10;

  final SelectionConfig config;
  final Random _random;

  InputSelector({
    required this.config,
    Random? random,
  }) : _random = random ?? Random.secure();

  /// Select inputs to cover the target amount plus fee.
  InputSelectionResult? selectInputs({
    required List<SpendableOutput> availableOutputs,
    required BigInt targetAmount,
    SelectionStrategy strategy = SelectionStrategy.closestMatch,
  }) {
    // Filter unlocked, unfrozen, and confirmed outputs
    final spendable = availableOutputs.where((output) {
      if (!output.isUnlocked || output.isFrozen) return false;
      if (config.currentHeight - output.blockHeight < config.minConfirmations) {
        return false;
      }
      return true;
    }).toList();

    if (spendable.isEmpty) return null;

    // Sort based on strategy
    final sorted = _sortByStrategy(spendable, targetAmount, strategy);

    // Greedy selection
    final selected = <SpendableOutput>[];
    var totalSelected = BigInt.zero;
    var estimatedFee = _estimateFee(0);

    for (final output in sorted) {
      if (selected.length >= config.maxInputs) break;

      selected.add(output);
      totalSelected += output.amount;
      estimatedFee = _estimateFee(selected.length);

      // Check if we have enough
      if (totalSelected >= targetAmount + estimatedFee) {
        final change = totalSelected - targetAmount - estimatedFee;
        return InputSelectionResult(
          selectedOutputs: selected,
          totalAmount: totalSelected,
          sendAmount: targetAmount,
          fee: estimatedFee,
          change: change,
        );
      }
    }

    // Not enough funds
    return null;
  }

  /// Select all outputs (sweep operation).
  InputSelectionResult? selectAll({
    required List<SpendableOutput> availableOutputs,
  }) {
    final spendable = availableOutputs.where((output) {
      if (!output.isUnlocked || output.isFrozen) return false;
      if (config.currentHeight - output.blockHeight < config.minConfirmations) {
        return false;
      }
      return true;
    }).toList();

    if (spendable.isEmpty) return null;

    // Take up to maxInputs outputs, prioritizing larger amounts
    spendable.sort((a, b) => b.amount.compareTo(a.amount));
    final selected = spendable.take(config.maxInputs).toList();

    final totalAmount = selected.fold<BigInt>(
      BigInt.zero,
      (sum, output) => sum + output.amount,
    );

    final fee = _estimateFee(selected.length, numOutputs: 1);
    final sendAmount = totalAmount - fee;

    if (sendAmount <= BigInt.zero) return null;

    return InputSelectionResult(
      selectedOutputs: selected,
      totalAmount: totalAmount,
      sendAmount: sendAmount,
      fee: fee,
      change: BigInt.zero,
    );
  }

  /// Estimate transaction fee based on number of inputs.
  BigInt _estimateFee(int numInputs, {int? numOutputs}) {
    final outputs = numOutputs ?? config.numOutputs;
    final txSize = txOverheadSize + (numInputs * inputSize) + (outputs * outputSize);
    return config.feePerByte * BigInt.from(txSize);
  }

  List<SpendableOutput> _sortByStrategy(
    List<SpendableOutput> outputs,
    BigInt targetAmount,
    SelectionStrategy strategy,
  ) {
    final sorted = List<SpendableOutput>.from(outputs);

    switch (strategy) {
      case SelectionStrategy.smallestFirst:
        sorted.sort((a, b) => a.amount.compareTo(b.amount));
      case SelectionStrategy.largestFirst:
        sorted.sort((a, b) => b.amount.compareTo(a.amount));
      case SelectionStrategy.closestMatch:
        sorted.sort((a, b) {
          final diffA = (a.amount - targetAmount).abs();
          final diffB = (b.amount - targetAmount).abs();
          return diffA.compareTo(diffB);
        });
      case SelectionStrategy.random:
        sorted.shuffle(_random);
    }

    return sorted;
  }
}

/// Utility functions for UTXO management.
class UtxoUtils {
  UtxoUtils._();

  /// Check if an output is unlocked based on unlock time.
  static bool isUnlocked({
    required int blockHeight,
    required int unlockTime,
    required int currentHeight,
    required int currentTime,
  }) {
    // Must have minimum confirmations
    if (currentHeight - blockHeight < InputSelector.unlockTimeBlocks) {
      return false;
    }

    if (unlockTime == 0) return true;
    if (unlockTime < 500000000) {
      return currentHeight >= unlockTime;
    }
    return currentTime >= unlockTime;
  }

  /// Group outputs by subaddress.
  static Map<(int, int), List<SpendableOutput>> groupBySubaddress(
    List<SpendableOutput> outputs,
  ) {
    final grouped = <(int, int), List<SpendableOutput>>{};
    for (final output in outputs) {
      final key = (output.subaddressMajor, output.subaddressMinor);
      grouped.putIfAbsent(key, () => []).add(output);
    }
    return grouped;
  }

  /// Calculate total balance from outputs.
  static BigInt totalBalance(List<SpendableOutput> outputs) {
    return outputs.fold<BigInt>(
      BigInt.zero,
      (sum, output) => sum + output.amount,
    );
  }

  /// Calculate unlocked balance (spendable now).
  static BigInt unlockedBalance(
    List<SpendableOutput> outputs, {
    required int currentHeight,
    int minConfirmations = 10,
  }) {
    return outputs.where((output) {
      if (!output.isUnlocked || output.isFrozen) return false;
      return currentHeight - output.blockHeight >= minConfirmations;
    }).fold<BigInt>(
      BigInt.zero,
      (sum, output) => sum + output.amount,
    );
  }
}
