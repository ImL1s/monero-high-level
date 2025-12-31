import 'dart:typed_data';

import 'package:test/test.dart';
import 'package:monero_dart/src/crypto/keccak.dart';

void main() {
  group('Keccak', () {
    test('empty input produces correct hash', () {
      final input = Uint8List(0);
      final expected = _hexToBytes(
        'c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470',
      );

      final result = Keccak.hash256(input);

      expect(result, equals(expected));
    });

    test('single byte input', () {
      final input = Uint8List.fromList([0x00]);
      final result = Keccak.hash256(input);

      expect(result.length, equals(32));
    });

    test('32-byte input is deterministic', () {
      final input = Uint8List.fromList(List.generate(32, (i) => i));
      final result1 = Keccak.hash256(input);
      final result2 = Keccak.hash256(input);

      expect(result1, equals(result2));
    });

    test('abc test vector (NIST ShortMsgKAT)', () {
      // Standard test vector: Keccak-256("abc")
      // From NIST ShortMsgKAT_256.txt
      final input = Uint8List.fromList('abc'.codeUnits);
      final expected = _hexToBytes(
        '4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45',
      );

      final result = Keccak.hash256(input);

      expect(result, equals(expected));
    });

    test('long input (> one block 136 bytes)', () {
      // Test with input longer than one block (136 bytes for Keccak-256)
      final input = Uint8List.fromList(
        List.generate(200, (i) => i % 256),
      );
      final result = Keccak.hash256(input);

      expect(result.length, equals(32));
    });

    test('monero seed derivation pattern', () {
      // Simulate Monero's key derivation from seed
      final seed = Uint8List.fromList(List.filled(32, 0x42));
      final firstHash = Keccak.hash256(seed);
      final secondHash = Keccak.hash256(firstHash);

      expect(firstHash.length, equals(32));
      expect(secondHash.length, equals(32));
      expect(firstHash, equals(Keccak.hash256(seed)));
    });

    test('NIST hex message test vector', () {
      // Message of 24 bits = 0x616263 (ASCII "abc")
      final input = _hexToBytes('616263');
      final expected = _hexToBytes(
        '4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45',
      );
      expect(Keccak.hash256(input), equals(expected));
    });

    test('concatenated public keys pattern', () {
      // Simulate concatenating two 32-byte public keys
      final pubKey1 = Uint8List.fromList(List.filled(32, 0xAA));
      final pubKey2 = Uint8List.fromList(List.filled(32, 0xBB));
      final combined = Uint8List.fromList([...pubKey1, ...pubKey2]);

      final hash = Keccak.hash256(combined);
      expect(hash.length, equals(32));

      // Hash of 64 bytes should be deterministic
      expect(hash, equals(Keccak.hash256(combined)));
    });
  });
}

Uint8List _hexToBytes(String hex) {
  final len = hex.length;
  final data = Uint8List(len ~/ 2);
  for (var i = 0; i < len; i += 2) {
    data[i ~/ 2] = int.parse(hex.substring(i, i + 2), radix: 16);
  }
  return data;
}
