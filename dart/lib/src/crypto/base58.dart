import 'dart:typed_data';

import 'keccak.dart';

/// Monero-specific Base58 encoding/decoding.
///
/// Monero uses a modified Base58 encoding that differs from Bitcoin's Base58Check:
/// - Different alphabet (reordered to avoid similar-looking characters)
/// - Different checksum algorithm (Keccak instead of double SHA-256)
/// - Block-based encoding (8 bytes → 11 chars)
///
/// Reference: https://monerodocs.org/cryptography/base58/
class Base58 {
  /// Monero's Base58 alphabet (different from Bitcoin's)
  static const String _alphabet =
      '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

  /// Sizes for block encoding
  /// Full block: 8 bytes → 11 characters
  static const List<int> _encodedBlockSizes = [0, 2, 3, 5, 6, 7, 9, 10, 11];
  static const int _fullBlockSize = 8;
  static const int _fullEncodedBlockSize = 11;

  /// Encode data to Base58 with Monero's checksum
  ///
  /// [data] Raw data to encode
  /// Returns Base58 encoded string with checksum
  static String encode(Uint8List data) {
    // Add 4-byte Keccak checksum
    final hash = Keccak.hash256(data);
    final checksum = hash.sublist(0, 4);
    final dataWithChecksum = Uint8List(data.length + 4)
      ..setAll(0, data)
      ..setAll(data.length, checksum);

    return encodeRaw(dataWithChecksum);
  }

  /// Decode Base58 string with checksum verification
  ///
  /// [encoded] Base58 encoded string
  /// Returns decoded data (without checksum)
  /// Throws [FormatException] if checksum is invalid
  static Uint8List decode(String encoded) {
    final dataWithChecksum = decodeRaw(encoded);

    if (dataWithChecksum.length < 4) {
      throw const FormatException('Encoded data too short');
    }

    final data = dataWithChecksum.sublist(0, dataWithChecksum.length - 4);
    final checksum = dataWithChecksum.sublist(dataWithChecksum.length - 4);

    // Verify checksum
    final hash = Keccak.hash256(data);
    final expectedChecksum = hash.sublist(0, 4);

    for (var i = 0; i < 4; i++) {
      if (checksum[i] != expectedChecksum[i]) {
        throw const FormatException('Invalid checksum');
      }
    }

    return data;
  }

  /// Encode raw data to Base58 (no checksum)
  static String encodeRaw(Uint8List data) {
    if (data.isEmpty) return '';

    final result = StringBuffer();

    // Process full 8-byte blocks
    var offset = 0;
    while (offset + _fullBlockSize <= data.length) {
      result.write(_encodeBlock(data.sublist(offset, offset + _fullBlockSize)));
      offset += _fullBlockSize;
    }

    // Process remaining bytes
    if (offset < data.length) {
      result.write(_encodeBlock(data.sublist(offset)));
    }

    return result.toString();
  }

  /// Decode raw Base58 string (no checksum verification)
  static Uint8List decodeRaw(String encoded) {
    if (encoded.isEmpty) return Uint8List(0);

    final result = <int>[];

    // Process full 11-character blocks
    var offset = 0;
    while (offset + _fullEncodedBlockSize <= encoded.length) {
      result.addAll(
        _decodeBlock(
          encoded.substring(offset, offset + _fullEncodedBlockSize),
          _fullBlockSize,
        ),
      );
      offset += _fullEncodedBlockSize;
    }

    // Process remaining characters
    if (offset < encoded.length) {
      final remaining = encoded.length - offset;
      final blockSize = _encodedBlockSizes.indexOf(remaining);
      if (blockSize == -1) {
        throw const FormatException('Invalid Base58 string length');
      }
      result.addAll(_decodeBlock(encoded.substring(offset), blockSize));
    }

    return Uint8List.fromList(result);
  }

  /// Encode a single block (up to 8 bytes)
  static String _encodeBlock(Uint8List block) {
    if (block.length > _fullBlockSize) {
      throw ArgumentError('Block too large');
    }

    // Convert bytes to a big integer
    var num = BigInt.zero;
    for (final byte in block) {
      num = num * BigInt.from(256) + BigInt.from(byte);
    }

    // Determine output size
    final outputSize = block.length == _fullBlockSize
        ? _fullEncodedBlockSize
        : _encodedBlockSizes[block.length];

    // Convert to base58
    final chars = List<String>.filled(outputSize, '');
    for (var i = outputSize - 1; i >= 0; i--) {
      chars[i] = _alphabet[(num % BigInt.from(58)).toInt()];
      num ~/= BigInt.from(58);
    }

    return chars.join();
  }

  /// Decode a single Base58 block
  static List<int> _decodeBlock(String block, int expectedSize) {
    // Convert from base58 to big integer
    var num = BigInt.zero;
    for (var i = 0; i < block.length; i++) {
      final char = block[i];
      final digit = _alphabet.indexOf(char);
      if (digit == -1) {
        throw FormatException('Invalid Base58 character: $char');
      }
      num = num * BigInt.from(58) + BigInt.from(digit);
    }

    // Convert to bytes
    final result = List<int>.filled(expectedSize, 0);
    for (var i = expectedSize - 1; i >= 0; i--) {
      result[i] = (num & BigInt.from(0xFF)).toInt();
      num >>= 8;
    }

    return result;
  }

  /// Check if a string is valid Base58
  static bool isValid(String encoded) {
    for (var i = 0; i < encoded.length; i++) {
      if (!_alphabet.contains(encoded[i])) {
        return false;
      }
    }
    return true;
  }

  /// Validate a Monero address format
  ///
  /// [address] Base58 encoded address
  /// Returns true if the address has valid format and checksum
  static bool validateAddress(String address) {
    try {
      final decoded = decode(address);
      // Standard address: 65 bytes (1 prefix + 64 keys)
      // Integrated address: 73 bytes (1 prefix + 64 keys + 8 payment ID)
      return decoded.length == 65 || decoded.length == 73;
    } catch (_) {
      return false;
    }
  }
}
