library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import '../crypto/chacha20.dart';
import 'wallet_storage.dart';

/// File-based wallet storage implementation.
///
/// This is a minimal local storage layer that persists wallet state to disk.
/// Encryption is intentionally deferred to D5.2.
class FileWalletStorage implements WalletStorage {
  static const String walletExtension = '.wallet';

  final StreamController<int> _syncHeightController =
      StreamController<int>.broadcast();

  bool _open = false;
  late String _path;
  late String _password;
  int _syncHeight = 0;
  EncryptedKeys? _keys;

  final Map<String, StoredOutput> _outputsByKeyImage = {};
  final Map<String, StoredTransaction> _txByHash = {};

  final List<_StoredAccount> _accounts = [];

  int _nextAddressBookId = 1;
  final Map<int, AddressBookEntry> _addressBookById = {};

  final Map<String, String> _txNotesByHash = {};

  @override
  Future<void> open(String path, String password, {bool create = false}) async {
    final walletFile = File(_walletFilePath(path));
    _path = walletFile.path;
    _password = password;

    if (create) {
      if (walletFile.existsSync()) {
        throw StateError('Wallet already exists at: ${walletFile.path}');
      }
      _resetState();
      _ensureDefaultAccount();
      _open = true;
      await _persist();
      _syncHeightController.add(_syncHeight);
      return;
    }

    if (!walletFile.existsSync()) {
      throw StateError('Wallet not found at: ${walletFile.path}');
    }

    final jsonMap = await _readEncrypted(walletFile, password);
    _loadFromJson(jsonMap);
    _open = true;
    _syncHeightController.add(_syncHeight);
  }

  @override
  Future<void> close() async {
    if (!_open) return;
    await _persist();
    _open = false;
  }

  @override
  bool isOpen() => _open;

