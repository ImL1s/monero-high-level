import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:pointycastle/export.dart' as pc;

/// ChaCha20-Poly1305 authenticated encryption.
///
/// Monero uses ChaCha20-Poly1305 for encrypting wallet files
/// and for encrypted payment IDs.
class WalletCipher {
  static const int keySize = 32;
  static const int nonceSize = 12;
  static const int tagSize = 16;

  /// Encrypt data with ChaCha20-Poly1305
  ///
  /// [plaintext] Data to encrypt
  /// [key] 32-byte encryption key
  /// [nonce] 12-byte nonce (must be unique per key)
  /// [associatedData] Optional associated data for authentication
  /// Returns ciphertext + 16-byte auth tag
  static Uint8List encrypt({
    required Uint8List plaintext,
    required Uint8List key,
    required Uint8List nonce,
    Uint8List? associatedData,
  }) {
    _validateInputs(key, nonce);

    final cipher = pc.ChaCha20Poly1305(
      pc.ChaCha7539Engine(),
      pc.Poly1305(),
    )..init(
        true, // forEncryption
        pc.AEADParameters(
          pc.KeyParameter(key),
          tagSize * 8, // tag length in bits
          nonce,
          associatedData ?? Uint8List(0),
        ),
      );

    final output = Uint8List(cipher.getOutputSize(plaintext.length));
    final len = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);
    cipher.doFinal(output, len);
    return output;
  }

  /// Decrypt data with ChaCha20-Poly1305
  ///
  /// [ciphertext] Encrypted data with auth tag
  /// [key] 32-byte encryption key
  /// [nonce] 12-byte nonce
  /// [associatedData] Optional associated data for verification
  /// Returns decrypted plaintext
  /// Throws if authentication fails
  static Uint8List decrypt({
    required Uint8List ciphertext,
    required Uint8List key,
    required Uint8List nonce,
    Uint8List? associatedData,
  }) {
    _validateInputs(key, nonce);

    if (ciphertext.length < tagSize) {
      throw ArgumentError('Ciphertext too short');
    }

    final cipher = pc.ChaCha20Poly1305(
      pc.ChaCha7539Engine(),
      pc.Poly1305(),
    )..init(
        false, // forDecryption
        pc.AEADParameters(
          pc.KeyParameter(key),
          tagSize * 8,
          nonce,
          associatedData ?? Uint8List(0),
        ),
      );

    final output = Uint8List(cipher.getOutputSize(ciphertext.length));
    try {
      final len =
          cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);
      cipher.doFinal(output, len);
      return output;
    } on pc.InvalidCipherTextException {
      throw AuthenticationException('MAC verification failed');
    } on ArgumentError catch (e) {
      // pointycastle throws ArgumentError for MAC failures in some versions
      if (e.message.toString().contains('mac check')) {
        throw AuthenticationException('MAC verification failed');
      }
      rethrow;
    }
  }

  /// Derive encryption key from password using Argon2id
  ///
  /// [password] User password
  /// [salt] 16-byte salt
  /// [memory] Memory cost in KiB (default 65536 = 64 MiB)
  /// [iterations] Time cost (default 3)
  /// [parallelism] Parallelism factor (default 4)
  /// Returns 32-byte derived key
  static Uint8List deriveKey({
    required String password,
    required Uint8List salt,
    int memory = 65536,
    int iterations = 3,
    int parallelism = 4,
  }) {
    final argon2 = pc.Argon2BytesGenerator();
    argon2.init(pc.Argon2Parameters(
      pc.Argon2Parameters.ARGON2_id,
      salt,
      desiredKeyLength: keySize,
      iterations: iterations,
      memory: memory,
      lanes: parallelism,
    ));

    final passwordBytes = Uint8List.fromList(utf8.encode(password));
    final key = Uint8List(keySize);
    argon2.deriveKey(passwordBytes, 0, key, 0);
    return key;
  }

  /// Generate a random salt for key derivation.
  static Uint8List generateSalt([int length = 16]) {
    final random = Random.secure();
    return Uint8List.fromList(
      List<int>.generate(length, (_) => random.nextInt(256)),
    );
  }

  /// Generate a random nonce for encryption.
  static Uint8List generateNonce() {
    final random = Random.secure();
    return Uint8List.fromList(
      List<int>.generate(nonceSize, (_) => random.nextInt(256)),
    );
  }

  static void _validateInputs(Uint8List key, Uint8List nonce) {
    if (key.length != keySize) {
      throw ArgumentError('Key must be $keySize bytes');
    }
    if (nonce.length != nonceSize) {
      throw ArgumentError('Nonce must be $nonceSize bytes');
    }
  }
}

