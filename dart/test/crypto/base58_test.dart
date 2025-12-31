import 'dart:typed_data';

import 'package:test/test.dart';
import 'package:monero_dart/src/crypto/base58.dart';

void main() {
  group('Base58', () {
    test('encode empty array', () {
      final input = Uint8List(0);
      final encoded = Base58.encodeRaw(input);

      expect(encoded, isEmpty);
    });

    test('encode single byte', () {
      final input = Uint8List.fromList([0x00]);
      final encoded = Base58.encodeRaw(input);

      expect(encoded, isNotEmpty);
    });

    test('round trip encoding', () {
      final input = Uint8List.fromList(List.generate(32, (i) => i));
      final encoded = Base58.encode(input);
      final decoded = Base58.decode(encoded);

      expect(decoded, equals(input));
    });

    test('round trip raw encoding', () {
      final input = Uint8List.fromList(List.generate(32, (i) => i));
      final encoded = Base58.encodeRaw(input);
      final decoded = Base58.decodeRaw(encoded);

      expect(decoded, equals(input));
    });

    test('8-byte block encoding produces 11 characters', () {
      // Full block should produce 11 characters
      final input = Uint8List.fromList(List.filled(8, 0xFF));
      final encoded = Base58.encodeRaw(input);

      expect(encoded.length, equals(11));
    });

    test('valid characters accepted', () {
      expect(Base58.isValid('4'), isTrue);
      expect(Base58.isValid('123ABCabc'), isTrue);
    });

    test('invalid characters rejected', () {
      expect(Base58.isValid('0OIl'), isFalse);
    });

    test('invalid checksum throws', () {
      // Corrupt the checksum by changing last character
      final input = Uint8List.fromList(List.generate(32, (i) => i));
      final encoded = Base58.encode(input);
      final corrupted = '${encoded.substring(0, encoded.length - 1)}X';

      expect(() => Base58.decode(corrupted), throwsFormatException);
    });
  });
}