  @override
  Future<void> changePassword(String oldPassword, String newPassword) async {
    _requireOpen();
    if (oldPassword != _password) {
      throw AuthenticationException('Old password is incorrect');
    }
    _password = newPassword;
    await _persist();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Keys
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<void> saveKeys(EncryptedKeys keys) async {
    _requireOpen();
    _keys = keys;
    await _persist();
  }

  @override
  Future<EncryptedKeys?> loadKeys() async {
    _requireOpen();
    return _keys;
  }

  @override
  Future<bool> hasKeys() async {
    _requireOpen();
    return _keys != null;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Sync state
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<int> getSyncHeight() async {
    _requireOpen();
    return _syncHeight;
  }

  @override
  Future<void> setSyncHeight(int height) async {
    _requireOpen();
    if (height == _syncHeight) return;
    _syncHeight = height;
    _syncHeightController.add(_syncHeight);
    await _persist();
  }

  @override
  Stream<int> observeSyncHeight() {
    return _syncHeightController.stream;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Outputs
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<void> saveOutput(StoredOutput output) async {
    _requireOpen();
    _outputsByKeyImage[_bytesToHex(output.keyImage)] = output;
    await _persist();
  }

  @override
  Future<List<StoredOutput>> getOutputs({bool? spent}) async {
    _requireOpen();
    final outputs = _outputsByKeyImage.values.toList(growable: false);
    if (spent == null) return outputs;
    return outputs.where((o) => o.spent == spent).toList(growable: false);
  }

  @override
  Future<StoredOutput?> getOutput(Uint8List keyImage) async {
    _requireOpen();
    return _outputsByKeyImage[_bytesToHex(keyImage)];
  }

  @override
  Future<void> markOutputSpent(Uint8List keyImage, Uint8List spendingTxHash) async {
    _requireOpen();
    final key = _bytesToHex(keyImage);
    final existing = _outputsByKeyImage[key];
    if (existing == null) return;
    _outputsByKeyImage[key] = StoredOutput(
      keyImage: existing.keyImage,
      publicKey: existing.publicKey,
      amount: existing.amount,
      globalIndex: existing.globalIndex,
      txHash: existing.txHash,
      localIndex: existing.localIndex,
      height: existing.height,
      accountIndex: existing.accountIndex,
      subaddressIndex: existing.subaddressIndex,
      spent: true,
      spendingTxHash: spendingTxHash,
      frozen: existing.frozen,
      unlockTime: existing.unlockTime,
    );
    await _persist();
  }

  @override
  Future<void> deleteOutput(Uint8List keyImage) async {
    _requireOpen();
    _outputsByKeyImage.remove(_bytesToHex(keyImage));
    await _persist();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Transactions
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<void> saveTransaction(StoredTransaction tx) async {
    _requireOpen();
    _txByHash[_bytesToHex(tx.hash)] = tx;
    await _persist();
  }

  @override
  Future<StoredTransaction?> getTransaction(Uint8List hash) async {
    _requireOpen();
    return _txByHash[_bytesToHex(hash)];
  }

  @override
  Future<List<StoredTransaction>> getTransactions({int? accountIndex}) async {
    _requireOpen();
    final txs = _txByHash.values.toList(growable: false);
    if (accountIndex == null) return txs;
    return txs
        .where((t) => t.accountIndex == accountIndex)
        .toList(growable: false);
  }

  @override
  Future<void> updateTransactionHeight(Uint8List hash, int height) async {
    _requireOpen();
    final key = _bytesToHex(hash);
    final existing = _txByHash[key];
    if (existing == null) return;
    _txByHash[key] = StoredTransaction(
      hash: existing.hash,
      height: height,
      timestamp: existing.timestamp,
      fee: existing.fee,
      incoming: existing.incoming,
      accountIndex: existing.accountIndex,
      subaddressIndices: existing.subaddressIndices,
      amount: existing.amount,
      paymentId: existing.paymentId,
      note: existing.note,
    );
    await _persist();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Accounts & Subaddresses
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<int> getAccountCount() async {
    _requireOpen();
    return _accounts.length;
  }

  @override
  Future<int> addAccount(String label) async {
    _requireOpen();
    final index = _accounts.length;
    _accounts.add(
      _StoredAccount(
        label: label,
        subaddressLabels: [''],
      ),
    );
    await _persist();
    return index;
  }

  @override
  Future<void> setAccountLabel(int index, String label) async {
    _requireOpen();
    _requireAccount(index);
    _accounts[index] = _accounts[index].copyWith(label: label);
    await _persist();
  }

  @override
  Future<int> getSubaddressCount(int accountIndex) async {
    _requireOpen();
    _requireAccount(accountIndex);
    return _accounts[accountIndex].subaddressLabels.length;
  }

  @override
  Future<int> addSubaddress(int accountIndex, String label) async {
    _requireOpen();
    _requireAccount(accountIndex);
    final account = _accounts[accountIndex];
    final idx = account.subaddressLabels.length;
    final updated = account.copyWith(
      subaddressLabels: [...account.subaddressLabels, label],
    );
    _accounts[accountIndex] = updated;
    await _persist();
    return idx;
  }

  @override
  Future<void> setSubaddressLabel(
    int accountIndex,
    int addressIndex,
    String label,
  ) async {
    _requireOpen();
    _requireAccount(accountIndex);
    _requireSubaddress(accountIndex, addressIndex);
    final account = _accounts[accountIndex];
    final labels = [...account.subaddressLabels];
    labels[addressIndex] = label;
    _accounts[accountIndex] = account.copyWith(subaddressLabels: labels);
    await _persist();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Address book
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<int> addAddressBookEntry(AddressBookEntry entry) async {
    _requireOpen();
    final id = _nextAddressBookId++;
    _addressBookById[id] = AddressBookEntry(
      id: id,
      address: entry.address,
      paymentId: entry.paymentId,
      description: entry.description,
    );
    await _persist();
    return id;
  }

  @override
  Future<List<AddressBookEntry>> getAddressBook() async {
    _requireOpen();
    final ids = _addressBookById.keys.toList()..sort();
    return ids.map((id) => _addressBookById[id]!).toList(growable: false);
  }

  @override
  Future<void> deleteAddressBookEntry(int id) async {
    _requireOpen();
    _addressBookById.remove(id);
    await _persist();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Transaction notes
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<void> setTxNote(Uint8List txHash, String note) async {
    _requireOpen();
    _txNotesByHash[_bytesToHex(txHash)] = note;
    await _persist();
  }

  @override
  Future<String?> getTxNote(Uint8List txHash) async {
    _requireOpen();
    return _txNotesByHash[_bytesToHex(txHash)];
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Persistence
  // ─────────────────────────────────────────────────────────────────────────

  void _resetState() {
    _syncHeight = 0;
    _keys = null;
    _outputsByKeyImage.clear();
    _txByHash.clear();
    _accounts.clear();
    _addressBookById.clear();
    _txNotesByHash.clear();
    _nextAddressBookId = 1;
  }

  void _ensureDefaultAccount() {
    if (_accounts.isNotEmpty) return;
    _accounts.add(const _StoredAccount(label: 'Primary account', subaddressLabels: ['']));
  }

  Future<Map<String, Object?>> _readEncrypted(File file, String password) async {
    final encryptedBytes = await file.readAsBytes();

    Uint8List plainBytes;
    try {
      plainBytes = WalletEncryption.decrypt(password, encryptedBytes);
    } on AuthenticationException {
      throw StateError('Invalid password');
    } on FormatException {
      // Legacy unencrypted fallback: try reading as plain JSON
      final content = utf8.decode(encryptedBytes);
      final decoded = jsonDecode(content);
      if (decoded is! Map) {
        throw FormatException('Invalid wallet file format');
      }
      return decoded.cast<String, Object?>();
    }

    final decoded = jsonDecode(utf8.decode(plainBytes));
    if (decoded is! Map) {
      throw FormatException('Invalid wallet file format');
    }
    return decoded.cast<String, Object?>();
  }

  void _loadFromJson(Map<String, Object?> json) {
    _resetState();

    _syncHeight = (json['syncHeight'] as num?)?.toInt() ?? 0;

    final keysJson = json['keys'];
    if (keysJson is Map) {
      _keys = _encryptedKeysFromJson(keysJson.cast<String, Object?>());
    }

    final outputs = json['outputs'];
    if (outputs is List) {
      for (final item in outputs) {
        if (item is! Map) continue;
        final output = _storedOutputFromJson(item.cast<String, Object?>());
        _outputsByKeyImage[_bytesToHex(output.keyImage)] = output;
      }
    }

    final txs = json['transactions'];
    if (txs is List) {
      for (final item in txs) {
        if (item is! Map) continue;
        final tx = _storedTransactionFromJson(item.cast<String, Object?>());
        _txByHash[_bytesToHex(tx.hash)] = tx;
      }
    }

    final accounts = json['accounts'];
    if (accounts is List) {
      for (final item in accounts) {
        if (item is! Map) continue;
        _accounts.add(_storedAccountFromJson(item.cast<String, Object?>()));
      }
    }
    _ensureDefaultAccount();

    _nextAddressBookId = (json['nextAddressBookId'] as num?)?.toInt() ?? 1;

    final addressBook = json['addressBook'];
    if (addressBook is List) {
      for (final item in addressBook) {
        if (item is! Map) continue;
        final entry = _addressBookEntryFromJson(item.cast<String, Object?>());
        _addressBookById[entry.id] = entry;
        if (entry.id >= _nextAddressBookId) {
          _nextAddressBookId = entry.id + 1;
        }
      }
    }

    final notes = json['txNotes'];
    if (notes is Map) {
      for (final e in notes.entries) {
        if (e.key is String && e.value is String) {
          _txNotesByHash[e.key as String] = e.value as String;
        }
      }
    }
  }

  Future<void> _persist() async {
    final walletFile = File(_path);
    walletFile.parent.createSync(recursive: true);

    final addressBookIds = _addressBookById.keys.toList()..sort();

    final jsonMap = <String, Object?>{
      'version': 1,
      'syncHeight': _syncHeight,
      'keys': _keys != null ? _encryptedKeysToJson(_keys!) : null,
      'outputs': _outputsByKeyImage.values.map(_storedOutputToJson).toList(),
      'transactions': _txByHash.values.map(_storedTransactionToJson).toList(),
      'accounts': _accounts.map(_storedAccountToJson).toList(),
      'nextAddressBookId': _nextAddressBookId,
      'addressBook': addressBookIds
          .map((id) => _addressBookEntryToJson(_addressBookById[id]!))
          .toList(growable: false),
      'txNotes': Map<String, String>.from(_txNotesByHash),
    };

    final jsonString = const JsonEncoder.withIndent('  ').convert(jsonMap);
    final plainBytes = Uint8List.fromList(utf8.encode(jsonString));
    final encryptedBytes = WalletEncryption.encrypt(_password, plainBytes);

    final tmp = File('${walletFile.path}.tmp');
    await tmp.writeAsBytes(encryptedBytes);
    if (walletFile.existsSync()) {
      await walletFile.delete();
    }
    await tmp.rename(walletFile.path);
  }

  String _walletFilePath(String path) {
    if (path.endsWith(walletExtension)) return path;
    return '$path$walletExtension';
  }

  void _requireOpen() {
    if (!_open) {
      throw StateError('WalletStorage is not open');
    }
  }

  void _requireAccount(int index) {
    if (index < 0 || index >= _accounts.length) {
      throw RangeError.index(index, _accounts, 'accountIndex');
    }
  }

  void _requireSubaddress(int accountIndex, int addressIndex) {
    final count = _accounts[accountIndex].subaddressLabels.length;
    if (addressIndex < 0 || addressIndex >= count) {
      throw RangeError.index(addressIndex, _accounts[accountIndex].subaddressLabels, 'addressIndex');
    }
  }
}

class _StoredAccount {
  final String label;
  final List<String> subaddressLabels;

  const _StoredAccount({required this.label, required this.subaddressLabels});

  _StoredAccount copyWith({String? label, List<String>? subaddressLabels}) =>
      _StoredAccount(
        label: label ?? this.label,
        subaddressLabels: subaddressLabels ?? this.subaddressLabels,
      );
}

Map<String, Object?> _encryptedKeysToJson(EncryptedKeys keys) => <String, Object?>{
      'encryptedSpendKey': _bytesToHex(keys.encryptedSpendKey),
      'encryptedViewKey': _bytesToHex(keys.encryptedViewKey),
      'publicSpendKey': _bytesToHex(keys.publicSpendKey),
      'publicViewKey': _bytesToHex(keys.publicViewKey),
      'salt': _bytesToHex(keys.salt),
      'nonce': _bytesToHex(keys.nonce),
    };

EncryptedKeys _encryptedKeysFromJson(Map<String, Object?> json) => EncryptedKeys(
      encryptedSpendKey: _hexToBytes(json['encryptedSpendKey'] as String),
      encryptedViewKey: _hexToBytes(json['encryptedViewKey'] as String),
      publicSpendKey: _hexToBytes(json['publicSpendKey'] as String),
      publicViewKey: _hexToBytes(json['publicViewKey'] as String),
      salt: _hexToBytes(json['salt'] as String),
      nonce: _hexToBytes(json['nonce'] as String),
    );

Map<String, Object?> _storedOutputToJson(StoredOutput o) => <String, Object?>{
      'keyImage': _bytesToHex(o.keyImage),
      'publicKey': _bytesToHex(o.publicKey),
      'amount': o.amount.toString(),
      'globalIndex': o.globalIndex,
      'txHash': _bytesToHex(o.txHash),
      'localIndex': o.localIndex,
      'height': o.height,
      'accountIndex': o.accountIndex,
      'subaddressIndex': o.subaddressIndex,
      'spent': o.spent,
      'spendingTxHash': o.spendingTxHash != null ? _bytesToHex(o.spendingTxHash!) : null,
      'frozen': o.frozen,
      'unlockTime': o.unlockTime,
    };

StoredOutput _storedOutputFromJson(Map<String, Object?> json) => StoredOutput(
      keyImage: _hexToBytes(json['keyImage'] as String),
      publicKey: _hexToBytes(json['publicKey'] as String),
      amount: BigInt.parse(json['amount'] as String),
      globalIndex: (json['globalIndex'] as num).toInt(),
      txHash: _hexToBytes(json['txHash'] as String),
      localIndex: (json['localIndex'] as num).toInt(),
      height: (json['height'] as num).toInt(),
      accountIndex: (json['accountIndex'] as num).toInt(),
      subaddressIndex: (json['subaddressIndex'] as num).toInt(),
      spent: json['spent'] as bool,
      spendingTxHash: json['spendingTxHash'] == null
          ? null
          : _hexToBytes(json['spendingTxHash'] as String),
      frozen: json['frozen'] as bool,
      unlockTime: (json['unlockTime'] as num).toInt(),
    );

Map<String, Object?> _storedTransactionToJson(StoredTransaction t) => <String, Object?>{
      'hash': _bytesToHex(t.hash),
      'height': t.height,
      'timestamp': t.timestamp,
      'fee': t.fee.toString(),
      'incoming': t.incoming,
      'accountIndex': t.accountIndex,
      'subaddressIndices': t.subaddressIndices,
      'amount': t.amount.toString(),
      'paymentId': t.paymentId != null ? _bytesToHex(t.paymentId!) : null,
      'note': t.note,
    };

StoredTransaction _storedTransactionFromJson(Map<String, Object?> json) => StoredTransaction(
      hash: _hexToBytes(json['hash'] as String),
      height: (json['height'] as num?)?.toInt(),
      timestamp: (json['timestamp'] as num).toInt(),
      fee: BigInt.parse(json['fee'] as String),
      incoming: json['incoming'] as bool,
      accountIndex: (json['accountIndex'] as num).toInt(),
      subaddressIndices:
          (json['subaddressIndices'] as List).map((e) => (e as num).toInt()).toList(),
      amount: BigInt.parse(json['amount'] as String),
      paymentId: json['paymentId'] == null ? null : _hexToBytes(json['paymentId'] as String),
      note: json['note'] as String?,
    );

Map<String, Object?> _storedAccountToJson(_StoredAccount a) => <String, Object?>{
      'label': a.label,
      'subaddressLabels': a.subaddressLabels,
    };

_StoredAccount _storedAccountFromJson(Map<String, Object?> json) => _StoredAccount(
      label: json['label'] as String? ?? '',
      subaddressLabels: (json['subaddressLabels'] as List?)
              ?.map((e) => e as String)
              .toList(growable: false) ??
          const [''],
    );

Map<String, Object?> _addressBookEntryToJson(AddressBookEntry e) => <String, Object?>{
      'id': e.id,
      'address': e.address,
      'paymentId': e.paymentId,
      'description': e.description,
    };

AddressBookEntry _addressBookEntryFromJson(Map<String, Object?> json) => AddressBookEntry(
      id: (json['id'] as num?)?.toInt() ?? 0,
      address: json['address'] as String,
      paymentId: json['paymentId'] as String?,
      description: json['description'] as String,
    );

String _bytesToHex(Uint8List bytes) {
  final sb = StringBuffer();
  for (final b in bytes) {
    sb.write(b.toRadixString(16).padLeft(2, '0'));
  }
  return sb.toString();
}

Uint8List _hexToBytes(String hex) {
  final normalized = hex.length.isOdd ? '0$hex' : hex;
  final out = Uint8List(normalized.length ~/ 2);
  for (var i = 0; i < out.length; i++) {
    final byteHex = normalized.substring(i * 2, i * 2 + 2);
    out[i] = int.parse(byteHex, radix: 16);
  }
  return out;
}
