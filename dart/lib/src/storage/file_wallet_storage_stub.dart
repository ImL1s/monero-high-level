library;

import 'dart:typed_data';

import 'wallet_storage.dart';

/// File-based wallet storage implementation.
///
/// This implementation is only supported on platforms with `dart:io`.
class FileWalletStorage implements WalletStorage {
  @override
  Future<void> open(String path, String password, {bool create = false}) =>
      Future.error(
        UnsupportedError('FileWalletStorage is only supported on IO platforms'),
      );

  @override
  Future<void> close() => Future.value();

  @override
  bool isOpen() => false;

  @override
  Future<void> changePassword(String oldPassword, String newPassword) =>
      Future.error(
        UnsupportedError('FileWalletStorage is only supported on IO platforms'),
      );

  @override
  Future<void> saveKeys(EncryptedKeys keys) => Future.error(
        UnsupportedError('FileWalletStorage is only supported on IO platforms'),
      );

  @override
  Future<EncryptedKeys?> loadKeys() => Future.value(null);

  @override
  Future<bool> hasKeys() => Future.value(false);

  @override
  Future<int> getSyncHeight() => Future.value(0);

  @override
  Future<void> setSyncHeight(int height) => Future.value();

  @override
  Stream<int> observeSyncHeight() => const Stream<int>.empty();

  @override
  Future<void> saveOutput(StoredOutput output) => Future.error(
        UnsupportedError('FileWalletStorage is only supported on IO platforms'),
      );

  @override
  Future<List<StoredOutput>> getOutputs({bool? spent}) => Future.value(const []);

  @override
  Future<StoredOutput?> getOutput(Uint8List keyImage) => Future.value(null);

  @override
  Future<void> markOutputSpent(Uint8List keyImage, Uint8List spendingTxHash) =>
      Future.error(
        UnsupportedError('FileWalletStorage is only supported on IO platforms'),
      );

  @override
  Future<void> deleteOutput(Uint8List keyImage) => Future.value();

  @override
  Future<void> freezeOutput(Uint8List keyImage) => Future.value();

  @override
  Future<void> thawOutput(Uint8List keyImage) => Future.value();

  @override
  Future<ExportedOutputs> exportOutputs({bool all = false}) =>
      Future.value(const ExportedOutputs(outputs: []));

  @override
  Future<int> importOutputs(ExportedOutputs data) => Future.value(0);

  @override
  Future<ExportedKeyImages> exportKeyImages({bool all = false}) =>
      Future.value(const ExportedKeyImages(keyImages: []));

  @override
  Future<KeyImageImportResult> importKeyImages(ExportedKeyImages data) =>
      Future.value(KeyImageImportResult(
        imported: 0,
        spent: BigInt.zero,
        unspent: BigInt.zero,
      ));

  @override
  Future<void> saveTransaction(StoredTransaction tx) => Future.error(
        UnsupportedError('FileWalletStorage is only supported on IO platforms'),
      );

  @override
  Future<StoredTransaction?> getTransaction(Uint8List hash) => Future.value(null);

  @override
  Future<List<StoredTransaction>> getTransactions({int? accountIndex}) =>
      Future.value(const []);

  @override
  Future<void> updateTransactionHeight(Uint8List hash, int height) => Future.value();

  @override
  Future<int> getAccountCount() => Future.value(0);

  @override
  Future<int> addAccount(String label) => Future.value(0);

  @override
  Future<void> setAccountLabel(int index, String label) => Future.value();

  @override
  Future<int> getSubaddressCount(int accountIndex) => Future.value(0);

  @override
  Future<int> addSubaddress(int accountIndex, String label) => Future.value(0);

  @override
  Future<void> setSubaddressLabel(int accountIndex, int addressIndex, String label) =>
      Future.value();

  @override
  Future<int> addAddressBookEntry(AddressBookEntry entry) => Future.value(0);

  @override
  Future<List<AddressBookEntry>> getAddressBook() => Future.value(const []);

  @override
  Future<AddressBookEntry?> getAddressBookEntry(int id) => Future.value(null);

  @override
  Future<void> updateAddressBookEntry(AddressBookEntry entry) => Future.value();

  @override
  Future<void> deleteAddressBookEntry(int id) => Future.value();

  @override
  Future<void> setTxNote(Uint8List txHash, String note) => Future.value();

  @override
  Future<String?> getTxNote(Uint8List txHash) => Future.value(null);

  @override
  Future<void> deleteTxNote(Uint8List txHash) => Future.value();

  @override
  Future<Map<String, String>> getAllTxNotes() => Future.value(const {});
}
