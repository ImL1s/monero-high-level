/// Pedersen Commitment Implementation
///
/// Implements Pedersen commitments for Monero RingCT.
library;

import 'dart:typed_data';

import 'ed25519.dart';

/// Pedersen Commitment: C = aG + bH
///
/// Where:
/// - G is the Ed25519 base point
/// - H is a secondary generator point
/// - a is the blinding factor (mask)
/// - b is the value being committed to
class PedersenCommitment {
  PedersenCommitment._();

  /// H generator point for commitments.
  /// H = 8 * hash_to_point(G)
  static Uint8List get hPoint {
    // Standard Monero H point (compressed Edwards y-coordinate)
    return _hexToBytes(
      '8b655970153799af2aeadc9ff1add0ea6c7251d54154cfa92c173a0dd39c1f94',
    );
  }

  /// Create a Pedersen commitment: C = mask*G + amount*H
  ///
  /// [mask] Blinding factor (32 bytes scalar)
  /// [amount] Amount in atomic units
  /// Returns Commitment point (32 bytes compressed)
  static Uint8List commit(Uint8List mask, BigInt amount) {
    assert(mask.length == 32, 'Mask must be 32 bytes');

    // Convert amount to scalar
    final amountScalar = _amountToScalar(amount);

    // C = mask*G + amount*H
    final maskG = Ed25519.scalarMultBaseBytes(mask);
    final amountH = Ed25519.scalarMultBytes(amountScalar, hPoint);

    return Ed25519.pointAddBytes(maskG, amountH);
  }

  /// Create a commitment with a random mask.
  ///
  /// [amount] Amount in atomic units
  /// Returns Pair of (commitment, mask)
  static (Uint8List commitment, Uint8List mask) commitWithRandomMask(BigInt amount) {
    final mask = Ed25519.randomScalar();
    final commitment = commit(mask, amount);
    return (commitment, mask);
  }

  /// Create a zero commitment (used for transparent amounts).
  /// C = 0*G + amount*H = amount*H
  static Uint8List commitZeroMask(BigInt amount) {
    final amountScalar = _amountToScalar(amount);
    return Ed25519.scalarMultBytes(amountScalar, hPoint);
  }

  /// Add two commitments: C1 + C2
  static Uint8List add(Uint8List c1, Uint8List c2) {
    return Ed25519.pointAddBytes(c1, c2);
  }

  /// Subtract two commitments: C1 - C2
  static Uint8List subtract(Uint8List c1, Uint8List c2) {
    return Ed25519.pointSubBytes(c1, c2);
  }

  /// Sum multiple commitments.
  static Uint8List sum(List<Uint8List> commitments) {
    assert(commitments.isNotEmpty, 'Need at least one commitment');

    var result = commitments[0];
    for (var i = 1; i < commitments.length; i++) {
      result = add(result, commitments[i]);
    }
    return result;
  }

  /// Verify that commitments balance: sum(inputs) = sum(outputs) + fee*H
  static bool verifyBalance({
    required List<Uint8List> inputCommitments,
    required List<Uint8List> outputCommitments,
    required BigInt fee,
  }) {
    if (inputCommitments.isEmpty || outputCommitments.isEmpty) return false;

    final inputSum = sum(inputCommitments);
    final outputSum = sum(outputCommitments);
    final feeCommitment = commitZeroMask(fee);

    // Input sum should equal output sum + fee
    final expectedSum = add(outputSum, feeCommitment);

    return _constantTimeEquals(inputSum, expectedSum);
  }

  /// Compute pseudo output commitment for an input.
  ///
  /// In RingCT, each input provides a pseudo output that commits to
  /// the same amount as the real output being spent.
  static Uint8List computePseudoOut({
    required Uint8List inputMask,
    required BigInt amount,
  }) {
    return commit(inputMask, amount);
  }

  /// Generate mask for output that balances with inputs.
  ///
  /// For a transaction to balance:
  /// sum(input_masks) = sum(output_masks)
  /// 
  /// The last output mask is computed from this constraint.
  static Uint8List generateBalancingMask({
    required List<Uint8List> inputMasks,
    required List<Uint8List> previousOutputMasks,
  }) {
    // sum_inputs - sum_previous_outputs
    var result = Uint8List(32);

    for (final mask in inputMasks) {
      result = Ed25519.scalarAdd(result, mask);
    }

    for (final mask in previousOutputMasks) {
      result = Ed25519.scalarSub(result, mask);
    }

    return result;
  }

  static Uint8List _amountToScalar(BigInt amount) {
    // Convert to 32-byte little-endian scalar
    final bytes = Uint8List(32);
    var remaining = amount;
    for (var i = 0; i < 8 && remaining > BigInt.zero; i++) {
      bytes[i] = (remaining & BigInt.from(0xFF)).toInt();
      remaining = remaining >> 8;
    }
    return bytes;
  }

  static Uint8List _hexToBytes(String hex) {
    final length = hex.length ~/ 2;
    final bytes = Uint8List(length);
    for (var i = 0; i < length; i++) {
      bytes[i] = int.parse(hex.substring(i * 2, i * 2 + 2), radix: 16);
    }
    return bytes;
  }

  static bool _constantTimeEquals(Uint8List a, Uint8List b) {
    if (a.length != b.length) return false;
    var result = 0;
    for (var i = 0; i < a.length; i++) {
      result |= a[i] ^ b[i];
    }
    return result == 0;
  }
}
