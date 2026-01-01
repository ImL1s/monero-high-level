import 'package:monero_dart/src/network/wallet_rpc_client.dart';
import 'package:test/test.dart';

void main() {
  group('WalletRpcClient', () {
    test('can be instantiated with defaults', () {
      final client = WalletRpcClient();
      expect(client.host, equals('localhost'));
      expect(client.port, equals(18082));
      expect(client.useSsl, isFalse);
      client.close();
    });

    test('can be customized', () {
      final client = WalletRpcClient(
        host: 'wallet.example.com',
        port: 18083,
        useSsl: true,
        username: 'user',
        password: 'pass',
        timeout: Duration(seconds: 60),
      );
      expect(client.host, equals('wallet.example.com'));
      expect(client.port, equals(18083));
      expect(client.useSsl, isTrue);
      client.close();
    });
  });

  group('Wallet RPC DTOs', () {
    test('CheckTxProofResult.fromJson parses correctly', () {
      final json = {
        'confirmations': 482,
        'good': true,
        'in_pool': false,
        'received': 1000000000000,
      };
      final result = CheckTxProofResult.fromJson(json);
      expect(result.good, isTrue);
      expect(result.confirmations, equals(482));
      expect(result.inPool, isFalse);
    });

    test('CheckReserveProofResult.fromJson parses BigInt fields', () {
      final json = {
        'good': true,
        'spent': 0,
        'total': 100000000000,
      };
      final result = CheckReserveProofResult.fromJson(json);
      expect(result.good, isTrue);
      expect(result.total.toString(), equals('100000000000'));
    });

    test('IsMultisigResult.fromJson parses correctly', () {
      final json = {
        'multisig': true,
        'ready': true,
        'threshold': 2,
        'total': 3,
      };
      final result = IsMultisigResult.fromJson(json);
      expect(result.multisig, isTrue);
      expect(result.threshold, equals(2));
    });

    test('SignMultisigResult.fromJson parses correctly', () {
      final json = {
        'tx_data_hex': 'deadbeef',
        'tx_hash_list': ['h1', 'h2'],
      };
      final result = SignMultisigResult.fromJson(json);
      expect(result.txDataHex, equals('deadbeef'));
      expect(result.txHashList.length, equals(2));
    });
  });
}
