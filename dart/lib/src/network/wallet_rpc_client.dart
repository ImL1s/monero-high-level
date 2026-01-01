/// Monero Wallet RPC Client
///
/// Implements JSON-RPC 2.0 protocol for monero-wallet-rpc communication.
/// Supports retry with exponential backoff and circuit breaker pattern.
library wallet_rpc_client;

import 'dart:convert';

import 'package:dio/dio.dart';

import 'rpc_utils.dart';

class WalletRpcClient {
  final String host;
  final int port;
  final bool useSsl;
  final String? username;
  final String? password;
  final Duration timeout;
  final RetryConfig retryConfig;
  final CircuitBreaker? circuitBreaker;

  late final Dio _dio;
  late final String _baseUrl;

  WalletRpcClient({
    this.host = 'localhost',
    this.port = 18082,
    this.useSsl = false,
    this.username,
    this.password,
    this.timeout = const Duration(seconds: 30),
    this.retryConfig = const RetryConfig(),
    this.circuitBreaker,
  }) {
    final scheme = useSsl ? 'https' : 'http';
    _baseUrl = '$scheme://$host:$port';

    _dio = Dio(BaseOptions(
      baseUrl: _baseUrl,
      connectTimeout: timeout,
      receiveTimeout: timeout,
    ));

    _dio.interceptors.add(RetryInterceptor(dio: _dio, config: retryConfig));

    if (username != null && password != null) {
      _dio.interceptors.add(InterceptorsWrapper(
        onRequest: (options, handler) {
          final credentials = base64Encode(utf8.encode('$username:$password'));
          options.headers['Authorization'] = 'Basic $credentials';
          handler.next(options);
        },
      ));
    }
  }

  static BigInt _parseBigInt(dynamic value) {
    if (value == null) return BigInt.zero;
    if (value is BigInt) return value;
    if (value is int) return BigInt.from(value);
    if (value is num) return BigInt.from(value.toInt());
    return BigInt.tryParse('$value') ?? BigInt.zero;
  }

  Future<String> getTxKey(String txid) async {
    final result = await _jsonRpc('get_tx_key', {'txid': txid});
    return result['tx_key'] as String;
  }

  Future<CheckTxKeyResult> checkTxKey({
    required String txid,
    required String txKey,
    required String address,
  }) async {
    final result = await _jsonRpc('check_tx_key', {
      'txid': txid,
      'tx_key': txKey,
      'address': address,
    });
    return CheckTxKeyResult.fromJson(result);
  }

  Future<String> getTxProof({
    required String txid,
    required String address,
    String? message,
  }) async {
    final result = await _jsonRpc('get_tx_proof', {
      'txid': txid,
      'address': address,
      if (message != null) 'message': message,
    });
    return result['signature'] as String;
  }

  Future<CheckTxProofResult> checkTxProof({
    required String txid,
    required String address,
    required String signature,
    String? message,
  }) async {
    final result = await _jsonRpc('check_tx_proof', {
      'txid': txid,
      'address': address,
      'signature': signature,
      if (message != null) 'message': message,
    });
    return CheckTxProofResult.fromJson(result);
  }

  Future<String> getReserveProof({
    bool all = true,
    int? accountIndex,
    BigInt? amount,
    String? message,
  }) async {
    final result = await _jsonRpc('get_reserve_proof', {
      'all': all,
      if (!all && accountIndex != null) 'account_index': accountIndex,
      if (!all && amount != null) 'amount': amount.toString(),
      if (message != null) 'message': message,
    });
    return result['signature'] as String;
  }

  Future<CheckReserveProofResult> checkReserveProof({
    required String address,
    required String signature,
    String? message,
  }) async {
    final result = await _jsonRpc('check_reserve_proof', {
      'address': address,
      'signature': signature,
      if (message != null) 'message': message,
    });
    return CheckReserveProofResult.fromJson(result);
  }

  Future<String> sign(String data) async {
    final result = await _jsonRpc('sign', {'data': data});
    return result['signature'] as String;
  }

  Future<bool> verify({
    required String data,
    required String address,
    required String signature,
  }) async {
    final result = await _jsonRpc('verify', {
      'data': data,
      'address': address,
      'signature': signature,
    });
    return result['good'] as bool? ?? false;
  }

  Future<IsMultisigResult> isMultisig() async {
    final result = await _jsonRpc('is_multisig');
    return IsMultisigResult.fromJson(result);
  }

  Future<PrepareMultisigResult> prepareMultisig() async {
    final result = await _jsonRpc('prepare_multisig');
    return PrepareMultisigResult.fromJson(result);
  }

  Future<MakeMultisigResult> makeMultisig({
    required List<String> multisigInfo,
    required int threshold,
    String? password,
  }) async {
    final result = await _jsonRpc('make_multisig', {
      'multisig_info': multisigInfo,
      'threshold': threshold,
      if (password != null) 'password': password,
    });
    return MakeMultisigResult.fromJson(result);
  }

  Future<ExchangeMultisigKeysResult> exchangeMultisigKeys({
    required String password,
    required String multisigInfo,
    bool? forceUpdateUseWithCaution,
  }) async {
    final result = await _jsonRpc('exchange_multisig_keys', {
      'password': password,
      'multisig_info': multisigInfo,
      if (forceUpdateUseWithCaution != null)
        'force_update_use_with_caution': forceUpdateUseWithCaution,
    });
    return ExchangeMultisigKeysResult.fromJson(result);
  }

