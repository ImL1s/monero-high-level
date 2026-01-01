/// Wallet Lifecycle and Account Management
///
/// Provides extended wallet management including:
/// - Wallet creation, restoration, and lifecycle
/// - Account and subaddress management
/// - Balance queries by account
library;

import 'dart:typed_data';

import 'wallet.dart';

/// Wallet type indicating available operations.
enum WalletType {
  /// Full wallet with spend key - can sign and send transactions
  full,

  /// View-only wallet - can see incoming transactions but cannot spend
  viewOnly,

  /// Offline wallet - has spend key but no network access, for cold signing
  offline,
}

/// Account information within a wallet.
class AccountInfo {
  /// Account index (0-based)
  final int index;

  /// Account label
  final String label;

  /// Primary address for this account
  final String primaryAddress;

  /// Total balance (confirmed + unconfirmed)
  final BigInt balance;

  /// Unlocked (spendable) balance
  final BigInt unlockedBalance;

  /// Number of subaddresses in this account
  final int subaddressCount;

  const AccountInfo({
    required this.index,
    required this.label,
    required this.primaryAddress,
    required this.balance,
    required this.unlockedBalance,
    required this.subaddressCount,
  });

  /// Create an empty account with default values.
  factory AccountInfo.empty(int index) => AccountInfo(
        index: index,
        label: index == 0 ? 'Primary account' : 'Account #$index',
        primaryAddress: '',
        balance: BigInt.zero,
        unlockedBalance: BigInt.zero,
        subaddressCount: 1,
      );
}

/// Result of wallet creation.
class WalletCreationResult {
  /// The created wallet instance
  final MoneroWallet wallet;

  /// 25-word mnemonic seed
  final List<String> mnemonic;

  /// Primary address of the wallet
  final String primaryAddress;

  const WalletCreationResult({
    required this.wallet,
    required this.mnemonic,
    required this.primaryAddress,
  });
}

/// Wallet-related exceptions.
sealed class WalletException implements Exception {
  final String message;
  const WalletException(this.message);

  @override
  String toString() => 'WalletException: $message';
}

class WalletNotFound extends WalletException {
  WalletNotFound(String path) : super('Wallet not found at: $path');
}

class InvalidPassword extends WalletException {
  const InvalidPassword() : super('Invalid wallet password');
}

class InvalidMnemonic extends WalletException {
  InvalidMnemonic(String reason) : super('Invalid mnemonic: $reason');
}

class ViewOnlyWalletError extends WalletException {
  ViewOnlyWalletError(String operation)
      : super('Cannot $operation with view-only wallet');
}

class OfflineWalletError extends WalletException {
  OfflineWalletError(String operation)
      : super('Cannot $operation with offline wallet');
}

class AccountNotFound extends WalletException {
  AccountNotFound(int index) : super('Account not found: $index');
}

class SubaddressNotFound extends WalletException {
  SubaddressNotFound(int account, int address)
      : super('Subaddress not found: $account/$address');
}

class StorageError extends WalletException {
  final Object? cause;
  StorageError(super.message, [this.cause]);

  @override
  String toString() =>
      cause != null ? 'StorageError: $message ($cause)' : super.toString();
}

/// Extended wallet interface with lifecycle management.
///
/// Provides wallet creation, restoration, and lifecycle operations.
abstract class WalletManager {
  // ─────────────────────────────────────────────────────────────────────────
  // Wallet Lifecycle
  // ─────────────────────────────────────────────────────────────────────────

  /// Create a new wallet with a fresh seed.
  ///
  /// Returns a [WalletCreationResult] containing the wallet and mnemonic.
  Future<WalletCreationResult> createWallet(WalletConfig config);

  /// Restore wallet from mnemonic seed.
  ///
  /// [mnemonic] should be a 25-word seed.
  /// [restoreHeight] is the block height to start scanning from.
  Future<MoneroWallet> restoreFromMnemonic({
    required WalletConfig config,
    required List<String> mnemonic,
    int restoreHeight = 0,
  });

  /// Restore wallet from keys (view-only or full).
  ///
  /// [spendKey] is null for view-only wallets.
  Future<MoneroWallet> restoreFromKeys({
    required WalletConfig config,
    required String address,
    required String viewKey,
    String? spendKey,
    int restoreHeight = 0,
  });

  /// Open an existing wallet file.
  ///
  /// Throws [WalletNotFound] if wallet doesn't exist.
  /// Throws [InvalidPassword] if password is wrong.
  Future<MoneroWallet> openWallet(WalletConfig config);

  /// Close wallet and release resources.
  ///
  /// Saves state before closing.
  Future<void> closeWallet(MoneroWallet wallet);

  /// Check if a wallet file exists at the given path.
  bool walletExists(String path);

  // ─────────────────────────────────────────────────────────────────────────
  // Wallet Info
  // ─────────────────────────────────────────────────────────────────────────

  /// Get wallet type (full, view-only, offline).
  WalletType getWalletType(MoneroWallet wallet);

  /// Get the mnemonic seed (only for full wallets).
  ///
  /// Throws [ViewOnlyWalletError] if wallet is view-only.
  Future<List<String>> getMnemonic(MoneroWallet wallet);

