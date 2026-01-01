import 'dart:convert';
import 'dart:typed_data';

import 'package:monero_dart/src/crypto/chacha20.dart';
import 'package:test/test.dart';

void main() {
  group('WalletCipher', () {
    test('encrypt/decrypt round-trip', () {
      final key = WalletCipher.generateSalt(32);
      final nonce = WalletCipher.generateNonce();
      final plaintext = Uint8List.fromList(utf8.encode('Hello, Monero!'));

      final ciphertext = WalletCipher.encrypt(
        plaintext: plaintext,
        key: key,
        nonce: nonce,
      );

      expect(ciphertext.length, plaintext.length + WalletCipher.tagSize);

      final decrypted = WalletCipher.decrypt(
        ciphertext: ciphertext,
        key: key,
        nonce: nonce,
      );

      expect(decrypted, equals(plaintext));
    });

    test('wrong key fails', () {
      final key = WalletCipher.generateSalt(32);
      final wrongKey = WalletCipher.generateSalt(32);
      final nonce = WalletCipher.generateNonce();
      final plaintext = Uint8List.fromList([1, 2, 3, 4, 5]);

      final ciphertext = WalletCipher.encrypt(
        plaintext: plaintext,
        key: key,
        nonce: nonce,
      );

      expect(
        () => WalletCipher.decrypt(
          ciphertext: ciphertext,
          key: wrongKey,
          nonce: nonce,
        ),
        throwsA(isA<AuthenticationException>()),
      );
    });

    test('deriveKey produces consistent results', () {
      final salt = Uint8List.fromList(List.generate(16, (i) => i));
      final key1 = WalletCipher.deriveKey(
        password: 'testpassword',
        salt: salt,
        memory: 256, // Small for test speed
        iterations: 1,
        parallelism: 1,
      );
      final key2 = WalletCipher.deriveKey(
        password: 'testpassword',
        salt: salt,
        memory: 256,
        iterations: 1,
        parallelism: 1,
      );

      expect(key1, equals(key2));
      expect(key1.length, 32);
    });

    test('different passwords produce different keys', () {
      final salt = Uint8List.fromList(List.generate(16, (i) => i));
      final key1 = WalletCipher.deriveKey(
        password: 'password1',
        salt: salt,
        memory: 256,
        iterations: 1,
        parallelism: 1,
      );
      final key2 = WalletCipher.deriveKey(
        password: 'password2',
        salt: salt,
        memory: 256,
        iterations: 1,
        parallelism: 1,
      );

      expect(key1, isNot(equals(key2)));
    });
  });

  group('WalletEncryption', () {
    test('encrypt/decrypt round-trip', () {
      final plaintext = Uint8List.fromList(utf8.encode('{"test": "data"}'));
      final password = 'myPassword123';

      final encrypted = WalletEncryption.encrypt(password, plaintext);
      final decrypted = WalletEncryption.decrypt(password, encrypted);

      expect(decrypted, equals(plaintext));
    });

    test('wrong password throws AuthenticationException', () {
      final plaintext = Uint8List.fromList([1, 2, 3, 4, 5]);
      final encrypted = WalletEncryption.encrypt('correct', plaintext);

      expect(
        () => WalletEncryption.decrypt('wrong', encrypted),
        throwsA(isA<AuthenticationException>()),
      );
    });

    test('isEncrypted detects encrypted data', () {
      final plaintext = Uint8List.fromList([1, 2, 3, 4, 5]);
      final encrypted = WalletEncryption.encrypt('password', plaintext);

      expect(WalletEncryption.isEncrypted(encrypted), isTrue);
      expect(WalletEncryption.isEncrypted(plaintext), isFalse);
    });

    test('corrupted data throws FormatException', () {
      expect(
        () => WalletEncryption.decrypt('password', Uint8List(10)),
        throwsA(isA<FormatException>()),
      );
    });
  });
}
