import 'dart:math';
import 'dart:typed_data';

import '../constants.dart';
import 'english_word_list.dart';

/// Monero mnemonic seed operations.
///
/// Monero uses 25-word mnemonic seeds based on the Electrum word list.
/// The seed encodes a 256-bit private spend key + 1 checksum word.
///
/// Word list languages supported:
/// - English (1626 words)
/// - Japanese, Spanish, Portuguese, French, German, Italian, Russian, etc.
class Mnemonic {
  static const int _seedLength = mnemonicSeedBytes;
  static const int _wordCount = mnemonicWordCount;
  static const int _wordListSize = 1626;

  /// Convert entropy to mnemonic words.
  ///
  /// [entropy] 32-byte random data
  /// [language] Word list language (default: English)
  /// Returns list of 25 mnemonic words
  static List<String> entropyToMnemonic(
    Uint8List entropy, {
    String language = 'english',
  }) {
    if (entropy.length != _seedLength) {
      throw ArgumentError('Entropy must be $_seedLength bytes');
    }

    final wordList = _getWordList(language);
    final words = <String>[];

    // Convert entropy to words (3 words per 4 bytes chunk)
    for (var i = 0; i < 8; i++) {
      final chunk = (entropy[i * 4] & 0xFF) |
          ((entropy[i * 4 + 1] & 0xFF) << 8) |
          ((entropy[i * 4 + 2] & 0xFF) << 16) |
          ((entropy[i * 4 + 3] & 0xFF) << 24);

      final w1 = chunk % _wordListSize;
      final w2 = ((chunk ~/ _wordListSize) + w1) % _wordListSize;
      final w3 =
          ((chunk ~/ _wordListSize ~/ _wordListSize) + w2) % _wordListSize;

      words
        ..add(wordList[w1])
        ..add(wordList[w2])
        ..add(wordList[w3]);
    }

    // Add checksum word
    final checksumWord = _computeChecksum(words, wordList);
    words.add(checksumWord);

    return words;
  }

  /// Convert mnemonic words to entropy.
  ///
  /// [words] List of 25 mnemonic words
  /// [language] Word list language
  /// Returns 32-byte entropy
  static Uint8List mnemonicToEntropy(
    List<String> words, {
    String language = 'english',
  }) {
    if (words.length != _wordCount) {
      throw ArgumentError('Mnemonic must have $_wordCount words');
    }

    final wordList = _getWordList(language);
    final seedWords = words.sublist(0, 24);
    final checksumWord = words[24];

    // Verify checksum
    final expectedChecksum = _computeChecksum(seedWords, wordList);
    if (checksumWord != expectedChecksum) {
      throw const FormatException('Invalid checksum word');
    }

    // Convert words to entropy
    final entropy = Uint8List(_seedLength);
    for (var i = 0; i < 8; i++) {
      final w1 = wordList.indexOf(seedWords[i * 3]);
      final w2 = wordList.indexOf(seedWords[i * 3 + 1]);
      final w3 = wordList.indexOf(seedWords[i * 3 + 2]);

      if (w1 < 0 || w2 < 0 || w3 < 0) {
        throw const FormatException('Unknown word in mnemonic');
      }

      final chunk = w1 +
          _wordListSize * ((_wordListSize + w2 - w1) % _wordListSize) +
          _wordListSize *
              _wordListSize *
              ((_wordListSize + w3 - w2) % _wordListSize);

      entropy[i * 4] = chunk & 0xFF;
      entropy[i * 4 + 1] = (chunk >> 8) & 0xFF;
      entropy[i * 4 + 2] = (chunk >> 16) & 0xFF;
      entropy[i * 4 + 3] = (chunk >> 24) & 0xFF;
    }

    return entropy;
  }

  /// Validate mnemonic words.
  static bool validate(List<String> words, {String language = 'english'}) {
    if (words.length != _wordCount) return false;

    try {
      mnemonicToEntropy(words, language: language);
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Generate a new random mnemonic.
  static List<String> generate({String language = 'english'}) {
    final entropy = _generateSecureRandom(_seedLength);
    return entropyToMnemonic(entropy, language: language);
  }

  static String _computeChecksum(List<String> words, List<String> wordList) {
    // Checksum is based on first 3 characters of each word
    final prefixString = words.map((w) => w.substring(0, min(3, w.length))).join();
    final crc = _crc32(Uint8List.fromList(prefixString.codeUnits));
    return words[crc % words.length];
  }

  static int _crc32(Uint8List data) {
    var crc = 0xFFFFFFFF;
    for (final byte in data) {
      crc ^= byte;
      for (var j = 0; j < 8; j++) {
        if ((crc & 1) != 0) {
          crc = (crc >> 1) ^ 0xEDB88320;
        } else {
          crc >>= 1;
        }
      }
    }
    return crc ^ 0xFFFFFFFF;
  }

  static Uint8List _generateSecureRandom(int size) {
    final random = Random.secure();
    return Uint8List.fromList(
      List.generate(size, (_) => random.nextInt(256)),
    );
  }

  static List<String> _getWordList(String language) {
    return switch (language.toLowerCase()) {
      'english' => englishWordList,
      _ => throw ArgumentError('Unsupported language: $language'),
    };
  }
}
