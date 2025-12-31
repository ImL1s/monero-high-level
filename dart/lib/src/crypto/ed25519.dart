import 'dart:typed_data';

import 'keccak.dart';

/// Ed25519 elliptic curve operations for Monero.
///
/// Monero uses Ed25519 (Curve25519 in Edwards form) for its cryptographic operations.
/// The curve equation is: -x² + y² = 1 + d·x²·y² where d = -121665/121666
///
/// Key differences from standard Ed25519:
/// - Uses different key derivation (deterministic from Keccak hash)
/// - Uses "key images" for double-spend prevention
/// - Integrates with ring signatures (CLSAG)
///
/// Reference: https://ed25519.cr.yp.to/
class Ed25519 {
  /// Prime field modulus p = 2^255 - 19
  static final BigInt p = BigInt.parse(
    '7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed',
    radix: 16,
  );

  /// Group order l = 2^252 + 27742317777372353535851937790883648493
  static final BigInt l = BigInt.parse(
    '1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed',
    radix: 16,
  );

  /// Curve constant d = -121665/121666 mod p
  static final BigInt d = BigInt.parse(
    '52036cee2b6ffe738cc740797779e89800700a4d4141d8ab75eb4dca135978a3',
    radix: 16,
  );

  /// Base point G (generator) x-coordinate
  static final BigInt gx = BigInt.parse(
    '216936d3cd6e53fec0a4e231fdd6dc5c692cc7609525a7b2c9562d608f25d51a',
    radix: 16,
  );

  /// Base point G (generator) y-coordinate
  static final BigInt gy = BigInt.parse(
    '6666666666666666666666666666666666666666666666666666666666666658',
    radix: 16,
  );

  /// Generate a key pair from a seed
  ///
  /// [seed] 32-byte random seed
  /// Returns (private key, public key) pair
  static (Uint8List, Uint8List) generateKeyPair(Uint8List seed) {
    if (seed.length != 32) {
      throw ArgumentError('Seed must be 32 bytes');
    }

    // Hash seed to get private scalar
    final h = Keccak.hash256(seed);

    // Clamp the scalar (clear lowest 3 bits, set highest bit)
    h[0] &= 248;
    h[31] &= 127;
    h[31] |= 64;

    // Compute public key: A = aG
    final privateScalar = _bytesToBigInt(h);
    final publicPoint = scalarMultBase(privateScalar);

    return (h, pointToBytes(publicPoint));
  }

  /// Compute key image for a private key
  /// I = x * Hp(P) where P is the public key
  ///
  /// [privateKey] Private key scalar
  /// [publicKey] Public key point
  /// Returns key image bytes
  static Uint8List computeKeyImage(Uint8List privateKey, Uint8List publicKey) {
    final hp = hashToPoint(publicKey);
    final x = _bytesToBigInt(privateKey);
    final keyImage = scalarMult(hp, x);
    return pointToBytes(keyImage);
  }

  /// Hash arbitrary data to a point on the curve
  /// Used for key image generation
  ///
  /// [data] Input data
  /// Returns point on the curve
  static Point hashToPoint(Uint8List data) {
    // Elligator 2 map or try-and-increment
    // TODO: Implement proper hash-to-curve
    final hash = Keccak.hash256(data);
    return bytesToPoint(hash);
  }

  /// Scalar multiplication with base point G
  static Point scalarMultBase(BigInt scalar) {
    return scalarMult(Point(gx, gy), scalar);
  }

  /// Scalar multiplication: point * scalar
  static Point scalarMult(Point point, BigInt scalar) {
    // Double-and-add algorithm
    // TODO: Implement constant-time scalar multiplication
    var result = Point.identity;
    var temp = point;
    var s = scalar;

    while (s > BigInt.zero) {
      if (s.isOdd) {
        result = pointAdd(result, temp);
      }
      temp = pointDouble(temp);
      s >>= 1;
    }

    return result;
  }

  /// Point addition
  static Point pointAdd(Point p1, Point p2) {
    if (p1.isIdentity) return p2;
    if (p2.isIdentity) return p1;

    // Extended coordinates addition
    // TODO: Implement proper extended coordinates
    final x1 = p1.x;
    final y1 = p1.y;
    final x2 = p2.x;
    final y2 = p2.y;

    // Unified addition formula for twisted Edwards curves
    final x1y2 = (x1 * y2) % p;
    final x2y1 = (x2 * y1) % p;
    final x1x2 = (x1 * x2) % p;
    final y1y2 = (y1 * y2) % p;
    final dx1x2y1y2 = (d * x1x2 * y1y2) % p;

    final x3num = (x1y2 + x2y1) % p;
    final x3den = (BigInt.one + dx1x2y1y2) % p;
    final y3num = (y1y2 + x1x2) % p;  // Note: -1 * -x² = x²
    final y3den = (BigInt.one - dx1x2y1y2) % p;

    final x3 = (x3num * _modInverse(x3den, p)) % p;
    final y3 = (y3num * _modInverse(y3den, p)) % p;

    return Point(x3, y3);
  }

