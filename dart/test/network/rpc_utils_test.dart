import 'package:monero_dart/src/network/rpc_utils.dart';
import 'package:test/test.dart';

void main() {
  group('RetryConfig', () {
    test('default configuration', () {
      const config = RetryConfig();
      expect(config.maxAttempts, 3);
      expect(config.initialDelay, const Duration(milliseconds: 500));
      expect(config.maxDelay, const Duration(seconds: 10));
      expect(config.backoffMultiplier, 2.0);
      expect(config.retryOnTimeout, true);
      expect(config.retryOnConnectionError, true);
    });

    test('none configuration disables retry', () {
      expect(RetryConfig.none.maxAttempts, 1);
    });

    test('aggressive configuration', () {
      expect(RetryConfig.aggressive.maxAttempts, 5);
      expect(RetryConfig.aggressive.initialDelay, const Duration(milliseconds: 200));
      expect(RetryConfig.aggressive.maxDelay, const Duration(seconds: 30));
    });
  });

  group('RpcException', () {
    test('fromRpcError creates correct exception', () {
      final error = {'code': -1, 'message': 'Invalid method'};
      final exception = RpcException.fromRpcError(error);
      
      expect(exception.type, RpcErrorType.rpcError);
      expect(exception.code, -1);
      expect(exception.message, 'Invalid method');
      expect(exception.isRetryable, false);
    });

    test('connection error is retryable', () {
      final exception = RpcException(
        type: RpcErrorType.connectionError,
        message: 'Connection failed',
      );
      expect(exception.isRetryable, true);
    });

    test('timeout is retryable', () {
      final exception = RpcException(
        type: RpcErrorType.timeout,
        message: 'Request timed out',
      );
      expect(exception.isRetryable, true);
    });

    test('server error 500+ is retryable', () {
      final exception = RpcException(
        type: RpcErrorType.serverError,
        message: 'Internal server error',
        code: 500,
      );
      expect(exception.isRetryable, true);
    });

    test('server error 4xx is not retryable', () {
      final exception = RpcException(
        type: RpcErrorType.serverError,
        message: 'Bad request',
        code: 400,
      );
      expect(exception.isRetryable, false);
    });

    test('toString includes all info', () {
      final exception = RpcException(
        type: RpcErrorType.timeout,
        message: 'Request timed out',
        attemptsMade: 3,
      );
      expect(exception.toString(), contains('timeout'));
      expect(exception.toString(), contains('Request timed out'));
      expect(exception.toString(), contains('3'));
    });
  });

  group('CircuitBreaker', () {
    test('starts in closed state', () {
      final breaker = CircuitBreaker();
      expect(breaker.state, CircuitState.closed);
      expect(breaker.isOpen, false);
      expect(breaker.allowRequest(), true);
    });

    test('opens after reaching failure threshold', () {
      final breaker = CircuitBreaker(failureThreshold: 3);
      
      breaker.recordFailure();
      expect(breaker.state, CircuitState.closed);
      
      breaker.recordFailure();
      expect(breaker.state, CircuitState.closed);
      
      breaker.recordFailure();
      expect(breaker.state, CircuitState.open);
      expect(breaker.isOpen, true);
      expect(breaker.allowRequest(), false);
    });

    test('resets failure count on success', () {
      final breaker = CircuitBreaker(failureThreshold: 3);
      
      breaker.recordFailure();
      breaker.recordFailure();
      expect(breaker.state, CircuitState.closed);
      
      breaker.recordSuccess();
      
      // Now need 3 more failures to open
      breaker.recordFailure();
      breaker.recordFailure();
      expect(breaker.state, CircuitState.closed);
    });

    test('transitions to half-open after timeout', () async {
      final breaker = CircuitBreaker(
        failureThreshold: 2,
        resetTimeout: const Duration(milliseconds: 50),
      );
      
      breaker.recordFailure();
      breaker.recordFailure();
      expect(breaker.state, CircuitState.open);
      
      // Wait for reset timeout
      await Future<void>.delayed(const Duration(milliseconds: 60));
      
      expect(breaker.allowRequest(), true);
      expect(breaker.state, CircuitState.halfOpen);
    });

    test('closes again after success in half-open', () async {
      final breaker = CircuitBreaker(
        failureThreshold: 2,
        resetTimeout: const Duration(milliseconds: 50),
      );
      
      breaker.recordFailure();
      breaker.recordFailure();
      
      await Future<void>.delayed(const Duration(milliseconds: 60));
      breaker.allowRequest(); // Transition to half-open
      
      breaker.recordSuccess();
      expect(breaker.state, CircuitState.closed);
    });

    test('reset clears all state', () {
      final breaker = CircuitBreaker(failureThreshold: 2);
      
      breaker.recordFailure();
      breaker.recordFailure();
      expect(breaker.state, CircuitState.open);
      
      breaker.reset();
      expect(breaker.state, CircuitState.closed);
      expect(breaker.allowRequest(), true);
    });
  });
}
