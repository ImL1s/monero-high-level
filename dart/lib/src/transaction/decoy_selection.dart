/// Decoy Selection Algorithm
///
/// Implements gamma distribution-based decoy selection for ring signatures.
library;

import 'dart:math';
import 'dart:typed_data';

import '../constants.dart';

/// Decoy selection for ring signatures.
///
/// Uses gamma distribution matching Monero reference wallet:
/// - Recent outputs are more likely to be real spends
/// - Approximately 25% of outputs are recent (< 5 days)
class DecoySelector {
  // Gamma distribution parameters from Monero reference
  static const double _gammaShape = 19.28;
  static const double _gammaScale = 1 / 1.61;

  final Random _random;
  final int _currentHeight;
  final List<OutputForSelection> _availableOutputs;

  DecoySelector({
    required int currentHeight,
    required List<OutputForSelection> availableOutputs,
    Random? random,
  })  : _currentHeight = currentHeight,
        _availableOutputs = availableOutputs,
        _random = random ?? Random.secure();

  /// Select decoys for a ring signature
  ///
  /// Returns [ringSize - 1] decoys (excluding the real output)
  List<int> selectDecoys({
    required int realOutputIndex,
    int ringSize = MoneroConstants.ringSize,
  }) {
    final decoys = <int>{};
    final neededCount = ringSize - 1;

    // Exclude the real output and any locked outputs
    final excludeIndices = {realOutputIndex};

    var attempts = 0;
    const maxAttempts = 1000;

    while (decoys.length < neededCount && attempts < maxAttempts) {
      attempts++;
      final index = _sampleGamma();

      if (index >= 0 &&
          index < _availableOutputs.length &&
          !excludeIndices.contains(index) &&
          !decoys.contains(index)) {
        final output = _availableOutputs[index];

        // Check unlock time
        if (_isUnlocked(output)) {
          decoys.add(index);
        }
      }
    }

    if (decoys.length < neededCount) {
      throw DecoySelectionException(
        'Could not find enough decoys: ${decoys.length}/$neededCount after $maxAttempts attempts',
      );
    }

    return decoys.toList()..sort();
  }

  /// Sample from gamma distribution
  int _sampleGamma() {
    // Marsaglia and Tsang's method for gamma distribution
    final d = _gammaShape - 1 / 3;
    final c = 1 / sqrt(9 * d);

    while (true) {
      double x, v;
      do {
        x = _randomNormal();
        v = 1 + c * x;
      } while (v <= 0);

      v = v * v * v;
      final u = _random.nextDouble();

      if (u < 1 - 0.0331 * (x * x) * (x * x)) {
        final sample = d * v * _gammaScale;
        return _outputIndexFromAge(sample);
      }

      if (log(u) < 0.5 * x * x + d * (1 - v + log(v))) {
        final sample = d * v * _gammaScale;
        return _outputIndexFromAge(sample);
      }
    }
  }

  /// Convert age (in days) to output index
  int _outputIndexFromAge(double ageDays) {
    // Convert days to blocks (approximately 2 min per block)
    final ageBlocks = (ageDays * 720).round();
    final targetHeight = _currentHeight - ageBlocks;

    // Binary search for output at target height
    var low = 0;
    var high = _availableOutputs.length - 1;

    while (low < high) {
      final mid = (low + high) ~/ 2;
      if (_availableOutputs[mid].height < targetHeight) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }

    // Add some randomness around the found index
    final jitter = (_random.nextDouble() - 0.5) * 10;
    return (low + jitter.round()).clamp(0, _availableOutputs.length - 1);
  }

  /// Generate standard normal random number (Box-Muller)
  double _randomNormal() {
    final u1 = _random.nextDouble();
    final u2 = _random.nextDouble();
    return sqrt(-2 * log(u1)) * cos(2 * pi * u2);
  }

  /// Check if output is unlocked
  bool _isUnlocked(OutputForSelection output) {
    if (output.unlockTime == 0) return true;

    if (output.unlockTime < 500000000) {
      // Block height-based unlock
      return _currentHeight >= output.unlockTime + MoneroConstants.standardUnlockTime;
    } else {
      // Timestamp-based unlock (deprecated)
      final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
      return now >= output.unlockTime;
    }
  }
}

/// Output data for decoy selection
class OutputForSelection {
  final int globalIndex;
  final int height;
  final int unlockTime;
  final Uint8List publicKey;
  final Uint8List commitment;

  const OutputForSelection({
    required this.globalIndex,
    required this.height,
    required this.unlockTime,
    required this.publicKey,
    required this.commitment,
  });
}

/// Decoy selection exception
class DecoySelectionException implements Exception {
  final String message;
  const DecoySelectionException(this.message);

  @override
  String toString() => 'DecoySelectionException: $message';
}
