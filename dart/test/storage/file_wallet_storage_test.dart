import 'dart:io';
import 'dart:typed_data';

import 'package:monero_dart/monero_dart.dart';
import 'package:test/test.dart';

void main() {
  test('FileWalletStorage round-trip basics', () async {
    final dir = await Directory.systemTemp.createTemp('monero_dart_wallet_');
    try {
      final path = '${dir.path}/test_wallet';
      final storage = FileWalletStorage();

      await storage.open(path, 'pw', create: true);

      await storage.setSyncHeight(123);
      expect(await storage.getSyncHeight(), 123);

      final keyImage = Uint8List.fromList(List<int>.generate(32, (i) => i));
      final publicKey = Uint8List.fromList(List<int>.generate(32, (i) => 255 - i));
      final txHash = Uint8List.fromList(List<int>.generate(32, (i) => (i * 3) & 0xff));

      await storage.saveOutput(
        StoredOutput(
          keyImage: keyImage,
          publicKey: publicKey,
          amount: BigInt.from(42),
          globalIndex: 7,
          txHash: txHash,
          localIndex: 0,
          height: 10,
          accountIndex: 0,
          subaddressIndex: 0,
          spent: false,
          frozen: false,
          unlockTime: 0,
        ),
      );

      final outputs = await storage.getOutputs();
      expect(outputs.length, 1);
      expect(outputs.single.amount, BigInt.from(42));

      await storage.saveTransaction(
        StoredTransaction(
          hash: txHash,
          height: null,
          timestamp: 1,
          fee: BigInt.from(2),
          incoming: true,
          accountIndex: 0,
          subaddressIndices: const [0],
          amount: BigInt.from(40),
          paymentId: null,
          note: null,
        ),
      );

      await storage.setTxNote(txHash, 'note');
      expect(await storage.getTxNote(txHash), 'note');

      final id = await storage.addAddressBookEntry(
        const AddressBookEntry(
          address: '4xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
          description: 'desc',
        ),
      );
      expect(id, greaterThan(0));

      await storage.close();

      final storage2 = FileWalletStorage();
      await storage2.open(path, 'pw');
      expect(await storage2.getSyncHeight(), 123);
      expect((await storage2.getOutputs()).length, 1);
      expect((await storage2.getTransactions()).length, 1);
      expect(await storage2.getTxNote(txHash), 'note');
      expect((await storage2.getAddressBook()).length, 1);
      await storage2.close();
    } finally {
      await dir.delete(recursive: true);
    }
  });

  test('Address book CRUD operations', () async {
    final dir = await Directory.systemTemp.createTemp('monero_dart_wallet_');
    try {
      final path = '${dir.path}/test_wallet';
      final storage = FileWalletStorage();
      await storage.open(path, 'pw', create: true);

      // Add multiple entries
      final id1 = await storage.addAddressBookEntry(
        const AddressBookEntry(
          address: '4Alice111111111111111111111111111111111111111111111111111111',
          description: 'Alice',
        ),
      );
      final id2 = await storage.addAddressBookEntry(
        const AddressBookEntry(
          address: '4Bob22222222222222222222222222222222222222222222222222222222',
          description: 'Bob',
          paymentId: 'abc123',
        ),
      );

      expect(id1, greaterThan(0));
      expect(id2, greaterThan(id1));

      // Get all
      var entries = await storage.getAddressBook();
      expect(entries.length, 2);
      expect(entries[0].description, 'Alice');
      expect(entries[1].description, 'Bob');
      expect(entries[1].paymentId, 'abc123');

      // Get by ID
      final entry = await storage.getAddressBookEntry(id2);
      expect(entry, isNotNull);
      expect(entry!.description, 'Bob');

      // Update
      await storage.updateAddressBookEntry(AddressBookEntry(
        id: id1,
        address: '4Alice111111111111111111111111111111111111111111111111111111',
        description: 'Alice Updated',
      ));
      entries = await storage.getAddressBook();
      expect(entries[0].description, 'Alice Updated');

      // Delete
      await storage.deleteAddressBookEntry(id1);
      entries = await storage.getAddressBook();
      expect(entries.length, 1);
      expect(entries[0].description, 'Bob');

      await storage.close();
    } finally {
      await dir.delete(recursive: true);
    }
  });

  test('Transaction notes CRUD operations', () async {
    final dir = await Directory.systemTemp.createTemp('monero_dart_wallet_');
    try {
      final path = '${dir.path}/test_wallet';
      final storage = FileWalletStorage();
      await storage.open(path, 'pw', create: true);

      final txHash1 = Uint8List.fromList(List<int>.generate(32, (i) => i));
      final txHash2 = Uint8List.fromList(List<int>.generate(32, (i) => 255 - i));

      // Set notes
      await storage.setTxNote(txHash1, 'Payment for coffee');
      await storage.setTxNote(txHash2, 'Rent payment');

      // Get individual notes
      expect(await storage.getTxNote(txHash1), 'Payment for coffee');
      expect(await storage.getTxNote(txHash2), 'Rent payment');

      // Get all notes
      final allNotes = await storage.getAllTxNotes();
      expect(allNotes.length, 2);

      // Update note
      await storage.setTxNote(txHash1, 'Updated note');
      expect(await storage.getTxNote(txHash1), 'Updated note');

      // Delete note
      await storage.deleteTxNote(txHash1);
      expect(await storage.getTxNote(txHash1), isNull);
      expect((await storage.getAllTxNotes()).length, 1);

      await storage.close();
    } finally {
      await dir.delete(recursive: true);
    }
  });

  test('Freeze and thaw outputs', () async {
    final dir = await Directory.systemTemp.createTemp('monero_dart_wallet_');
    try {
      final path = '${dir.path}/test_wallet';
      final storage = FileWalletStorage();
      await storage.open(path, 'pw', create: true);

      final keyImage = Uint8List.fromList(List<int>.generate(32, (i) => i));
      final publicKey = Uint8List.fromList(List<int>.generate(32, (i) => 255 - i));
      final txHash = Uint8List.fromList(List<int>.generate(32, (i) => (i * 3) & 0xff));

      await storage.saveOutput(
        StoredOutput(
          keyImage: keyImage,
          publicKey: publicKey,
          amount: BigInt.from(1000),
          globalIndex: 100,
          txHash: txHash,
          localIndex: 0,
          height: 500,
          accountIndex: 0,
          subaddressIndex: 0,
          spent: false,
          frozen: false,
          unlockTime: 0,
        ),
      );

      // Initially not frozen
      var output = await storage.getOutput(keyImage);
      expect(output!.frozen, isFalse);

      // Freeze
      await storage.freezeOutput(keyImage);
      output = await storage.getOutput(keyImage);
      expect(output!.frozen, isTrue);

      // Thaw
      await storage.thawOutput(keyImage);
      output = await storage.getOutput(keyImage);
      expect(output!.frozen, isFalse);

      await storage.close();
    } finally {
      await dir.delete(recursive: true);
    }
  });
}
