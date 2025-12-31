/// Monero Daemon RPC Client
///
/// Implements JSON-RPC 2.0 protocol for monerod communication.
/// Supports retry with exponential backoff and circuit breaker pattern.
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import 'rpc_utils.dart';

/// Daemon RPC client for Monero node communication.
class DaemonClient {
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

  DaemonClient({
    this.host = 'localhost',
    this.port = 18081,
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

    // Add retry interceptor
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

  /// Get daemon info
  Future<DaemonInfo> getInfo() async {
    final result = await _jsonRpc('get_info');
    return DaemonInfo.fromJson(result);
  }

  /// Get current blockchain height
  Future<int> getHeight() async {
    final result = await _jsonRpc('get_height');
    return result['height'] as int;
  }

  /// Get block by height
  Future<BlockInfo> getBlockByHeight(int height) async {
    final result = await _jsonRpc('get_block', {'height': height});
    return BlockInfo.fromJson(result);
  }

  /// Get outputs for key offsets (for decoy selection)
  Future<List<OutputInfo>> getOutputs(List<int> offsets) async {
    final result = await _request('/get_outs', {
      'outputs': offsets.map((o) => {'index': o}).toList(),
      'get_txid': true,
    });
    
    final outs = result['outs'] as List;
    return outs.map((o) => OutputInfo.fromJson(o as Map<String, dynamic>)).toList();
  }

  /// Submit raw transaction
  Future<TxSubmitResult> sendRawTransaction(Uint8List txBlob) async {
    final result = await _request('/send_raw_transaction', {
      'tx_as_hex': _bytesToHex(txBlob),
      'do_not_relay': false,
    });
    return TxSubmitResult.fromJson(result);
  }

  /// Get fee estimate
  Future<FeeEstimate> getFeeEstimate() async {
    final result = await _jsonRpc('get_fee_estimate');
    return FeeEstimate.fromJson(result);
  }

  /// Get transaction pool (mempool)
  Future<List<PoolTransaction>> getTransactionPool() async {
    final result = await _jsonRpc('get_transaction_pool');
    final txs = result['transactions'] as List? ?? [];
    return txs.map((tx) => PoolTransaction.fromJson(tx as Map<String, dynamic>)).toList();
  }

  /// JSON-RPC 2.0 call with retry and circuit breaker support
  Future<Map<String, dynamic>> _jsonRpc(String method, [Map<String, dynamic>? params]) async {
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

  /// Direct HTTP request with retry and circuit breaker support
  Future<Map<String, dynamic>> _request(String path, Map<String, dynamic> body) async {
    _checkCircuitBreaker();
    
    try {
      final response = await _dio.post<Map<String, dynamic>>(path, data: body);
      circuitBreaker?.recordSuccess();
      return response.data!;
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

  String _bytesToHex(Uint8List bytes) =>
      bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();

  /// Close client
  void close() => _dio.close();
}

/// Daemon info response
class DaemonInfo {
  final int height;
  final int targetHeight;
  final int difficulty;
  final int txCount;
  final int txPoolSize;
  final bool synchronized;
  final String version;
  final String topBlockHash;
  final bool mainnet;
  final bool testnet;
  final bool stagenet;

  DaemonInfo({
    required this.height,
    required this.targetHeight,
    required this.difficulty,
    required this.txCount,
    required this.txPoolSize,
    required this.synchronized,
    required this.version,
    required this.topBlockHash,
    required this.mainnet,
    required this.testnet,
    required this.stagenet,
  });

  factory DaemonInfo.fromJson(Map<String, dynamic> json) => DaemonInfo(
        height: json['height'] as int? ?? 0,
        targetHeight: json['target_height'] as int? ?? 0,
        difficulty: json['difficulty'] as int? ?? 0,
        txCount: json['tx_count'] as int? ?? 0,
        txPoolSize: json['tx_pool_size'] as int? ?? 0,
        synchronized: json['synchronized'] as bool? ?? false,
        version: json['version'] as String? ?? '',
        topBlockHash: json['top_block_hash'] as String? ?? '',
        mainnet: json['mainnet'] as bool? ?? false,
        testnet: json['testnet'] as bool? ?? false,
        stagenet: json['stagenet'] as bool? ?? false,
      );
}

/// Block information
class BlockInfo {
  final int height;
  final String hash;
  final int timestamp;
  final String prevHash;
  final int nonce;
  final List<String> txHashes;

  BlockInfo({
    required this.height,
    required this.hash,
    required this.timestamp,
    required this.prevHash,
    required this.nonce,
    required this.txHashes,
  });

  factory BlockInfo.fromJson(Map<String, dynamic> json) {
    final blockHeader = json['block_header'] as Map<String, dynamic>? ?? {};
    return BlockInfo(
      height: blockHeader['height'] as int? ?? 0,
      hash: blockHeader['hash'] as String? ?? '',
      timestamp: blockHeader['timestamp'] as int? ?? 0,
      prevHash: blockHeader['prev_hash'] as String? ?? '',
      nonce: blockHeader['nonce'] as int? ?? 0,
      txHashes: (json['tx_hashes'] as List?)?.cast<String>() ?? [],
    );
  }
}

/// Output information for decoy selection
class OutputInfo {
  final int height;
  final String key;
  final String mask;
  final String txid;
  final bool unlocked;

  OutputInfo({
    required this.height,
    required this.key,
    required this.mask,
    required this.txid,
    required this.unlocked,
  });

  factory OutputInfo.fromJson(Map<String, dynamic> json) => OutputInfo(
        height: json['height'] as int? ?? 0,
        key: json['key'] as String? ?? '',
        mask: json['mask'] as String? ?? '',
        txid: json['txid'] as String? ?? '',
        unlocked: json['unlocked'] as bool? ?? false,
      );
}

/// Transaction submission result
class TxSubmitResult {
  final bool success;
  final String? reason;
  final bool doubleSpend;
  final bool feeTooLow;
  final bool invalidInput;
  final bool tooBig;

  TxSubmitResult({
    required this.success,
    this.reason,
    this.doubleSpend = false,
    this.feeTooLow = false,
    this.invalidInput = false,
    this.tooBig = false,
  });

  factory TxSubmitResult.fromJson(Map<String, dynamic> json) => TxSubmitResult(
        success: json['status'] == 'OK',
        reason: json['reason'] as String?,
        doubleSpend: json['double_spend'] as bool? ?? false,
        feeTooLow: json['fee_too_low'] as bool? ?? false,
        invalidInput: json['invalid_input'] as bool? ?? false,
        tooBig: json['too_big'] as bool? ?? false,
      );
}

/// Fee estimate
class FeeEstimate {
  final int feePerByte;
  final int quantizationMask;
  final List<int> fees;

  FeeEstimate({
    required this.feePerByte,
    required this.quantizationMask,
    required this.fees,
  });

  factory FeeEstimate.fromJson(Map<String, dynamic> json) => FeeEstimate(
        feePerByte: json['fee'] as int? ?? 0,
        quantizationMask: json['quantization_mask'] as int? ?? 1,
        fees: (json['fees'] as List?)?.cast<int>() ?? [],
      );
}

/// Pool (mempool) transaction
class PoolTransaction {
  final String txHash;
  final int blobSize;
  final int fee;
  final int receivedTime;

  PoolTransaction({
    required this.txHash,
    required this.blobSize,
    required this.fee,
    required this.receivedTime,
  });

  factory PoolTransaction.fromJson(Map<String, dynamic> json) => PoolTransaction(
        txHash: json['id_hash'] as String? ?? '',
        blobSize: json['blob_size'] as int? ?? 0,
        fee: json['fee'] as int? ?? 0,
        receivedTime: json['receive_time'] as int? ?? 0,
      );
}

/// Daemon RPC exception
class DaemonException implements Exception {
  final int code;
  final String message;

  DaemonException({required this.code, required this.message});

  factory DaemonException.fromJson(Map<String, dynamic> json) => DaemonException(
        code: json['code'] as int? ?? -1,
        message: json['message'] as String? ?? 'Unknown error',
      );

  @override
  String toString() => 'DaemonException($code): $message';
}
