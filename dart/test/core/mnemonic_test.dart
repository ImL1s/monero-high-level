import 'dart:typed_data';

import 'package:test/test.dart';
import 'package:monero_dart/src/core/mnemonic.dart';
import 'package:monero_dart/src/core/english_word_list.dart';

void main() {
  group('Mnemonic', () {
    test('English word list has 1626 words', () {
      expect(englishWordList.length, equals(1626));
    });

    test('English word list has no duplicates', () {
      final unique = englishWordList.toSet();
      expect(unique.length, equals(englishWordList.length));
    });

    test('entropyToMnemonic produces 25 words', () {
      final entropy = Uint8List(32);
      for (var i = 0; i < 32; i++) entropy[i] = i;
      
      final mnemonic = Mnemonic.entropyToMnemonic(entropy);
      expect(mnemonic.length, equals(25));
    });

    test('all words are from word list', () {
      final entropy = Uint8List(32);
      for (var i = 0; i < 32; i++) entropy[i] = i;
      
      final mnemonic = Mnemonic.entropyToMnemonic(entropy);
      for (final word in mnemonic) {
        expect(englishWordList.contains(word), isTrue, reason: 'Word "$word" not in list');
      }
    });

    test('mnemonic is deterministic', () {
      final entropy = Uint8List(32);
      for (var i = 0; i < 32; i++) entropy[i] = 0x42;
      
      final mnemonic1 = Mnemonic.entropyToMnemonic(entropy);
      final mnemonic2 = Mnemonic.entropyToMnemonic(entropy);
      expect(mnemonic1, equals(mnemonic2));
    });

    test('different entropy produces different mnemonic', () {
      final entropy1 = Uint8List(32)..fillRange(0, 32, 0x01);
      final entropy2 = Uint8List(32)..fillRange(0, 32, 0x02);
      
      final mnemonic1 = Mnemonic.entropyToMnemonic(entropy1);
      final mnemonic2 = Mnemonic.entropyToMnemonic(entropy2);
      expect(mnemonic1, isNot(equals(mnemonic2)));
    });

    test('mnemonic to entropy round trip', () {
      final originalEntropy = Uint8List(32);
      for (var i = 0; i < 32; i++) originalEntropy[i] = (i * 7 + 13) % 256;
      
      final mnemonic = Mnemonic.entropyToMnemonic(originalEntropy);
      final recoveredEntropy = Mnemonic.mnemonicToEntropy(mnemonic);
      
      expect(recoveredEntropy, equals(originalEntropy));
    });

    test('checksum word is last word', () {
      final entropy = Uint8List(32);
      for (var i = 0; i < 32; i++) entropy[i] = i;
      
      final mnemonic = Mnemonic.entropyToMnemonic(entropy);
      final checksumWord = mnemonic[24];
      
      // Checksum word should be one of the seed words
      final seedWords = mnemonic.sublist(0, 24);
      expect(seedWords.contains(checksumWord), isTrue);
    });

    test('validate accepts valid mnemonic', () {
      final entropy = Uint8List(32);
      for (var i = 0; i < 32; i++) entropy[i] = 0x55;
      
      final mnemonic = Mnemonic.entropyToMnemonic(entropy);
      expect(Mnemonic.validate(mnemonic), isTrue);
    });

    test('validate rejects wrong word count', () {
      expect(Mnemonic.validate(['abbey', 'abducts']), isFalse);
      expect(Mnemonic.validate([]), isFalse);
    });

    test('generate produces valid mnemonic', () {
      final mnemonic = Mnemonic.generate();
      expect(mnemonic.length, equals(25));
      expect(Mnemonic.validate(mnemonic), isTrue);
    });
  });
}
