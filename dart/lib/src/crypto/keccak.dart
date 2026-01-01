import 'dart:typed_data';

/// Keccak hash function implementation.
///
/// Monero uses Keccak-256 (NOT SHA3-256, which has different padding).
/// This is a critical distinction as Keccak uses 10*1 padding while
/// SHA3 uses 0110*1 padding.
///
/// Reference: https://keccak.team/keccak.html
class Keccak {
  static const int _rate = 1088 ~/ 8; // 136 bytes for Keccak-256
  static const int _outputLength = 256 ~/ 8; // 32 bytes

  /// Round constants for Keccak-f[1600]
  static final List<BigInt> _rc = [
    BigInt.parse('0x0000000000000001'),
    BigInt.parse('0x0000000000008082'),
    BigInt.parse('0x800000000000808A'),
    BigInt.parse('0x8000000080008000'),
    BigInt.parse('0x000000000000808B'),
    BigInt.parse('0x0000000080000001'),
    BigInt.parse('0x8000000080008081'),
    BigInt.parse('0x8000000000008009'),
    BigInt.parse('0x000000000000008A'),
    BigInt.parse('0x0000000000000088'),
    BigInt.parse('0x0000000080008009'),
    BigInt.parse('0x000000008000000A'),
    BigInt.parse('0x000000008000808B'),
    BigInt.parse('0x800000000000008B'),
    BigInt.parse('0x8000000000008089'),
    BigInt.parse('0x8000000000008003'),
    BigInt.parse('0x8000000000008002'),
    BigInt.parse('0x8000000000000080'),
    BigInt.parse('0x000000000000800A'),
    BigInt.parse('0x800000008000000A'),
    BigInt.parse('0x8000000080008081'),
    BigInt.parse('0x8000000000008080'),
    BigInt.parse('0x0000000080000001'),
    BigInt.parse('0x8000000080008008'),
  ];

  /// Rotation offsets
  static const List<List<int>> _rotations = [
    [0, 36, 3, 41, 18],
    [1, 44, 10, 45, 2],
    [62, 6, 43, 15, 61],
    [28, 55, 25, 21, 56],
    [27, 20, 39, 8, 14],
  ];

  /// Computes Keccak-256 hash of the input data.
  ///
  /// [data] Input data to hash
  /// Returns 32-byte hash result
  static Uint8List hash256(Uint8List data) {
    return keccak(data, _outputLength);
  }

  /// Computes Keccak hash with specified output length.
  ///
  /// [data] Input data to hash
  /// [outputLength] Desired output length in bytes
  /// Returns hash result of specified length
  static Uint8List keccak(Uint8List data, int outputLength) {
    final state = List<BigInt>.filled(25, BigInt.zero); // 5x5 state matrix

    // Absorb phase
    final padded = _pad(data);
    for (var i = 0; i < padded.length; i += _rate) {
      final end = i + _rate < padded.length ? i + _rate : padded.length;
      final block = padded.sublist(i, end);
      _absorb(state, block);
      _keccakF(state);
    }

    // Squeeze phase
    return _squeeze(state, outputLength);
  }

  static Uint8List _pad(Uint8List data) {
    // Keccak padding: 10*1 pattern (NOT SHA3 which uses 0110*1)
    final padLen = _rate - (data.length % _rate);
    final padded = Uint8List(data.length + padLen);
    padded.setAll(0, data);

    if (padLen == 1) {
      padded[data.length] = 0x81;
    } else {
      padded[data.length] = 0x01;
      padded[padded.length - 1] = 0x80;
    }

    return padded;
  }

  static void _absorb(List<BigInt> state, Uint8List block) {
    final lanes = block.length ~/ 8;
    for (var i = 0; i < lanes && i < _rate ~/ 8; i++) {
      var lane = BigInt.zero;
      for (var j = 0; j < 8; j++) {
        if (i * 8 + j < block.length) {
          lane |= BigInt.from(block[i * 8 + j]) << (j * 8);
        }
      }
      state[i] ^= lane;
    }
  }

  static Uint8List _squeeze(List<BigInt> state, int outputLength) {
    final output = Uint8List(outputLength);
    var offset = 0;

    while (offset < outputLength) {
      for (var i = 0; i < _rate ~/ 8 && offset < outputLength; i++) {
        final lane = state[i];
        for (var j = 0; j < 8 && offset < outputLength; j++) {
          output[offset++] = ((lane >> (j * 8)) & BigInt.from(0xFF)).toInt();
        }
      }
      if (offset < outputLength) {
        _keccakF(state);
      }
    }

    return output;
  }

  static void _keccakF(List<BigInt> state) {
    for (var round = 0; round < 24; round++) {
      _theta(state);
      _rhoPi(state);
      _chi(state);
      _iota(state, round);
    }
  }

  static void _theta(List<BigInt> state) {
    final c = List<BigInt>.filled(5, BigInt.zero);
    final d = List<BigInt>.filled(5, BigInt.zero);

    for (var x = 0; x < 5; x++) {
      c[x] = state[x] ^
          state[x + 5] ^
          state[x + 10] ^
          state[x + 15] ^
          state[x + 20];
    }

    for (var x = 0; x < 5; x++) {
      d[x] = c[(x + 4) % 5] ^ _rotateLeft(c[(x + 1) % 5], 1);
    }

    for (var x = 0; x < 5; x++) {
      for (var y = 0; y < 5; y++) {
        state[x + y * 5] ^= d[x];
      }
    }
  }

  static void _rhoPi(List<BigInt> state) {
    final temp = List<BigInt>.filled(25, BigInt.zero);
    for (var x = 0; x < 5; x++) {
      for (var y = 0; y < 5; y++) {
        final newX = y;
        final newY = (2 * x + 3 * y) % 5;
        temp[newX + newY * 5] =
            _rotateLeft(state[x + y * 5], _rotations[x][y]);
      }
    }
    for (var i = 0; i < 25; i++) {
      state[i] = temp[i];
    }
  }

  static void _chi(List<BigInt> state) {
    for (var y = 0; y < 5; y++) {
      final row = [
        state[0 + y * 5],
        state[1 + y * 5],
        state[2 + y * 5],
        state[3 + y * 5],
        state[4 + y * 5],
      ];
      for (var x = 0; x < 5; x++) {
        state[x + y * 5] = row[x] ^
            ((~row[(x + 1) % 5] & _mask64) & row[(x + 2) % 5]);
      }
    }
  }

  static void _iota(List<BigInt> state, int round) {
    state[0] ^= _rc[round];
  }

  static final BigInt _mask64 = (BigInt.one << 64) - BigInt.one;

  static BigInt _rotateLeft(BigInt value, int bits) {
    value &= _mask64;
    return ((value << bits) | (value >> (64 - bits))) & _mask64;
  }
}