  Future<SignMultisigResult> signMultisig(String txDataHex) async {
    final result = await _jsonRpc('sign_multisig', {'tx_data_hex': txDataHex});
    return SignMultisigResult.fromJson(result);
  }

  Future<List<String>> getLanguages() async {
    final result = await _jsonRpc('get_languages');
    return (result['languages'] as List?)?.cast<String>() ?? const [];
  }

  Future<GetHeightResult> getHeight() async {
    final result = await _jsonRpc('get_height');
    return GetHeightResult.fromJson(result);
  }

  Future<GetBalanceResult> getBalance({
    int accountIndex = 0,
    List<int>? addressIndices,
    bool? allAccounts,
    bool? strict,
  }) async {
    final result = await _jsonRpc('get_balance', {
      'account_index': accountIndex,
      if (addressIndices != null) 'address_indices': addressIndices,
      if (allAccounts != null) 'all_accounts': allAccounts,
      if (strict != null) 'strict': strict,
    });
    return GetBalanceResult.fromJson(result);
  }

  Future<RefreshResult> refresh({int? startHeight}) async {
    final result = await _jsonRpc('refresh', {
      if (startHeight != null) 'start_height': startHeight,
    });
    return RefreshResult.fromJson(result);
  }

  Future<void> autoRefresh({bool enable = true, int? periodSeconds}) async {
    await _jsonRpc('auto_refresh', {
      'enable': enable,
      if (periodSeconds != null) 'period': periodSeconds,
    });
  }

  Future<void> store() async {
    await _jsonRpc('store');
  }

  Future<void> closeWallet() async {
    await _jsonRpc('close_wallet');
  }

  Future<void> changeWalletPassword({
    String? oldPassword,
    required String newPassword,
  }) async {
    await _jsonRpc('change_wallet_password', {
      if (oldPassword != null) 'old_password': oldPassword,
      'new_password': newPassword,
    });
  }

  Future<QueryKeyResult> queryKey(String keyType) async {
    final result = await _jsonRpc('query_key', {'key_type': keyType});
    return QueryKeyResult.fromJson(result);
  }

  Future<ValidateAddressResult> validateAddress({
    required String address,
    bool? anyNetType,
    bool? allowOpenalias,
  }) async {
    final result = await _jsonRpc('validate_address', {
      'address': address,
      if (anyNetType != null) 'any_net_type': anyNetType,
      if (allowOpenalias != null) 'allow_openalias': allowOpenalias,
    });
    return ValidateAddressResult.fromJson(result);
  }

  Future<MakeIntegratedAddressResult> makeIntegratedAddress({
    String? standardAddress,
    String? paymentId,
  }) async {
    final result = await _jsonRpc('make_integrated_address', {
      if (standardAddress != null) 'standard_address': standardAddress,
      if (paymentId != null) 'payment_id': paymentId,
    });
    return MakeIntegratedAddressResult.fromJson(result);
  }

  Future<SplitIntegratedAddressResult> splitIntegratedAddress({
    required String integratedAddress,
  }) async {
    final result = await _jsonRpc('split_integrated_address', {
      'integrated_address': integratedAddress,
    });
    return SplitIntegratedAddressResult.fromJson(result);
  }

  Future<String> makeUri({
    required String address,
    BigInt? amount,
    String? paymentId,
    String? recipientName,
    String? txDescription,
  }) async {
    final result = await _jsonRpc('make_uri', {
      'address': address,
      if (amount != null) 'amount': amount.toString(),
      if (paymentId != null) 'payment_id': paymentId,
      if (recipientName != null) 'recipient_name': recipientName,
      if (txDescription != null) 'tx_description': txDescription,
    });
    return result['uri'] as String? ?? '';
  }

  Future<ParsedUriResult> parseUri({required String uri}) async {
    final result = await _jsonRpc('parse_uri', {'uri': uri});
    return ParsedUriResult.fromJson(result['uri'] as Map<String, dynamic>? ?? const {});
  }

  Future<void> setTxNotes({required List<String> txids, required List<String> notes}) async {
    await _jsonRpc('set_tx_notes', {
      'txids': txids,
      'notes': notes,
    });
  }

  Future<List<String>> getTxNotes({required List<String> txids}) async {
    final result = await _jsonRpc('get_tx_notes', {
      'txids': txids,
    });
    return (result['notes'] as List?)?.cast<String>() ?? const [];
  }

  Future<void> setAttribute({required String key, required String value}) async {
    await _jsonRpc('set_attribute', {
      'key': key,
      'value': value,
    });
  }

  Future<String> getAttribute({required String key}) async {
    final result = await _jsonRpc('get_attribute', {
      'key': key,
    });
    return result['value'] as String? ?? '';
  }

  Future<void> freeze({required String keyImage}) async {
    await _jsonRpc('freeze', {'key_image': keyImage});
  }

  Future<bool> frozen({required String keyImage}) async {
    final result = await _jsonRpc('frozen', {'key_image': keyImage});
    return result['frozen'] as bool? ?? false;
  }

  Future<void> thaw({required String keyImage}) async {
    await _jsonRpc('thaw', {'key_image': keyImage});
  }

