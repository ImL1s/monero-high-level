import 'dart:typed_data';

import 'package:monero_dart/src/transaction/models.dart';
import 'package:monero_dart/src/transaction/scanner.dart';
import 'package:test/test.dart';

void main() {
  group('TransactionModels', () {
    test('RctType.fromValue', () {
      expect(RctType.fromValue(0), RctType.none);
      expect(RctType.fromValue(1), RctType.full);
      expect(RctType.fromValue(5), RctType.clsag);
      expect(RctType.fromValue(6), RctType.bulletproofPlus);
      expect(RctType.fromValue(99), RctType.none); // Unknown
    });

    test('KeyImage creation and hex conversion', () {
      final bytes = Uint8List(32)..fillRange(0, 32, 0xAB);
      final keyImage = KeyImage(bytes);
      
      expect(keyImage.bytes.length, 32);
      expect(keyImage.toHex().length, 64);
      
      final fromHex = KeyImage.fromHex(keyImage.toHex());
      expect(fromHex, keyImage);
    });

    test('SubaddressIndex', () {
      const main = SubaddressIndex.main;
      expect(main.isMainAddress, true);
      expect(main.isSubaddress, false);
      expect(main.toString(), '(0,0)');
      
      const sub = SubaddressIndex(1, 5);
      expect(sub.isMainAddress, false);
      expect(sub.isSubaddress, true);
      expect(sub.toString(), '(1,5)');
    });

    test('TxOutput creation', () {
      final target = TxOutTarget(
        key: Uint8List(32),
        viewTag: 0x42,
      );
      final output = TxOutput(
        index: 0,
        amount: 1000000000,
        target: target,
        globalIndex: 12345,
      );
      
      expect(output.index, 0);
      expect(output.amount, 1000000000);
      expect(output.target.viewTag, 0x42);
      expect(output.globalIndex, 12345);
    });

    test('OwnedOutput outpoint', () {
      final txHash = Uint8List(32)..fillRange(0, 32, 0x12);
      final output = OwnedOutput(
        txHash: txHash,
        outputIndex: 1,
        globalIndex: 54321,
        amount: 5000000000,
        publicKey: Uint8List(32),
        blockHeight: 1000,
        timestamp: 1609459200,
      );
      
      expect(output.outpoint.endsWith(':1'), true);
      expect(output.outpoint.startsWith('12'), true);
    });

    test('ScannedOutput creation', () {
      final output = TxOutput(
        index: 0,
        amount: 0,
        target: TxOutTarget(key: Uint8List(32)),
      );
      final scanned = ScannedOutput(
        output: output,
        amount: 1000000000,
        isOwned: true,
        subaddressIndex: const SubaddressIndex(0, 1),
      );
      
      expect(scanned.isOwned, true);
      expect(scanned.amount, 1000000000);
      expect(scanned.subaddressIndex, const SubaddressIndex(0, 1));
    });
  });

  group('ViewKeyScanner', () {
    late Uint8List viewSecretKey;
    late Uint8List viewPublicKey;
    late Uint8List spendPublicKey;

    setUp(() {
      viewSecretKey = Uint8List(32)..setRange(0, 32, List.generate(32, (i) => i + 1));
      viewPublicKey = Uint8List(32)..setRange(0, 32, List.generate(32, (i) => i + 33));
      spendPublicKey = Uint8List(32)..setRange(0, 32, List.generate(32, (i) => i + 65));
    });

    test('scanner creation', () {
      final scanner = ViewKeyScanner(
        viewPublicKey: viewPublicKey,
        viewSecretKey: viewSecretKey,
        spendPublicKey: spendPublicKey,
      );
      expect(scanner, isNotNull);
    });

    test('precompute subaddresses', () {
      final scanner = ViewKeyScanner(
        viewPublicKey: viewPublicKey,
        viewSecretKey: viewSecretKey,
        spendPublicKey: spendPublicKey,
      );
      
      // Should not throw
      scanner.precomputeSubaddresses(majorMax: 1, minorMax: 4);
    });

    test('scan empty transaction', () {
      final scanner = ViewKeyScanner(
        viewPublicKey: viewPublicKey,
        viewSecretKey: viewSecretKey,
        spendPublicKey: spendPublicKey,
      );
      
      final tx = Transaction(
        hash: Uint8List(32),
        version: 2,
        unlockTime: 0,
        inputs: [],
        outputs: [],
        extra: TxExtra(txPubKey: Uint8List(32), raw: Uint8List(0)),
      );
      
      final results = scanner.scanTransaction(tx);
      expect(results, isEmpty);
    });

    test('scan transaction without tx public key', () {
      final scanner = ViewKeyScanner(
        viewPublicKey: viewPublicKey,
        viewSecretKey: viewSecretKey,
        spendPublicKey: spendPublicKey,
      );
      
      final tx = Transaction(
        hash: Uint8List(32),
        version: 2,
        unlockTime: 0,
        inputs: [],
        outputs: [
          TxOutput(
            index: 0,
            amount: 0,
            target: TxOutTarget(key: Uint8List(32)),
          ),
        ],
        extra: TxExtra(txPubKey: null, raw: Uint8List(0)),
      );
      
      final results = scanner.scanTransaction(tx);
      expect(results, isEmpty);
    });
  });

  group('SubaddressTable', () {
    test('add and lookup', () {
      final table = SubaddressTable();
      
      final key1 = Uint8List(32)..fillRange(0, 32, 0x01);
      final key2 = Uint8List(32)..fillRange(0, 32, 0x02);
      
      table.add(key1, const SubaddressIndex(0, 0));
      table.add(key2, const SubaddressIndex(1, 5));
      
      expect(table.size, 2);
      
      final result1 = table.lookup(key1);
      expect(result1, isNotNull);
      expect(result1!.major, 0);
      expect(result1.minor, 0);
      
      final result2 = table.lookup(key2);
      expect(result2, isNotNull);
      expect(result2!.major, 1);
      expect(result2.minor, 5);
      
      final key3 = Uint8List(32)..fillRange(0, 32, 0x03);
      expect(table.lookup(key3), isNull);
      
      table.clear();
      expect(table.size, 0);
    });
  });
}
