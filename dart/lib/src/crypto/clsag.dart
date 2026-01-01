/// CLSAG Ring Signature Implementation
///
/// Compact Linkable Spontaneous Anonymous Group signatures
/// for Monero transaction privacy.
library;

import 'dart:typed_data';

import 'ed25519.dart';
import 'keccak.dart';

/// CLSAG signature structure
class CLSAGSignature {
  /// Initial challenge (32 bytes)
  final Uint8List c1;

  /// Response scalars (n x 32 bytes)
  final List<Uint8List> s;

  /// Key image I (32 bytes)
  final Uint8List keyImage;

  /// Auxiliary commitment key image (32 bytes)
  final Uint8List d;

  CLSAGSignature({
    required this.c1,
    required this.s,
    required this.keyImage,
    required this.d,
  });

  /// Serialize signature for transaction
  Uint8List serialize() {
    final buffer = BytesBuilder();
    buffer.add(c1);
    for (final si in s) {
      buffer.add(si);
    }
    buffer.add(d);
    // Note: keyImage is typically serialized separately in inputs
    return buffer.toBytes();
  }

  /// Calculate signature size in bytes
  int get size => 32 + (s.length * 32) + 32;
}

/// Ring member data for CLSAG signing
class RingMember {
  /// P_i: stealth address public key
  final Uint8List publicKey;

  /// C_i: Pedersen commitment
  final Uint8List commitment;

  RingMember({
    required this.publicKey,
    required this.commitment,
  });
}

/// CLSAG signature generation and verification.
///
/// CLSAG provides:
/// - Anonymity: signer is hidden among ring members
/// - Linkability: double-spending is detectable via key images
/// - Compactness: O(1) signature size vs O(n) for older schemes
class CLSAG {
  CLSAG._();

  /// Domain separator for mu_P
  static const String _domainP = 'CLSAG_agg_0';

  /// Domain separator for mu_C
  static const String _domainC = 'CLSAG_agg_1';

  /// Domain separator for round challenge
  static const String _domainRound = 'CLSAG_round';

  /// Generate a CLSAG signature
  ///
  /// [message] The message/transaction prefix hash to sign (32 bytes)
  /// [ring] List of ring members (public keys + commitments)
  /// [realIndex] Index of the real input in the ring (secret)
  /// [privateKey] Private key of the real input (32 bytes)
  /// [privateCommitmentKey] Private key for commitment (z = mask difference)
  static CLSAGSignature sign({
    required Uint8List message,
    required List<RingMember> ring,
    required int realIndex,
    required Uint8List privateKey,
    required Uint8List privateCommitmentKey,
  }) {
    assert(message.length == 32, 'Message must be 32 bytes');
    assert(ring.isNotEmpty, 'Ring must not be empty');
    assert(realIndex >= 0 && realIndex < ring.length, 'Real index out of bounds');
    assert(privateKey.length == 32, 'Private key must be 32 bytes');
    assert(privateCommitmentKey.length == 32, 'Private commitment key must be 32 bytes');

    final n = ring.length;
    final realMember = ring[realIndex];

    // Compute key image: I = x * Hp(P)
    final hp = _hashToPoint(realMember.publicKey);
    final keyImage = Ed25519.scalarMultBytes(privateKey, hp);

    // Compute auxiliary key image: D = z * Hp(P)
    final d = Ed25519.scalarMultBytes(privateCommitmentKey, hp);

    // Compute aggregation coefficients
    final muP = _computeMuP(message, ring, keyImage, d);
    final muC = _computeMuC(message, ring, keyImage, d);

    // Compute W_i = mu_P * P_i + mu_C * C_i for each ring member
    final w = <Uint8List>[];
    for (final member in ring) {
      final term1 = Ed25519.scalarMultBytes(muP, member.publicKey);
      final term2 = Ed25519.scalarMultBytes(muC, member.commitment);
      w.add(Ed25519.pointAddBytes(term1, term2));
    }

    // Generate random scalar alpha for real input
    final alpha = Ed25519.randomScalar();

    // Generate random scalars s_i for fake inputs
    final s = List<Uint8List>.generate(n, (_) => Uint8List(32));
    for (var i = 0; i < n; i++) {
      if (i != realIndex) {
        s[i] = Ed25519.randomScalar();
      }
    }

    // Compute L_π = alpha * G and R_π = alpha * Hp(P_π)
    final lReal = Ed25519.scalarMultBaseBytes(alpha);
    final rReal = Ed25519.scalarMultBytes(alpha, hp);

    // Initialize challenges array
    final c = List<Uint8List>.filled(n, Uint8List(32));

    // Compute c_{π+1}
    c[(realIndex + 1) % n] = _computeChallenge(
      message: message,
      w: w,
      keyImage: keyImage,
      d: d,
      l: lReal,
      r: rReal,
    );

    // Forward pass: compute challenges and L, R values
    for (var i = 1; i < n; i++) {
      final idx = (realIndex + i) % n;
      final nextIdx = (idx + 1) % n;

      // L_i = s_i * G + c_i * W_i
      final l = Ed25519.pointAddBytes(
        Ed25519.scalarMultBaseBytes(s[idx]),
        Ed25519.scalarMultBytes(c[idx], w[idx]),
      );

      // R_i = s_i * Hp(P_i) + c_i * (mu_P * I + mu_C * D)
      final hpI = _hashToPoint(ring[idx].publicKey);
      final muPi = Ed25519.scalarMultBytes(muP, keyImage);
      final muCd = Ed25519.scalarMultBytes(muC, d);
      final r = Ed25519.pointAddBytes(
        Ed25519.scalarMultBytes(s[idx], hpI),
        Ed25519.scalarMultBytes(c[idx], Ed25519.pointAddBytes(muPi, muCd)),
      );

      c[nextIdx] = _computeChallenge(
        message: message,
        w: w,
        keyImage: keyImage,
        d: d,
        l: l,
        r: r,
      );
    }

    // Compute s_π = alpha - c_π * (mu_P * x + mu_C * z)
    final muPx = Ed25519.scalarMul(muP, privateKey);
    final muCz = Ed25519.scalarMul(muC, privateCommitmentKey);
    final xz = Ed25519.scalarAdd(muPx, muCz);
    final cXz = Ed25519.scalarMul(c[realIndex], xz);
    s[realIndex] = Ed25519.scalarSub(alpha, cXz);

    return CLSAGSignature(
      c1: c[0],
      s: s,
      keyImage: keyImage,
      d: d,
    );
  }