  Future<EstimateTxSizeAndWeightResult> estimateTxSizeAndWeight({
    required int nInputs,
    required int nOutputs,
    required int ringSize,
    required bool rct,
  }) async {
    final result = await _jsonRpc('estimate_tx_size_and_weight', {
      'n_inputs': nInputs,
      'n_outputs': nOutputs,
      'ring_size': ringSize,
      'rct': rct,
    });
    return EstimateTxSizeAndWeightResult.fromJson(result);
  }

  Future<void> scanTx({required List<String> txids}) async {
    await _jsonRpc('scan_tx', {'txids': txids});
  }

  Future<String> exportOutputs({bool all = false}) async {
    final result = await _jsonRpc('export_outputs', {
      'all': all,
    });
    return result['outputs_data_hex'] as String? ?? '';
  }

  Future<int> importOutputs({required String outputsDataHex}) async {
    final result = await _jsonRpc('import_outputs', {
      'outputs_data_hex': outputsDataHex,
    });
    return result['num_imported'] as int? ?? 0;
  }

  Future<ExportKeyImagesResult> exportKeyImages({bool all = false}) async {
    final result = await _jsonRpc('export_key_images', {
      'all': all,
    });
    return ExportKeyImagesResult.fromJson(result);
  }

  Future<ImportKeyImagesResult> importKeyImages({
    int? offset,
    required List<SignedKeyImage> signedKeyImages,
  }) async {
    final result = await _jsonRpc('import_key_images', {
      if (offset != null) 'offset': offset,
      'signed_key_images': signedKeyImages.map((e) => e.toJson()).toList(),
    });
    return ImportKeyImagesResult.fromJson(result);
  }

  Future<List<AddressBookEntry>> getAddressBook({required List<int> entries}) async {
    final result = await _jsonRpc('get_address_book', {
      'entries': entries,
    });
    return (result['entries'] as List?)
            ?.map((e) => AddressBookEntry.fromJson(e as Map<String, dynamic>))
            .toList() ??
        const [];
  }

  Future<int> addAddressBook({
    required String address,
    String? paymentId,
    String description = '',
  }) async {
    final result = await _jsonRpc('add_address_book', {
      'address': address,
      if (paymentId != null) 'payment_id': paymentId,
      'description': description,
    });
    return result['index'] as int? ?? -1;
  }

  Future<void> editAddressBook({
    required int index,
    String? address,
    String? paymentId,
    String? description,
  }) async {
    await _jsonRpc('edit_address_book', {
      'index': index,
      if (address != null) ...{
        'set_address': true,
        'address': address,
      },
      if (paymentId != null) ...{
        'set_payment_id': true,
        'payment_id': paymentId,
      },
      if (description != null) ...{
        'set_description': true,
        'description': description,
      },
    });
  }

  Future<void> deleteAddressBook({required int index}) async {
    await _jsonRpc('delete_address_book', {
      'index': index,
    });
  }

  Future<void> rescanBlockchain() async {
    await _jsonRpc('rescan_blockchain');
  }

  Future<void> rescanSpent() async {
    await _jsonRpc('rescan_spent');
  }

  Future<void> setDaemon({
    String? address,
    bool trusted = false,
    String? username,
    String? password,
  }) async {
    await _jsonRpc('set_daemon', {
      if (address != null) 'address': address,
      'trusted': trusted,
      if (username != null) 'username': username,
      if (password != null) 'password': password,
    });
  }

  Future<GetAddressIndexResult> getAddressIndex({required String address}) async {
    final result = await _jsonRpc('get_address_index', {
      'address': address,
    });
    return GetAddressIndexResult.fromJson(result);
  }

  Future<CreateAddressResult> createAddress({
    required int accountIndex,
    String? label,
    int? count,
  }) async {
    final result = await _jsonRpc('create_address', {
      'account_index': accountIndex,
      if (label != null) 'label': label,
      if (count != null) 'count': count,
    });
    return CreateAddressResult.fromJson(result);
  }

  Future<void> labelAddress({required SubaddressIndex index, required String label}) async {
    await _jsonRpc('label_address', {
      'index': index.toJson(),
      'label': label,
    });
  }

  Future<GetAccountsResult> getAccounts({
    String? tag,
    bool? regex,
    bool? strictBalances,
  }) async {
    final result = await _jsonRpc('get_accounts', {
      if (tag != null) 'tag': tag,
      if (regex != null) 'regex': regex,
      if (strictBalances != null) 'strict_balances': strictBalances,
    });
    return GetAccountsResult.fromJson(result);
  }

  Future<CreateAccountResult> createAccount({String? label}) async {
    final result = await _jsonRpc('create_account', {
      if (label != null) 'label': label,
    });
    return CreateAccountResult.fromJson(result);
  }

  Future<void> labelAccount({required int accountIndex, required String label}) async {
    await _jsonRpc('label_account', {
      'account_index': accountIndex,
      'label': label,
    });
  }

  Future<GetAccountTagsResult> getAccountTags() async {
    final result = await _jsonRpc('get_account_tags');
    return GetAccountTagsResult.fromJson(result);
  }

  Future<void> tagAccounts({required String tag, required List<int> accounts}) async {
    await _jsonRpc('tag_accounts', {
      'tag': tag,
      'accounts': accounts,
    });
  }

