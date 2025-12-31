/// Wallet Storage Interface
///
/// Handles persistence of wallet data with encryption.
library;

import 'dart:typed_data';

/// Abstract wallet storage interface.
abstract class WalletStorage {
  /// Open or create wallet storage
  Future<void> open(String path, String password, {bool create = false});

  /// Close storage
  Future<void> close();

  /// Check if storage is open
  bool isOpen();

  /// Change encryption password
  Future<void> changePassword(String oldPassword, String newPassword);

  // Keys
  Future<void> saveKeys(EncryptedKeys keys);
  Future<EncryptedKeys?> loadKeys();
  Future<bool> hasKeys();

  // Sync state
  Future<int> getSyncHeight();
  Future<void> setSyncHeight(int height);
  Stream<int> observeSyncHeight();

  // Outputs
  Future<void> saveOutput(StoredOutput output);
  Future<List<StoredOutput>> getOutputs({bool? spent});
  Future<StoredOutput?> getOutput(Uint8List keyImage);
  Future<void> markOutputSpent(Uint8List keyImage, Uint8List spendingTxHash);
  Future<void> deleteOutput(Uint8List keyImage);

  // Transactions
  Future<void> saveTransaction(StoredTransaction tx);
  Future<StoredTransaction?> getTransaction(Uint8List hash);
  Future<List<StoredTransaction>> getTransactions({int? accountIndex});
  Future<void> updateTransactionHeight(Uint8List hash, int height);

  // Accounts & Subaddresses
  Future<int> getAccountCount();
  Future<int> addAccount(String label);
  Future<void> setAccountLabel(int index, String label);
  Future<int> getSubaddressCount(int accountIndex);
  Future<int> addSubaddress(int accountIndex, String label);
  Future<void> setSubaddressLabel(int accountIndex, int addressIndex, String label);

  // Address book
  Future<int> addAddressBookEntry(AddressBookEntry entry);
  Future<List<AddressBookEntry>> getAddressBook();
  Future<void> deleteAddressBookEntry(int id);

  // Transaction notes
  Future<void> setTxNote(Uint8List txHash, String note);
  Future<String?> getTxNote(Uint8List txHash);
}

/// Encrypted keys container
class EncryptedKeys {
  final Uint8List encryptedSpendKey;
  final Uint8List encryptedViewKey;
  final Uint8List publicSpendKey;
  final Uint8List publicViewKey;
  final Uint8List salt;
  final Uint8List nonce;

  const EncryptedKeys({
    required this.encryptedSpendKey,
    required this.encryptedViewKey,
    required this.publicSpendKey,
    required this.publicViewKey,
    required this.salt,
    required this.nonce,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is EncryptedKeys &&
          _listEquals(publicSpendKey, other.publicSpendKey) &&
          _listEquals(publicViewKey, other.publicViewKey);

  @override
  int get hashCode => Object.hash(
        Object.hashAll(publicSpendKey),
        Object.hashAll(publicViewKey),
      );
}

/// Stored output (UTXO)
class StoredOutput {
  final Uint8List keyImage;
  final Uint8List publicKey;
  final BigInt amount;
  final int globalIndex;
  final Uint8List txHash;
  final int localIndex;
  final int height;
  final int accountIndex;
  final int subaddressIndex;
  final bool spent;
  final Uint8List? spendingTxHash;
  final bool frozen;
  final int unlockTime;

  const StoredOutput({
    required this.keyImage,
    required this.publicKey,
    required this.amount,
    required this.globalIndex,
    required this.txHash,
    required this.localIndex,
    required this.height,
    required this.accountIndex,
    required this.subaddressIndex,
    required this.spent,
    this.spendingTxHash,
    required this.frozen,
    required this.unlockTime,
  });

  bool get isUnlocked {
    if (unlockTime == 0) return true;
    // Simplified: real implementation needs current height
    return false;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is StoredOutput && _listEquals(keyImage, other.keyImage);

  @override
  int get hashCode => Object.hashAll(keyImage);
}

/// Stored transaction
class StoredTransaction {
  final Uint8List hash;
  final int? height; // null if in mempool
  final int timestamp;
  final BigInt fee;
  final bool incoming;
  final int accountIndex;
  final List<int> subaddressIndices;
  final BigInt amount;
  final Uint8List? paymentId;
  final String? note;

  const StoredTransaction({
    required this.hash,
    this.height,
    required this.timestamp,
    required this.fee,
    required this.incoming,
    required this.accountIndex,
    required this.subaddressIndices,
    required this.amount,
    this.paymentId,
    this.note,
  });

  bool get isConfirmed => height != null;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is StoredTransaction && _listEquals(hash, other.hash);

  @override
  int get hashCode => Object.hashAll(hash);
}

/// Address book entry
class AddressBookEntry {
  final int id;
  final String address;
  final String? paymentId;
  final String description;

  const AddressBookEntry({
    this.id = 0,
    required this.address,
    this.paymentId,
    required this.description,
  });
}

// Utility for list comparison
bool _listEquals(Uint8List a, Uint8List b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