  /// Point doubling
  static Point pointDouble(Point point) {
    return pointAdd(point, point);
  }

  /// Reduce a 64-byte scalar modulo l
  static Uint8List scalarReduce(Uint8List scalar) {
    if (scalar.length != 64) {
      throw ArgumentError('Input must be 64 bytes');
    }
    final s = _bytesToBigInt(scalar);
    return _bigIntToBytes(s % l, 32);
  }

  /// Scalar multiplication: a * b mod l
  static Uint8List scalarMulMod(Uint8List a, Uint8List b) {
    final aInt = _bytesToBigInt(a);
    final bInt = _bytesToBigInt(b);
    return _bigIntToBytes((aInt * bInt) % l, 32);
  }

  /// Scalar addition: a + b mod l
  static Uint8List scalarAddMod(Uint8List a, Uint8List b) {
    final aInt = _bytesToBigInt(a);
    final bInt = _bytesToBigInt(b);
    return _bigIntToBytes((aInt + bInt) % l, 32);
  }

  /// Convert point to 32-byte representation
  static Uint8List pointToBytes(Point point) {
    // Compress point: y-coordinate with x-sign in high bit
    final result = _bigIntToBytes(point.y, 32);
    if (point.x.isOdd) {
      result[31] |= 0x80;
    }
    return result;
  }

  /// Convert 32-byte representation to point
  static Point bytesToPoint(Uint8List bytes) {
    if (bytes.length != 32) {
      throw ArgumentError('Point must be 32 bytes');
    }

    // Decompress point
    final y = _bytesToBigInt(bytes) & ((BigInt.one << 255) - BigInt.one);
    final xSign = (bytes[31] >> 7) & 1;

    // Compute x from y: x² = (y² - 1) / (d·y² + 1)
    final y2 = (y * y) % p;
    final num = (y2 - BigInt.one) % p;
    final den = (d * y2 + BigInt.one) % p;
    final x2 = (num * _modInverse(den, p)) % p;

    // Square root (using Tonelli-Shanks or p ≡ 3 mod 4 shortcut)
    var x = _modSqrt(x2, p);
    if (x.isOdd != (xSign == 1)) {
      x = p - x;
    }

    return Point(x, y);
  }

  static BigInt _bytesToBigInt(Uint8List bytes) {
    var result = BigInt.zero;
    for (var i = bytes.length - 1; i >= 0; i--) {
      result = (result << 8) | BigInt.from(bytes[i]);
    }
    return result;
  }

  static Uint8List _bigIntToBytes(BigInt value, int length) {
    final result = Uint8List(length);
    var v = value;
    for (var i = 0; i < length; i++) {
      result[i] = (v & BigInt.from(0xFF)).toInt();
      v >>= 8;
    }
    return result;
  }

  static BigInt _modInverse(BigInt a, BigInt m) {
    // Extended Euclidean algorithm
    var t = BigInt.zero;
    var newT = BigInt.one;
    var r = m;
    var newR = a % m;

    while (newR != BigInt.zero) {
      final quotient = r ~/ newR;
      final tempT = t;
      t = newT;
      newT = tempT - quotient * newT;
      final tempR = r;
      r = newR;
      newR = tempR - quotient * newR;
    }

    if (r > BigInt.one) {
      throw ArgumentError('Not invertible');
    }
    if (t < BigInt.zero) {
      t += m;
    }
    return t;
  }

  static BigInt _modSqrt(BigInt a, BigInt p) {
    // For p ≡ 5 (mod 8), use Atkin's algorithm
    // For Ed25519, p ≡ 5 (mod 8)
    final v = a.modPow((p - BigInt.from(5)) ~/ BigInt.from(8), p);
    final i = (BigInt.two * a * v * v) % p;
    return (a * v * (i - BigInt.one)) % p;
  }
}

/// Point on the Ed25519 curve
class Point {
  /// X-coordinate
  final BigInt x;

  /// Y-coordinate
  final BigInt y;

  /// Identity point (neutral element)
  static final Point identity = Point(BigInt.zero, BigInt.one);

  const Point(this.x, this.y);

  /// Check if this is the identity point
  bool get isIdentity => x == BigInt.zero && y == BigInt.one;

  @override
  bool operator ==(Object other) {
    if (other is! Point) return false;
    return x == other.x && y == other.y;
  }

  @override
  int get hashCode => Object.hash(x, y);

  @override
  String toString() => 'Point($x, $y)';
}