  Future<void> untagAccounts({required List<int> accounts}) async {
    await _jsonRpc('untag_accounts', {
      'accounts': accounts,
    });
  }

  Future<void> setAccountTagDescription({required String tag, required String description}) async {
    await _jsonRpc('set_account_tag_description', {
      'tag': tag,
      'description': description,
    });
  }

  Future<TransferResult> transfer({
    required List<TransferDestination> destinations,
    int? accountIndex,
    List<int>? subaddrIndices,
    int? priority,
    int? ringSize,
    int? unlockTime,
    bool? getTxKey,
    bool? doNotRelay,
    bool? getTxHex,
    bool? getTxMetadata,
  }) async {
    final result = await _jsonRpc('transfer', {
      'destinations': destinations.map((d) => d.toJson()).toList(),
      if (accountIndex != null) 'account_index': accountIndex,
      if (subaddrIndices != null) 'subaddr_indices': subaddrIndices,
      if (priority != null) 'priority': priority,
      if (ringSize != null) 'ring_size': ringSize,
      if (unlockTime != null) 'unlock_time': unlockTime,
      if (getTxKey != null) 'get_tx_key': getTxKey,
      if (doNotRelay != null) 'do_not_relay': doNotRelay,
      if (getTxHex != null) 'get_tx_hex': getTxHex,
      if (getTxMetadata != null) 'get_tx_metadata': getTxMetadata,
    });
    return TransferResult.fromJson(result);
  }

  Future<Map<String, dynamic>> transferSplit({
    required List<TransferDestination> destinations,
    int? accountIndex,
    List<int>? subaddrIndices,
    int? priority,
    int? ringSize,
    int? unlockTime,
    String? paymentId,
    bool? getTxKeys,
    bool? doNotRelay,
    bool? getTxHex,
    bool? getTxMetadata,
  }) async {
    return _jsonRpc('transfer_split', {
      'destinations': destinations.map((d) => d.toJson()).toList(),
      if (accountIndex != null) 'account_index': accountIndex,
      if (subaddrIndices != null) 'subaddr_indices': subaddrIndices,
      if (priority != null) 'priority': priority,
      if (ringSize != null) 'ring_size': ringSize,
      if (unlockTime != null) 'unlock_time': unlockTime,
      if (paymentId != null) 'payment_id': paymentId,
      if (getTxKeys != null) 'get_tx_keys': getTxKeys,
      if (doNotRelay != null) 'do_not_relay': doNotRelay,
      if (getTxHex != null) 'get_tx_hex': getTxHex,
      if (getTxMetadata != null) 'get_tx_metadata': getTxMetadata,
    });
  }

  Future<SignTransferResult> signTransfer({
    required String unsignedTxset,
    bool? exportRaw,
    bool? getTxKeys,
  }) async {
    final result = await _jsonRpc('sign_transfer', {
      'unsigned_txset': unsignedTxset,
      if (exportRaw != null) 'export_raw': exportRaw,
      if (getTxKeys != null) 'get_tx_keys': getTxKeys,
    });
    return SignTransferResult.fromJson(result);
  }

  Future<SubmitTransferResult> submitTransfer({required String txDataHex}) async {
    final result = await _jsonRpc('submit_transfer', {
      'tx_data_hex': txDataHex,
    });
    return SubmitTransferResult.fromJson(result);
  }

  Future<String> getSpendProof({required String txid, String? message}) async {
    final result = await _jsonRpc('get_spend_proof', {
      'txid': txid,
      if (message != null) 'message': message,
    });
    return result['signature'] as String? ?? '';
  }

  Future<bool> checkSpendProof({required String txid, required String signature, String? message}) async {
    final result = await _jsonRpc('check_spend_proof', {
      'txid': txid,
      'signature': signature,
      if (message != null) 'message': message,
    });
    return result['good'] as bool? ?? false;
  }

  Future<String> exportMultisigInfo() async {
    final result = await _jsonRpc('export_multisig_info');
    return result['info'] as String? ?? '';
  }

  Future<int> importMultisigInfo({required List<String> info}) async {
    final result = await _jsonRpc('import_multisig_info', {
      'info': info,
    });
    return result['n_outputs'] as int? ?? 0;
  }

  Future<FinalizeMultisigResult> finalizeMultisig({
    required List<String> multisigInfo,
    String? password,
  }) async {
    final result = await _jsonRpc('finalize_multisig', {
      'multisig_info': multisigInfo,
      if (password != null) 'password': password,
    });
    return FinalizeMultisigResult.fromJson(result);
  }

  Future<SubmitMultisigResult> submitMultisig({required String txDataHex}) async {
    final result = await _jsonRpc('submit_multisig', {
      'tx_data_hex': txDataHex,
    });
    return SubmitMultisigResult.fromJson(result);
  }

  /// Returns the wallet-rpc version information.
  Future<GetVersionResult> getVersion() async {
    final result = await _jsonRpc('get_version');
    return GetVersionResult.fromJson(result);
  }

  /// Creates a new wallet file inside the wallet-rpc `--wallet-dir`.
  ///
  /// This works well with `monero-wallet-rpc --offline` for quick E2E tests.
  Future<void> createWallet({
    required String filename,
    required String password,
    String language = 'English',
  }) async {
    await _jsonRpc('create_wallet', {
      'filename': filename,
      'password': password,
      'language': language,
    });
  }

