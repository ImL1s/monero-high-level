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
