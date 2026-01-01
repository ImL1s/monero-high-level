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
}