  /// Opens an existing wallet file inside the wallet-rpc `--wallet-dir`.
  Future<void> openWallet({
    required String filename,
    required String password,
  }) async {
    await _jsonRpc('open_wallet', {
      'filename': filename,
      'password': password,
    });
  }

  /// Returns the primary address for an account.
  Future<GetAddressResult> getAddress({
    int accountIndex = 0,
    List<int>? addressIndex,
  }) async {
    final result = await _jsonRpc('get_address', {
      'account_index': accountIndex,
      if (addressIndex != null) 'address_index': addressIndex,
    });
    return GetAddressResult.fromJson(result);
  }

  Future<Map<String, dynamic>> _jsonRpc(String method,
      [Map<String, dynamic>? params]) async {
    _checkCircuitBreaker();

    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/json_rpc',
        data: {
          'jsonrpc': '2.0',
          'id': '0',
          'method': method,
          if (params != null) 'params': params,
        },
      );

      final body = response.data!;
      if (body.containsKey('error')) {
        final error = body['error'] as Map<String, dynamic>;
        circuitBreaker?.recordFailure();
        throw RpcException.fromRpcError(error);
      }

      circuitBreaker?.recordSuccess();
      return body['result'] as Map<String, dynamic>;
    } on DioException catch (e) {
      circuitBreaker?.recordFailure();
      throw RpcException.fromDioError(e);
    }
  }

  void _checkCircuitBreaker() {
    if (circuitBreaker != null && !circuitBreaker!.allowRequest()) {
      throw RpcException(
        type: RpcErrorType.connectionError,
        message: 'Circuit breaker is open, requests blocked',
      );
    }
  }

  void close() => _dio.close();
}

class CheckTxKeyResult {
  final int confirmations;
  final bool inPool;
  final int received;

  const CheckTxKeyResult({
    required this.confirmations,
    required this.inPool,
    required this.received,
  });

  factory CheckTxKeyResult.fromJson(Map<String, dynamic> json) => CheckTxKeyResult(
        confirmations: json['confirmations'] as int? ?? 0,
        inPool: json['in_pool'] as bool? ?? false,
        received: json['received'] as int? ?? 0,
      );
}

class CheckTxProofResult {
  final int confirmations;
  final bool good;
  final bool inPool;
  final int received;

  const CheckTxProofResult({
    required this.confirmations,
    required this.good,
    required this.inPool,
    required this.received,
  });

  factory CheckTxProofResult.fromJson(Map<String, dynamic> json) => CheckTxProofResult(
        confirmations: json['confirmations'] as int? ?? 0,
        good: json['good'] as bool? ?? false,
        inPool: json['in_pool'] as bool? ?? false,
        received: json['received'] as int? ?? 0,
      );
}

class CheckReserveProofResult {
  final bool good;
  final BigInt spent;
  final BigInt total;

  const CheckReserveProofResult({
    required this.good,
    required this.spent,
    required this.total,
  });

  factory CheckReserveProofResult.fromJson(Map<String, dynamic> json) =>
      CheckReserveProofResult(
        good: json['good'] as bool? ?? false,
        spent: BigInt.tryParse('${json['spent'] ?? 0}') ?? BigInt.zero,
        total: BigInt.tryParse('${json['total'] ?? 0}') ?? BigInt.zero,
      );
}

class IsMultisigResult {
  final bool multisig;
  final bool ready;
  final int threshold;
  final int total;

  const IsMultisigResult({
    required this.multisig,
    required this.ready,
    required this.threshold,
    required this.total,
  });

  factory IsMultisigResult.fromJson(Map<String, dynamic> json) => IsMultisigResult(
        multisig: json['multisig'] as bool? ?? false,
        ready: json['ready'] as bool? ?? false,
        threshold: json['threshold'] as int? ?? 0,
        total: json['total'] as int? ?? 0,
      );
}

class PrepareMultisigResult {
  final String multisigInfo;

  const PrepareMultisigResult({required this.multisigInfo});

  factory PrepareMultisigResult.fromJson(Map<String, dynamic> json) =>
      PrepareMultisigResult(multisigInfo: json['multisig_info'] as String? ?? '');
}

class MakeMultisigResult {
  final String address;
  final String multisigInfo;

  const MakeMultisigResult({required this.address, required this.multisigInfo});

  factory MakeMultisigResult.fromJson(Map<String, dynamic> json) => MakeMultisigResult(
        address: json['address'] as String? ?? '',
        multisigInfo: json['multisig_info'] as String? ?? '',
      );
}

class ExchangeMultisigKeysResult {
  final String address;
  final String multisigInfo;

  const ExchangeMultisigKeysResult({
    required this.address,
    required this.multisigInfo,
  });

  factory ExchangeMultisigKeysResult.fromJson(Map<String, dynamic> json) =>
      ExchangeMultisigKeysResult(
        address: json['address'] as String? ?? '',
        multisigInfo: json['multisig_info'] as String? ?? '',
      );
}

class SignMultisigResult {
  final String txDataHex;
  final List<String> txHashList;

  const SignMultisigResult({required this.txDataHex, required this.txHashList});