  /// Verify a CLSAG signature
  static bool verify({
    required Uint8List message,
    required List<RingMember> ring,
    required CLSAGSignature signature,
  }) {
    if (ring.isEmpty) return false;
    if (signature.s.length != ring.length) return false;

    final n = ring.length;

    // Compute aggregation coefficients
    final muP = _computeMuP(message, ring, signature.keyImage, signature.d);
    final muC = _computeMuC(message, ring, signature.keyImage, signature.d);

    // Compute W_i = mu_P * P_i + mu_C * C_i
    final w = <Uint8List>[];
    for (final member in ring) {
      final term1 = Ed25519.scalarMultBytes(muP, member.publicKey);
      final term2 = Ed25519.scalarMultBytes(muC, member.commitment);
      w.add(Ed25519.pointAddBytes(term1, term2));
    }

    // Verify by recomputing challenges
    var c = signature.c1;

    for (var i = 0; i < n; i++) {
      // L_i = s_i * G + c_i * W_i
      final l = Ed25519.pointAddBytes(
        Ed25519.scalarMultBaseBytes(signature.s[i]),
        Ed25519.scalarMultBytes(c, w[i]),
      );

      // R_i = s_i * Hp(P_i) + c_i * (mu_P * I + mu_C * D)
      final hp = _hashToPoint(ring[i].publicKey);
      final muPi = Ed25519.scalarMultBytes(muP, signature.keyImage);
      final muCd = Ed25519.scalarMultBytes(muC, signature.d);
      final r = Ed25519.pointAddBytes(
        Ed25519.scalarMultBytes(signature.s[i], hp),
        Ed25519.scalarMultBytes(c, Ed25519.pointAddBytes(muPi, muCd)),
      );

      c = _computeChallenge(
        message: message,
        w: w,
        keyImage: signature.keyImage,
        d: signature.d,
        l: l,
        r: r,
      );
    }

    // Verify c_0 matches
    return _constantTimeEquals(c, signature.c1);
  }

  static Uint8List _hashToPoint(Uint8List data) {
    final hp = Ed25519.hashToPoint(data);
    return Ed25519.pointToBytes(hp);
  }

  static Uint8List _computeMuP(
    Uint8List message,
    List<RingMember> ring,
    Uint8List keyImage,
    Uint8List d,
  ) {
    final buffer = BytesBuilder();
    buffer.add(_stringToBytes(_domainP));
    buffer.add(message);
    for (final member in ring) {
      buffer.add(member.publicKey);
      buffer.add(member.commitment);
    }
    buffer.add(keyImage);
    buffer.add(d);
    return Ed25519.scalarReduce(Keccak.hash256(buffer.toBytes()));
  }

  static Uint8List _computeMuC(
    Uint8List message,
    List<RingMember> ring,
    Uint8List keyImage,
    Uint8List d,
  ) {
    final buffer = BytesBuilder();
    buffer.add(_stringToBytes(_domainC));
    buffer.add(message);
    for (final member in ring) {
      buffer.add(member.publicKey);
      buffer.add(member.commitment);
    }
    buffer.add(keyImage);
    buffer.add(d);
    return Ed25519.scalarReduce(Keccak.hash256(buffer.toBytes()));
  }

  static Uint8List _computeChallenge({
    required Uint8List message,
    required List<Uint8List> w,
    required Uint8List keyImage,
    required Uint8List d,
    required Uint8List l,
    required Uint8List r,
  }) {
    final buffer = BytesBuilder();
    buffer.add(_stringToBytes(_domainRound));
    buffer.add(message);
    for (final wi in w) {
      buffer.add(wi);
    }
    buffer.add(keyImage);
    buffer.add(d);
    buffer.add(l);
    buffer.add(r);
    return Ed25519.scalarReduce(Keccak.hash256(buffer.toBytes()));
  }

  static Uint8List _stringToBytes(String s) {
    return Uint8List.fromList(s.codeUnits);
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
