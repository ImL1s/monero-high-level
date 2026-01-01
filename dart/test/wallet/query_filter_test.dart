import 'dart:typed_data';

import 'package:monero_dart/src/storage/wallet_storage.dart';
import 'package:monero_dart/src/wallet/wallet_manager.dart';
import 'package:test/test.dart';

void main() {
  group('OutputFilter.matches', () {
    StoredOutput createOutput({
      BigInt? amount,
      int accountIndex = 0,
      int subaddressIndex = 0,
      bool spent = false,
      bool frozen = false,
    }) {
      return StoredOutput(
        keyImage: Uint8List.fromList(List.filled(32, 0)),
        publicKey: Uint8List.fromList(List.filled(32, 1)),
        amount: amount ?? BigInt.from(1000000000),
        globalIndex: 100,
        txHash: Uint8List.fromList(List.filled(32, 2)),
        localIndex: 0,
        height: 1000,
        accountIndex: accountIndex,
        subaddressIndex: subaddressIndex,
        spent: spent,
        spendingTxHash: null,
        frozen: frozen,
        unlockTime: 0,
      );
    }

    test('all filter matches everything', () {
      const filter = OutputFilter.all;

      expect(filter.matches(createOutput()), isTrue);
      expect(filter.matches(createOutput(spent: true)), isTrue);
      expect(filter.matches(createOutput(frozen: true)), isTrue);
      expect(filter.matches(createOutput(spent: true, frozen: true)), isTrue);
    });

    test('spendable filter excludes spent and frozen', () {
      const filter = OutputFilter.spendable;

      expect(filter.matches(createOutput()), isTrue);
      expect(filter.matches(createOutput(spent: true)), isFalse);
      expect(filter.matches(createOutput(frozen: true)), isFalse);
    });

    test('account filter', () {
      const filter = OutputFilter(accountIndex: 1);

      expect(filter.matches(createOutput(accountIndex: 0)), isFalse);
      expect(filter.matches(createOutput(accountIndex: 1)), isTrue);
      expect(filter.matches(createOutput(accountIndex: 2)), isFalse);
    });

    test('subaddress filter', () {
      const filter = OutputFilter(subaddressIndex: 5);

      expect(filter.matches(createOutput(subaddressIndex: 0)), isFalse);
      expect(filter.matches(createOutput(subaddressIndex: 5)), isTrue);
    });

    test('amount range filter', () {
      final minOnly = OutputFilter(minAmount: BigInt.from(500));
      final maxOnly = OutputFilter(maxAmount: BigInt.from(2000));
      final range = OutputFilter(
        minAmount: BigInt.from(500),
        maxAmount: BigInt.from(2000),
      );

      expect(minOnly.matches(createOutput(amount: BigInt.from(100))), isFalse);
      expect(minOnly.matches(createOutput(amount: BigInt.from(500))), isTrue);
      expect(minOnly.matches(createOutput(amount: BigInt.from(1000))), isTrue);

      expect(maxOnly.matches(createOutput(amount: BigInt.from(1000))), isTrue);
      expect(maxOnly.matches(createOutput(amount: BigInt.from(2000))), isTrue);
      expect(maxOnly.matches(createOutput(amount: BigInt.from(3000))), isFalse);

      expect(range.matches(createOutput(amount: BigInt.from(100))), isFalse);
      expect(range.matches(createOutput(amount: BigInt.from(1000))), isTrue);
      expect(range.matches(createOutput(amount: BigInt.from(3000))), isFalse);
    });
  });

  group('TransactionFilter.matches', () {
    StoredTransaction createTx({
      int? height,
      int timestamp = 1700000000,
      bool incoming = true,
      int accountIndex = 0,
      List<int> subaddressIndices = const [0],
      BigInt? amount,
      Uint8List? paymentId,
    }) {
      return StoredTransaction(
        hash: Uint8List.fromList(List.filled(32, 0)),
        height: height,
        timestamp: timestamp,
        fee: BigInt.from(10000),
        incoming: incoming,
        accountIndex: accountIndex,
        subaddressIndices: subaddressIndices,
        amount: amount ?? BigInt.from(1000000000),
        paymentId: paymentId,
        note: null,
      );
    }

    test('all filter matches everything', () {
      const filter = TransactionFilter.all;

      expect(filter.matches(createTx(height: 1000)), isTrue);
      expect(filter.matches(createTx(height: null)), isTrue);
      expect(filter.matches(createTx(incoming: true)), isTrue);
      expect(filter.matches(createTx(incoming: false)), isTrue);
    });

    test('confirmed filter excludes pending', () {
      const filter = TransactionFilter.confirmed;

      expect(filter.matches(createTx(height: 1000)), isTrue);
      expect(filter.matches(createTx(height: null)), isFalse);
    });

    test('incoming/outgoing filter', () {
      const inFilter = TransactionFilter.incomingOnly;
      const outFilter = TransactionFilter.outgoingOnly;

      expect(inFilter.matches(createTx(incoming: true)), isTrue);
      expect(inFilter.matches(createTx(incoming: false)), isFalse);

      expect(outFilter.matches(createTx(incoming: true)), isFalse);
      expect(outFilter.matches(createTx(incoming: false)), isTrue);
    });

    test('account filter', () {
      const filter = TransactionFilter(accountIndex: 1);

      expect(filter.matches(createTx(accountIndex: 0)), isFalse);
      expect(filter.matches(createTx(accountIndex: 1)), isTrue);
    });

    test('subaddress filter', () {
      const filter = TransactionFilter(subaddressIndex: 2);

      expect(filter.matches(createTx(subaddressIndices: [0, 1])), isFalse);
      expect(filter.matches(createTx(subaddressIndices: [0, 2])), isTrue);
      expect(filter.matches(createTx(subaddressIndices: [2])), isTrue);
    });

    test('height range filter', () {
      const filter = TransactionFilter(
        minHeight: 500,
        maxHeight: 1500,
      );

      expect(filter.matches(createTx(height: 100)), isFalse);
      expect(filter.matches(createTx(height: 500)), isTrue);
      expect(filter.matches(createTx(height: 1000)), isTrue);
      expect(filter.matches(createTx(height: 1500)), isTrue);
      expect(filter.matches(createTx(height: 2000)), isFalse);
      // Pending (null height) doesn't match height range filter
      expect(filter.matches(createTx(height: null)), isFalse);
    });

    test('timestamp range filter', () {
      const filter = TransactionFilter(
        fromTimestamp: 1600000000,
        toTimestamp: 1800000000,
      );

      expect(filter.matches(createTx(timestamp: 1500000000)), isFalse);
      expect(filter.matches(createTx(timestamp: 1600000000)), isTrue);
      expect(filter.matches(createTx(timestamp: 1700000000)), isTrue);
      expect(filter.matches(createTx(timestamp: 1800000000)), isTrue);
      expect(filter.matches(createTx(timestamp: 1900000000)), isFalse);
    });

    test('amount range filter', () {
      final filter = TransactionFilter(
        minAmount: BigInt.from(500),
        maxAmount: BigInt.from(2000),
      );

      expect(filter.matches(createTx(amount: BigInt.from(100))), isFalse);
      expect(filter.matches(createTx(amount: BigInt.from(1000))), isTrue);
      expect(filter.matches(createTx(amount: BigInt.from(3000))), isFalse);
    });

    test('payment ID filter', () {
      final paymentId = Uint8List.fromList(
        [0xab, 0xcd, 0xef, 0x12, 0x34, 0x56, 0x78, 0x90],
      );

      const filter = TransactionFilter(paymentId: 'abcd');

      expect(filter.matches(createTx(paymentId: null)), isFalse);
      expect(filter.matches(createTx(paymentId: paymentId)), isTrue);
    });

    test('combined filters', () {
      const filter = TransactionFilter(
        accountIndex: 0,
        incoming: true,
        minHeight: 100,
        includePending: false,
      );

      // All conditions match
      expect(
        filter.matches(createTx(
          accountIndex: 0,
          incoming: true,
          height: 200,
        )),
        isTrue,
      );

      // Wrong account
      expect(
        filter.matches(createTx(
          accountIndex: 1,
          incoming: true,
          height: 200,
        )),
        isFalse,
      );

      // Wrong direction
      expect(
        filter.matches(createTx(
          accountIndex: 0,
          incoming: false,
          height: 200,
        )),
        isFalse,
      );

      // Height too low
      expect(
        filter.matches(createTx(
          accountIndex: 0,
          incoming: true,
          height: 50,
        )),
        isFalse,
      );

      // Pending not included
      expect(
        filter.matches(createTx(
          accountIndex: 0,
          incoming: true,
          height: null,
        )),
        isFalse,
      );
    });
  });
}