  factory SignMultisigResult.fromJson(Map<String, dynamic> json) => SignMultisigResult(
        txDataHex: json['tx_data_hex'] as String? ?? '',
        txHashList: (json['tx_hash_list'] as List?)?.cast<String>() ?? const [],
      );
}

class GetVersionResult {
  final int version;

  const GetVersionResult({required this.version});

  factory GetVersionResult.fromJson(Map<String, dynamic> json) => GetVersionResult(
        version: json['version'] as int? ?? 0,
      );
}

class GetAddressResult {
  final String address;

  const GetAddressResult({required this.address});

  factory GetAddressResult.fromJson(Map<String, dynamic> json) => GetAddressResult(
        address: json['address'] as String? ?? '',
      );
}

class GetHeightResult {
  final int height;

  const GetHeightResult({required this.height});

  factory GetHeightResult.fromJson(Map<String, dynamic> json) => GetHeightResult(
        height: json['height'] as int? ?? 0,
      );
}

class GetBalanceResult {
  final BigInt balance;
  final BigInt unlockedBalance;
  final bool multisigImportNeeded;
  final int timeToUnlock;
  final int blocksToUnlock;
  final List<SubaddressBalance> perSubaddress;

  const GetBalanceResult({
    required this.balance,
    required this.unlockedBalance,
    required this.multisigImportNeeded,
    required this.timeToUnlock,
    required this.blocksToUnlock,
    required this.perSubaddress,
  });

  factory GetBalanceResult.fromJson(Map<String, dynamic> json) => GetBalanceResult(
        balance: WalletRpcClient._parseBigInt(json['balance']),
        unlockedBalance: WalletRpcClient._parseBigInt(json['unlocked_balance']),
        multisigImportNeeded: json['multisig_import_needed'] as bool? ?? false,
        timeToUnlock: json['time_to_unlock'] as int? ?? 0,
        blocksToUnlock: json['blocks_to_unlock'] as int? ?? 0,
        perSubaddress: (json['per_subaddress'] as List?)
                ?.map((e) => SubaddressBalance.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
      );
}

class SubaddressBalance {
  final int accountIndex;
  final int addressIndex;
  final String address;
  final BigInt balance;
  final BigInt unlockedBalance;
  final String label;
  final int numUnspentOutputs;
  final int timeToUnlock;
  final int blocksToUnlock;

  const SubaddressBalance({
    required this.accountIndex,
    required this.addressIndex,
    required this.address,
    required this.balance,
    required this.unlockedBalance,
    required this.label,
    required this.numUnspentOutputs,
    required this.timeToUnlock,
    required this.blocksToUnlock,
  });

  factory SubaddressBalance.fromJson(Map<String, dynamic> json) => SubaddressBalance(
        accountIndex: json['account_index'] as int? ?? 0,
        addressIndex: json['address_index'] as int? ?? 0,
        address: json['address'] as String? ?? '',
        balance: WalletRpcClient._parseBigInt(json['balance']),
        unlockedBalance: WalletRpcClient._parseBigInt(json['unlocked_balance']),
        label: json['label'] as String? ?? '',
        numUnspentOutputs: json['num_unspent_outputs'] as int? ?? 0,
        timeToUnlock: json['time_to_unlock'] as int? ?? 0,
        blocksToUnlock: json['blocks_to_unlock'] as int? ?? 0,
      );
}

class RefreshResult {
  final int blocksFetched;
  final bool receivedMoney;

  const RefreshResult({required this.blocksFetched, required this.receivedMoney});

  factory RefreshResult.fromJson(Map<String, dynamic> json) => RefreshResult(
        blocksFetched: json['blocks_fetched'] as int? ?? 0,
        receivedMoney: json['received_money'] as bool? ?? false,
      );
}

class QueryKeyResult {
  final String key;

  const QueryKeyResult({required this.key});

  factory QueryKeyResult.fromJson(Map<String, dynamic> json) => QueryKeyResult(
        key: json['key'] as String? ?? '',
      );
}

class ValidateAddressResult {
  final bool valid;
  final bool integrated;
  final bool subaddress;
  final String nettype;
  final String openaliasAddress;

  const ValidateAddressResult({
    required this.valid,
    required this.integrated,
    required this.subaddress,
    required this.nettype,
    required this.openaliasAddress,
  });

  factory ValidateAddressResult.fromJson(Map<String, dynamic> json) => ValidateAddressResult(
        valid: json['valid'] as bool? ?? false,
        integrated: json['integrated'] as bool? ?? false,
        subaddress: json['subaddress'] as bool? ?? false,
        nettype: json['nettype'] as String? ?? '',
        openaliasAddress: json['openalias_address'] as String? ?? '',
      );
}

class MakeIntegratedAddressResult {
  final String integratedAddress;
  final String paymentId;

  const MakeIntegratedAddressResult({
    required this.integratedAddress,
    required this.paymentId,
  });

  factory MakeIntegratedAddressResult.fromJson(Map<String, dynamic> json) =>
      MakeIntegratedAddressResult(
        integratedAddress: json['integrated_address'] as String? ?? '',
        paymentId: json['payment_id'] as String? ?? '',
      );
}

class SplitIntegratedAddressResult {
  final bool isSubaddress;
  final String standardAddress;
  final String paymentId;

  const SplitIntegratedAddressResult({
    required this.isSubaddress,
    required this.standardAddress,
    required this.paymentId,
  });