/// Thrown when MAC verification fails during decryption.
class AuthenticationException implements Exception {
  final String message;
  AuthenticationException(this.message);

  @override
  String toString() => 'AuthenticationException: $message';
}

/// High-level wallet encryption utilities.
///
/// Provides a simple API for encrypting/decrypting wallet data with password.
class WalletEncryption {
  /// Magic bytes to identify encrypted wallet files.
  static final Uint8List magic =
      Uint8List.fromList([0x4D, 0x4F, 0x4E, 0x45]); // "MONE"

  /// Current encryption format version.
  static const int version = 1;

  /// Encrypt wallet data with password.
  ///
  /// Returns encrypted blob with header:
  /// [4 bytes magic][1 byte version][16 bytes salt][12 bytes nonce][ciphertext + tag]
  static Uint8List encrypt(String password, Uint8List plaintext) {
    final salt = WalletCipher.generateSalt();
    final nonce = WalletCipher.generateNonce();
    final key = WalletCipher.deriveKey(password: password, salt: salt);

    final ciphertext = WalletCipher.encrypt(
      plaintext: plaintext,
      key: key,
      nonce: nonce,
    );

    // Build output: magic + version + salt + nonce + ciphertext
    final output = BytesBuilder();
    output.add(magic);
    output.addByte(version);
    output.add(salt);
    output.add(nonce);
    output.add(ciphertext);

    return output.toBytes();
  }

  /// Decrypt wallet data with password.
  ///
  /// Throws [AuthenticationException] if password is wrong.
  /// Throws [FormatException] if data format is invalid.
  static Uint8List decrypt(String password, Uint8List encrypted) {
    if (encrypted.length <
        magic.length + 1 + 16 + 12 + WalletCipher.tagSize) {
      throw FormatException('Encrypted data too short');
    }

    // Verify magic
    for (var i = 0; i < magic.length; i++) {
      if (encrypted[i] != magic[i]) {
        throw FormatException('Invalid wallet file format');
      }
    }

    var offset = magic.length;

    // Check version
    final fileVersion = encrypted[offset++];
    if (fileVersion != version) {
      throw FormatException('Unsupported encryption version: $fileVersion');
    }

    // Extract salt
    final salt = Uint8List.fromList(encrypted.sublist(offset, offset + 16));
    offset += 16;

    // Extract nonce
    final nonce = Uint8List.fromList(encrypted.sublist(offset, offset + 12));
    offset += 12;

    // Extract ciphertext
    final ciphertext = Uint8List.fromList(encrypted.sublist(offset));

    // Derive key and decrypt
    final key = WalletCipher.deriveKey(
      password: password,
      salt: salt,
    );

    return WalletCipher.decrypt(
      ciphertext: ciphertext,
      key: key,
      nonce: nonce,
    );
  }

  /// Check if data appears to be an encrypted wallet file.
  static bool isEncrypted(Uint8List data) {
    if (data.length < magic.length) return false;
    for (var i = 0; i < magic.length; i++) {
      if (data[i] != magic[i]) return false;
    }
    return true;
  }
}

// Legacy alias for backward compatibility
typedef ChaCha20Poly1305 = WalletCipher;