  /// Get wallet restore height.
  int getRestoreHeight(MoneroWallet wallet);

  /// Set wallet restore height (for re-scanning).
  Future<void> setRestoreHeight(MoneroWallet wallet, int height);
}

/// Account management operations.
abstract class AccountManager {
  /// Get all accounts in the wallet.
  Future<List<AccountInfo>> getAccounts(MoneroWallet wallet);

  /// Get a specific account.
  ///
  /// Throws [AccountNotFound] if account doesn't exist.
  Future<AccountInfo> getAccount(MoneroWallet wallet, int accountIndex);

  /// Create a new account.
  ///
  /// [label] is an optional label for the account.
  Future<AccountInfo> createAccount(MoneroWallet wallet, {String label = ''});

  /// Set account label.
  Future<void> setAccountLabel(
    MoneroWallet wallet,
    int accountIndex,
    String label,
  );

  /// Get balance for a specific account.
  Future<WalletBalance> getAccountBalance(MoneroWallet wallet, int accountIndex);

  /// Get total wallet balance (sum of all accounts).
  Future<WalletBalance> getTotalBalance(MoneroWallet wallet);
}

/// Subaddress management operations.
abstract class SubaddressManager {
  /// Get all subaddresses for an account.
  Future<List<SubaddressInfo>> getSubaddresses(
    MoneroWallet wallet,
    int accountIndex,
  );

  /// Create a new subaddress.
  ///
  /// [accountIndex] is the account to create subaddress in.
  /// [label] is an optional label.
  Future<SubaddressInfo> createSubaddress(
    MoneroWallet wallet,
    int accountIndex, {
    String label = '',
  });

  /// Set subaddress label.
  Future<void> setSubaddressLabel(
    MoneroWallet wallet,
    int accountIndex,
    int addressIndex,
    String label,
  );

  /// Get subaddress by indices.
  ///
  /// Throws [SubaddressNotFound] if subaddress doesn't exist.
  Future<SubaddressInfo> getSubaddress(
    MoneroWallet wallet,
    int accountIndex,
    int addressIndex,
  );

  /// Find which account/subaddress an address belongs to.
  ///
  /// Returns `(accountIndex, addressIndex)` or null if not found.
  Future<(int, int)?> findSubaddress(MoneroWallet wallet, String address);

  /// Get the number of subaddresses in an account.
  Future<int> getSubaddressCount(MoneroWallet wallet, int accountIndex);
}

/// Subaddress index pair for convenience.
class SubaddressIndex {
  final int major;
  final int minor;

  const SubaddressIndex({required this.major, required this.minor});

  /// Main account, primary address.
  static const main = SubaddressIndex(major: 0, minor: 0);

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SubaddressIndex && major == other.major && minor == other.minor;

  @override
  int get hashCode => Object.hash(major, minor);

  @override
  String toString() => 'SubaddressIndex($major, $minor)';
}

/// Extended wallet balance with per-account breakdown.
class AccountBalance {
  final int accountIndex;
  final BigInt balance;
  final BigInt unlockedBalance;
  final BigInt pendingBalance;

  const AccountBalance({
    required this.accountIndex,
    required this.balance,
    required this.unlockedBalance,
    required this.pendingBalance,
  });

  static AccountBalance zero(int accountIndex) => AccountBalance(
        accountIndex: accountIndex,
        balance: BigInt.zero,
        unlockedBalance: BigInt.zero,
        pendingBalance: BigInt.zero,
      );
}

/// Output filter options for queries.
class OutputFilter {
  /// Include spent outputs
  final bool includeSpent;

  /// Include frozen outputs
  final bool includeFrozen;

  /// Filter by account index
  final int? accountIndex;

  /// Filter by subaddress index
  final int? subaddressIndex;

  /// Minimum amount
  final BigInt? minAmount;

  /// Maximum amount
  final BigInt? maxAmount;

  const OutputFilter({
    this.includeSpent = false,
    this.includeFrozen = true,
    this.accountIndex,
    this.subaddressIndex,
    this.minAmount,
    this.maxAmount,
  });

  static const all = OutputFilter(includeSpent: true, includeFrozen: true);
  static const spendable = OutputFilter(includeSpent: false, includeFrozen: false);
}

/// Transaction filter options for queries.
class TransactionFilter {
  /// Filter by account index
  final int? accountIndex;

  /// Filter by subaddress index
  final int? subaddressIndex;

  /// Include pending (unconfirmed) transactions
  final bool includePending;

  /// Only incoming transactions
  final bool? incoming;

  /// Minimum height
  final int? minHeight;

  /// Maximum height
  final int? maxHeight;

  /// Payment ID filter
  final String? paymentId;

  const TransactionFilter({
    this.accountIndex,
    this.subaddressIndex,
    this.includePending = true,
    this.incoming,
    this.minHeight,
    this.maxHeight,
    this.paymentId,
  });

  static const all = TransactionFilter();
  static const confirmed = TransactionFilter(includePending: false);
  static const incomingOnly = TransactionFilter(incoming: true);
  static const outgoingOnly = TransactionFilter(incoming: false);
}