  factory SplitIntegratedAddressResult.fromJson(Map<String, dynamic> json) =>
      SplitIntegratedAddressResult(
        isSubaddress: json['is_subaddress'] as bool? ?? false,
        standardAddress: json['standard_address'] as String? ?? '',
        paymentId: json['payment_id'] as String? ?? '',
      );
}

class ParsedUriResult {
  final String address;
  final BigInt amount;
  final String paymentId;
  final String recipientName;
  final String txDescription;

  const ParsedUriResult({
    required this.address,
    required this.amount,
    required this.paymentId,
    required this.recipientName,
    required this.txDescription,
  });

  factory ParsedUriResult.fromJson(Map<String, dynamic> json) => ParsedUriResult(
        address: json['address'] as String? ?? '',
        amount: WalletRpcClient._parseBigInt(json['amount']),
        paymentId: json['payment_id'] as String? ?? '',
        recipientName: json['recipient_name'] as String? ?? '',
        txDescription: json['tx_description'] as String? ?? '',
      );
}

class SignedKeyImage {
  final String keyImage;
  final String signature;

  const SignedKeyImage({required this.keyImage, required this.signature});

  factory SignedKeyImage.fromJson(Map<String, dynamic> json) => SignedKeyImage(
        keyImage: json['key_image'] as String? ?? '',
        signature: json['signature'] as String? ?? '',
      );

  Map<String, dynamic> toJson() => {
        'key_image': keyImage,
        'signature': signature,
      };
}

class ExportKeyImagesResult {
  final int offset;
  final List<SignedKeyImage> signedKeyImages;

  const ExportKeyImagesResult({required this.offset, required this.signedKeyImages});

  factory ExportKeyImagesResult.fromJson(Map<String, dynamic> json) => ExportKeyImagesResult(
        offset: json['offset'] as int? ?? 0,
        signedKeyImages: (json['signed_key_images'] as List?)
                ?.map((e) => SignedKeyImage.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
      );
}

class ImportKeyImagesResult {
  final int height;
  final BigInt spent;
  final BigInt unspent;

  const ImportKeyImagesResult({required this.height, required this.spent, required this.unspent});

  factory ImportKeyImagesResult.fromJson(Map<String, dynamic> json) => ImportKeyImagesResult(
        height: json['height'] as int? ?? 0,
        spent: WalletRpcClient._parseBigInt(json['spent']),
        unspent: WalletRpcClient._parseBigInt(json['unspent']),
      );
}

class AddressBookEntry {
  final String address;
  final String description;
  final int index;
  final String paymentId;

  const AddressBookEntry({
    required this.address,
    required this.description,
    required this.index,
    required this.paymentId,
  });

  factory AddressBookEntry.fromJson(Map<String, dynamic> json) => AddressBookEntry(
        address: json['address'] as String? ?? '',
        description: json['description'] as String? ?? '',
        index: json['index'] as int? ?? 0,
        paymentId: json['payment_id'] as String? ?? '',
      );
}

class EstimateTxSizeAndWeightResult {
  final int size;
  final int weight;

  const EstimateTxSizeAndWeightResult({required this.size, required this.weight});

  factory EstimateTxSizeAndWeightResult.fromJson(Map<String, dynamic> json) =>
      EstimateTxSizeAndWeightResult(
        size: json['size'] as int? ?? 0,
        weight: json['weight'] as int? ?? 0,
      );
}

class SubaddressIndex {
  final int major;
  final int minor;

  const SubaddressIndex({required this.major, required this.minor});

  factory SubaddressIndex.fromJson(Map<String, dynamic> json) => SubaddressIndex(
        major: json['major'] as int? ?? 0,
        minor: json['minor'] as int? ?? 0,
      );

  Map<String, dynamic> toJson() => {
        'major': major,
        'minor': minor,
      };
}

class GetAddressIndexResult {
  final SubaddressIndex index;

  const GetAddressIndexResult({required this.index});

  factory GetAddressIndexResult.fromJson(Map<String, dynamic> json) =>
      GetAddressIndexResult(
        index: SubaddressIndex.fromJson(
          (json['index'] as Map<String, dynamic>?) ?? const {},
        ),
      );
}

class CreateAddressResult {
  final String address;
  final int addressIndex;
  final List<int> addressIndices;
  final List<String> addresses;

  const CreateAddressResult({
    required this.address,
    required this.addressIndex,
    required this.addressIndices,
    required this.addresses,
  });

  factory CreateAddressResult.fromJson(Map<String, dynamic> json) => CreateAddressResult(
        address: json['address'] as String? ?? '',
        addressIndex: json['address_index'] as int? ?? 0,
        addressIndices: (json['address_indices'] as List?)?.cast<int>() ?? const [],
        addresses: (json['addresses'] as List?)?.cast<String>() ?? const [],
      );
}

class AccountInfo {
  final int accountIndex;
  final BigInt balance;
  final BigInt unlockedBalance;
  final String baseAddress;
  final String label;
  final String tag;

  const AccountInfo({
    required this.accountIndex,
    required this.balance,
    required this.unlockedBalance,
    required this.baseAddress,
    required this.label,
    required this.tag,
  });

