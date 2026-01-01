import 'package:test/test.dart';
import 'package:monero_dart/src/network/daemon_client.dart';

void main() {
  group('DaemonClient', () {
    test('can be instantiated with defaults', () {
      final client = DaemonClient();
      expect(client.host, equals('localhost'));
      expect(client.port, equals(18081));
      expect(client.useSsl, isFalse);
      client.close();
    });

    test('can be customized', () {
      final client = DaemonClient(
        host: 'node.example.com',
        port: 18089,
        useSsl: true,
        username: 'user',
        password: 'pass',
        timeout: Duration(seconds: 60),
      );
      expect(client.host, equals('node.example.com'));
      expect(client.port, equals(18089));
      expect(client.useSsl, isTrue);
      client.close();
    });
  });

  group('DaemonInfo', () {
    test('fromJson parses correctly', () {
      final json = {
        'height': 123456,
        'target_height': 123456,
        'difficulty': 1000000,
        'tx_count': 50000,
        'tx_pool_size': 10,
        'synchronized': true,
        'version': '0.18.3.0',
        'top_block_hash': 'abc123',
        'mainnet': false,
        'testnet': false,
        'stagenet': true,
      };
      final info = DaemonInfo.fromJson(json);
      expect(info.height, equals(123456));
      expect(info.stagenet, isTrue);
      expect(info.synchronized, isTrue);
    });

    test('handles missing fields gracefully', () {
      final info = DaemonInfo.fromJson({});
      expect(info.height, equals(0));
      expect(info.version, isEmpty);
    });
  });

  group('BlockInfo', () {
    test('fromJson parses correctly', () {
      final json = {
        'block_header': {
          'height': 100,
          'hash': 'block_hash',
          'timestamp': 1234567890,
          'prev_hash': 'prev_hash',
          'nonce': 42,
        },
        'tx_hashes': ['tx1', 'tx2'],
      };
      final block = BlockInfo.fromJson(json);
      expect(block.height, equals(100));
      expect(block.txHashes.length, equals(2));
    });
  });

  group('OutputInfo', () {
    test('fromJson parses correctly', () {
      final json = {
        'height': 500,
        'key': 'output_key',
        'mask': 'output_mask',
        'txid': 'tx_id',
        'unlocked': true,
      };
      final output = OutputInfo.fromJson(json);
      expect(output.unlocked, isTrue);
    });
  });

  group('TxSubmitResult', () {
    test('parses success', () {
      final json = {'status': 'OK'};
      final result = TxSubmitResult.fromJson(json);
      expect(result.success, isTrue);
    });

    test('parses failure with reason', () {
      final json = {
        'status': 'Failed',
        'reason': 'Fee too low',
        'fee_too_low': true,
      };
      final result = TxSubmitResult.fromJson(json);
      expect(result.success, isFalse);
      expect(result.feeTooLow, isTrue);
    });
  });

  group('FeeEstimate', () {
    test('fromJson parses correctly', () {
      final json = {
        'fee': 1000,
        'quantization_mask': 1,
        'fees': [1000, 5000, 25000, 1000000],
      };
      final fee = FeeEstimate.fromJson(json);
      expect(fee.fees.length, equals(4));
    });
  });

  group('PoolTransaction', () {
    test('fromJson parses correctly', () {
      final json = {
        'id_hash': 'pool_tx_hash',
        'blob_size': 1500,
        'fee': 30000000,
        'receive_time': 1234567890,
      };
      final poolTx = PoolTransaction.fromJson(json);
      expect(poolTx.blobSize, equals(1500));
    });
  });

  group('DaemonException', () {
    test('fromJson creates exception', () {
      final json = {
        'code': -1,
        'message': 'Method not found',
      };
      final ex = DaemonException.fromJson(json);
      expect(ex.code, equals(-1));
      expect(ex.message, equals('Method not found'));
    });

    test('toString formats correctly', () {
      final ex = DaemonException(code: 42, message: 'Test error');
      expect(ex.toString(), equals('DaemonException(42): Test error'));
    });
  });
}
