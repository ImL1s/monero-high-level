import 'dart:typed_data';

import '../crypto/ed25519.dart';
import '../crypto/keccak.dart';

/// Monero key management.
///
/// Monero wallet keys:
/// - Private spend key: Random 256-bit scalar
/// - Private view key: Keccak256(private_spend_key) mod l
/// - Public spend key: private_spend_key * G
/// - Public view key: private_view_key * G
class MoneroKeys {
  /// Private spend key (32 bytes)
  final Uint8List privateSpendKey;

  /// Private view key (32 bytes)
  final Uint8List privateViewKey;

  /// Public spend key (32 bytes)
  final Uint8List publicSpendKey;

  /// Public view key (32 bytes)
  final Uint8List publicViewKey;

  const MoneroKeys._({
    required this.privateSpendKey,
    required this.privateViewKey,
    required this.publicSpendKey,
    required this.publicViewKey,
  });

  /// Create keys from private spend key.
  ///
  /// [privateSpendKey] 32-byte private spend key (from seed)
  factory MoneroKeys.fromSpendKey(Uint8List privateSpendKey) {
    if (privateSpendKey.length != 32) {
      throw ArgumentError('Private spend key must be 32 bytes');
    }

    // Derive private view key: Keccak256(private_spend_key) mod l
    final hash = Keccak.hash256(privateSpendKey);
    final privateViewKey = Ed25519.scalarReduce(
      Uint8List(64)..setAll(0, hash),
    );

    // Derive public keys
    final spendScalar = _bytesToBigInt(privateSpendKey);
    final viewScalar = _bytesToBigInt(privateViewKey);

    final publicSpendPoint = Ed25519.scalarMultBase(spendScalar);
    final publicViewPoint = Ed25519.scalarMultBase(viewScalar);

    return MoneroKeys._(
      privateSpendKey: privateSpendKey,
      privateViewKey: privateViewKey,
      publicSpendKey: Ed25519.pointToBytes(publicSpendPoint),
      publicViewKey: Ed25519.pointToBytes(publicViewPoint),
    );
  }

  /// Create view-only keys (no private spend key).
  factory MoneroKeys.viewOnly({
    required Uint8List privateViewKey,
    required Uint8List publicSpendKey,
  }) {
    if (privateViewKey.length != 32) {
      throw ArgumentError('Private view key must be 32 bytes');
    }
    if (publicSpendKey.length != 32) {
      throw ArgumentError('Public spend key must be 32 bytes');
    }

    final viewScalar = _bytesToBigInt(privateViewKey);
    final publicViewPoint = Ed25519.scalarMultBase(viewScalar);

    return MoneroKeys._(
      privateSpendKey: Uint8List(32), // Empty for view-only
      privateViewKey: privateViewKey,
      publicSpendKey: publicSpendKey,
      publicViewKey: Ed25519.pointToBytes(publicViewPoint),
    );
  }

  /// Check if this is a view-only wallet.
  bool get isViewOnly => privateSpendKey.every((b) => b == 0);

  /// Derive a subaddress key pair.
  ///
  /// [accountIndex] Account index (major)
  /// [addressIndex] Address index within account (minor)
  /// Returns (public spend key, public view key) for subaddress
  (Uint8List, Uint8List) deriveSubaddressKeys(
    int accountIndex,
    int addressIndex,
  ) {
    if (accountIndex == 0 && addressIndex == 0) {
      // Primary address
      return (publicSpendKey, publicViewKey);
    }

    // m = Hs("SubAddr" || a || major || minor)
    final prefix = Uint8List.fromList('SubAddr\x00'.codeUnits);
    final data = Uint8List(prefix.length + 32 + 8)
      ..setAll(0, prefix)
      ..setAll(prefix.length, privateViewKey)
      ..buffer.asByteData().setUint32(prefix.length + 32, accountIndex, Endian.little)
      ..buffer.asByteData().setUint32(prefix.length + 36, addressIndex, Endian.little);

    final m = Keccak.hash256(data);
    final mScalar = Ed25519.scalarReduce(Uint8List(64)..setAll(0, m));

    // D = B + m*G
    final spendPoint = Ed25519.bytesToPoint(publicSpendKey);
    final mG = Ed25519.scalarMultBase(_bytesToBigInt(mScalar));
    final subSpendPoint = Ed25519.pointAdd(spendPoint, mG);
    final subSpendKey = Ed25519.pointToBytes(subSpendPoint);

    // C = a*D
    final viewScalar = _bytesToBigInt(privateViewKey);
    final subViewPoint = Ed25519.scalarMult(subSpendPoint, viewScalar);
    final subViewKey = Ed25519.pointToBytes(subViewPoint);

    return (subSpendKey, subViewKey);
  }

  static BigInt _bytesToBigInt(Uint8List bytes) {
    var result = BigInt.zero;
    for (var i = bytes.length - 1; i >= 0; i--) {
      result = (result << 8) | BigInt.from(bytes[i]);
    }
    return result;
  }
}