  factory AccountInfo.fromJson(Map<String, dynamic> json) => AccountInfo(
        accountIndex: json['account_index'] as int? ?? 0,
        balance: WalletRpcClient._parseBigInt(json['balance']),
        unlockedBalance: WalletRpcClient._parseBigInt(json['unlocked_balance']),
        baseAddress: json['base_address'] as String? ?? '',
        label: json['label'] as String? ?? '',
        tag: json['tag'] as String? ?? '',
      );
}

class GetAccountsResult {
  final List<AccountInfo> subaddressAccounts;
  final BigInt totalBalance;
  final BigInt totalUnlockedBalance;

  const GetAccountsResult({
    required this.subaddressAccounts,
    required this.totalBalance,
    required this.totalUnlockedBalance,
  });

  factory GetAccountsResult.fromJson(Map<String, dynamic> json) => GetAccountsResult(
        subaddressAccounts: (json['subaddress_accounts'] as List?)
                ?.map((e) => AccountInfo.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
        totalBalance: WalletRpcClient._parseBigInt(json['total_balance']),
        totalUnlockedBalance: WalletRpcClient._parseBigInt(json['total_unlocked_balance']),
      );
}

class CreateAccountResult {
  final int accountIndex;
  final String address;

  const CreateAccountResult({required this.accountIndex, required this.address});

  factory CreateAccountResult.fromJson(Map<String, dynamic> json) => CreateAccountResult(
        accountIndex: json['account_index'] as int? ?? 0,
        address: json['address'] as String? ?? '',
      );
}

class AccountTagInfo {
  final String tag;
  final String label;
  final List<int> accounts;

  const AccountTagInfo({required this.tag, required this.label, required this.accounts});

  factory AccountTagInfo.fromJson(Map<String, dynamic> json) => AccountTagInfo(
        tag: json['tag'] as String? ?? '',
        label: json['label'] as String? ?? '',
        accounts: (json['accounts'] as List?)?.cast<int>() ?? const [],
      );
}

class GetAccountTagsResult {
  final List<AccountTagInfo> accountTags;

  const GetAccountTagsResult({required this.accountTags});

  factory GetAccountTagsResult.fromJson(Map<String, dynamic> json) => GetAccountTagsResult(
        accountTags: (json['account_tags'] as List?)
                ?.map((e) => AccountTagInfo.fromJson(e as Map<String, dynamic>))
                .toList() ??
            const [],
      );
}

class TransferDestination {
  final String address;
  final BigInt amount;

  const TransferDestination({required this.address, required this.amount});

  Map<String, dynamic> toJson() => {
        'address': address,
        'amount': amount.toString(),
      };
}

class TransferResult {
  final BigInt amount;
  final BigInt fee;
  final String txHash;
  final String txKey;
  final String txBlob;
  final String txMetadata;
  final String multisigTxset;
  final String unsignedTxset;

  const TransferResult({
    required this.amount,
    required this.fee,
    required this.txHash,
    required this.txKey,
    required this.txBlob,
    required this.txMetadata,
    required this.multisigTxset,
    required this.unsignedTxset,
  });

  factory TransferResult.fromJson(Map<String, dynamic> json) => TransferResult(
        amount: WalletRpcClient._parseBigInt(json['amount']),
        fee: WalletRpcClient._parseBigInt(json['fee']),
        txHash: json['tx_hash'] as String? ?? '',
        txKey: json['tx_key'] as String? ?? '',
        txBlob: json['tx_blob'] as String? ?? '',
        txMetadata: json['tx_metadata'] as String? ?? '',
        multisigTxset: json['multisig_txset'] as String? ?? '',
        unsignedTxset: json['unsigned_txset'] as String? ?? '',
      );
}

class SignTransferResult {
  final String signedTxset;
  final List<String> txHashList;
  final List<String> txRawList;
  final List<String> txKeyList;

  const SignTransferResult({
    required this.signedTxset,
    required this.txHashList,
    required this.txRawList,
    required this.txKeyList,
  });

  factory SignTransferResult.fromJson(Map<String, dynamic> json) => SignTransferResult(
        signedTxset: json['signed_txset'] as String? ?? '',
        txHashList: (json['tx_hash_list'] as List?)?.cast<String>() ?? const [],
        txRawList: (json['tx_raw_list'] as List?)?.cast<String>() ?? const [],
        txKeyList: (json['tx_key_list'] as List?)?.cast<String>() ?? const [],
      );
}

class SubmitTransferResult {
  final List<String> txHashList;

  const SubmitTransferResult({required this.txHashList});

  factory SubmitTransferResult.fromJson(Map<String, dynamic> json) => SubmitTransferResult(
        txHashList: (json['tx_hash_list'] as List?)?.cast<String>() ?? const [],
      );
}

class FinalizeMultisigResult {
  final String address;

  const FinalizeMultisigResult({required this.address});

  factory FinalizeMultisigResult.fromJson(Map<String, dynamic> json) => FinalizeMultisigResult(
        address: json['address'] as String? ?? '',
      );
}

class SubmitMultisigResult {
  final List<String> txHashList;

  const SubmitMultisigResult({required this.txHashList});

  factory SubmitMultisigResult.fromJson(Map<String, dynamic> json) => SubmitMultisigResult(
        txHashList: (json['tx_hash_list'] as List?)?.cast<String>() ?? const [],
      );
}
