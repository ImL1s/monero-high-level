import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';

/// Retry configuration for RPC calls
class RetryConfig {
  /// Maximum number of retry attempts
  final int maxAttempts;
  
  /// Initial delay between retries
  final Duration initialDelay;
  
  /// Maximum delay between retries
  final Duration maxDelay;
  
  /// Multiplier for exponential backoff
  final double backoffMultiplier;
  
  /// Whether to retry on timeout
  final bool retryOnTimeout;
  
  /// Whether to retry on connection errors
  final bool retryOnConnectionError;

  const RetryConfig({
    this.maxAttempts = 3,
    this.initialDelay = const Duration(milliseconds: 500),
    this.maxDelay = const Duration(seconds: 10),
    this.backoffMultiplier = 2.0,
    this.retryOnTimeout = true,
    this.retryOnConnectionError = true,
  });
  
  static const none = RetryConfig(maxAttempts: 1);
  
  static const aggressive = RetryConfig(
    maxAttempts: 5,
    initialDelay: Duration(milliseconds: 200),
    maxDelay: Duration(seconds: 30),
    backoffMultiplier: 2.5,
  );
}

/// RPC error types for better error handling
enum RpcErrorType {
  /// Network connection failed
  connectionError,
  
  /// Request timed out
  timeout,
  
  /// Server returned an error response
  serverError,
  
  /// Invalid response format
  invalidResponse,
  
  /// RPC method error (from daemon)
  rpcError,
  
  /// Unknown error
  unknown,
}

/// Enhanced RPC exception with error type classification
class RpcException implements Exception {
  final RpcErrorType type;
  final String message;
  final int? code;
  final dynamic originalError;
  final int attemptsMade;

  RpcException({
    required this.type,
    required this.message,
    this.code,
    this.originalError,
    this.attemptsMade = 1,
  });

  factory RpcException.fromDioError(DioException e, {int attemptsMade = 1}) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return RpcException(
          type: RpcErrorType.timeout,
          message: 'Request timed out: ${e.message}',
          originalError: e,
          attemptsMade: attemptsMade,
        );
      case DioExceptionType.connectionError:
        return RpcException(
          type: RpcErrorType.connectionError,
          message: 'Connection failed: ${e.message}',
          originalError: e,
          attemptsMade: attemptsMade,
        );
      case DioExceptionType.badResponse:
        final statusCode = e.response?.statusCode;
        return RpcException(
          type: RpcErrorType.serverError,
          message: 'Server error: HTTP $statusCode',
          code: statusCode,
          originalError: e,
          attemptsMade: attemptsMade,
        );
      default:
        return RpcException(
          type: RpcErrorType.unknown,
          message: e.message ?? 'Unknown error',
          originalError: e,
          attemptsMade: attemptsMade,
        );
    }
  }

  factory RpcException.fromRpcError(Map<String, dynamic> error, {int attemptsMade = 1}) {
    return RpcException(
      type: RpcErrorType.rpcError,
      message: error['message'] as String? ?? 'Unknown RPC error',
      code: error['code'] as int?,
      attemptsMade: attemptsMade,
    );
  }

  bool get isRetryable {
    return type == RpcErrorType.timeout || 
           type == RpcErrorType.connectionError ||
           (type == RpcErrorType.serverError && (code == null || code! >= 500));
  }

  @override
  String toString() => 'RpcException($type): $message [attempts: $attemptsMade]';
}

/// Retry interceptor for Dio
class RetryInterceptor extends Interceptor {
  final Dio dio;
  final RetryConfig config;
  
  RetryInterceptor({required this.dio, required this.config});
  
  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final extra = err.requestOptions.extra;
    final attempt = (extra['attempt'] as int?) ?? 1;
    
    if (attempt >= config.maxAttempts) {
      return handler.next(err);
    }
    
    if (!_shouldRetry(err)) {
      return handler.next(err);
    }
    
    final delay = _calculateDelay(attempt);
    await Future.delayed(delay);
    
    try {
      final options = err.requestOptions;
      options.extra['attempt'] = attempt + 1;
      
      final response = await dio.fetch(options);
      return handler.resolve(response);
    } on DioException catch (e) {
      return handler.next(e);
    }
  }
  
  bool _shouldRetry(DioException err) {
    switch (err.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return config.retryOnTimeout;
      case DioExceptionType.connectionError:
        return config.retryOnConnectionError;
      case DioExceptionType.badResponse:
        final statusCode = err.response?.statusCode;
        return statusCode != null && statusCode >= 500;
      default:
        return false;
    }
  }
  
  Duration _calculateDelay(int attempt) {
    var delay = config.initialDelay.inMilliseconds.toDouble() * 
                pow(config.backoffMultiplier, attempt - 1);
    delay = delay.clamp(0.0, config.maxDelay.inMilliseconds.toDouble());
    return Duration(milliseconds: delay.toInt());
  }
  
  double pow(double base, int exponent) {
    var result = 1.0;
    for (var i = 0; i < exponent; i++) {
      result *= base;
    }
    return result;
  }
}

/// Circuit breaker states
enum CircuitState { closed, open, halfOpen }

/// Circuit breaker for RPC calls
class CircuitBreaker {
  final int failureThreshold;
  final Duration resetTimeout;
  
  CircuitState _state = CircuitState.closed;
  int _failureCount = 0;
  DateTime? _lastFailureTime;
  
  CircuitBreaker({
    this.failureThreshold = 5,
    this.resetTimeout = const Duration(seconds: 30),
  });
  
  CircuitState get state => _state;
  
  bool get isOpen => _state == CircuitState.open;
  
  void recordSuccess() {
    _failureCount = 0;
    _state = CircuitState.closed;
  }
  
  void recordFailure() {
    _failureCount++;
    _lastFailureTime = DateTime.now();
    
    if (_failureCount >= failureThreshold) {
      _state = CircuitState.open;
    }
  }
  
  bool allowRequest() {
    if (_state == CircuitState.closed) {
      return true;
    }
    
    if (_state == CircuitState.open) {
      final now = DateTime.now();
      if (_lastFailureTime != null && 
          now.difference(_lastFailureTime!) >= resetTimeout) {
        _state = CircuitState.halfOpen;
        return true;
      }
      return false;
    }
    
    // Half-open: allow one request to test
    return true;
  }
  
  void reset() {
    _state = CircuitState.closed;
    _failureCount = 0;
    _lastFailureTime = null;
  }
}
