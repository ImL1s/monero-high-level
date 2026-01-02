import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:monero_dart/src/network/wallet_rpc_client.dart';
import 'package:test/test.dart';

Future<HttpServer> _startJsonRpcServer({
  required Map<String, Map<String, dynamic>> responses,
  required void Function(Map<String, dynamic> request) onRequest,
}) async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);

  unawaited(() async {
    await for (final req in server) {
      try {
        final bodyText = await utf8.decoder.bind(req).join();
        final body = jsonDecode(bodyText) as Map<String, dynamic>;
        onRequest(body);

        final method = body['method'] as String?;
        if (method == null) {
          req.response.statusCode = 400;
          await req.response.close();
          continue;
        }

        final result = responses[method];
        if (result == null) {
          // Simulate JSON-RPC error
          req.response.headers.contentType = ContentType.json;
          req.response.write(jsonEncode({
            'jsonrpc': '2.0',
            'id': body['id'],
            'error': {'code': -32601, 'message': 'Method not found: $method'},
          }));
          await req.response.close();
          continue;
        }

        req.response.headers.contentType = ContentType.json;
        req.response.write(jsonEncode({
          'jsonrpc': '2.0',
          'id': body['id'],
          'result': result,
        }));
        await req.response.close();
      } catch (_) {
        // Best-effort for test server
        try {
          req.response.statusCode = 500;
          await req.response.close();
        } catch (_) {}
      }
    }
  }());

  return server;
}

void main() {
  test('WalletRpcClient smoke: can call core methods against JSON-RPC server', () async {
    final seen = <String, Map<String, dynamic>>{};

    final responses = <String, Map<String, dynamic>>{
      'get_version': {'version': 100},
      'get_languages': {'languages': ['English']},
      'create_wallet': <String, dynamic>{},
      'open_wallet': <String, dynamic>{},
      'get_address': {'address': '44SMOKE_TEST_ADDRESS'},
      'validate_address': {
        'valid': true,
        'integrated': false,
        'subaddress': false,
        'nettype': 'mainnet',
        'openalias_address': '',
      },
      'get_balance': {
        'balance': '123',
        'unlocked_balance': '100',
        'multisig_import_needed': false,
        'time_to_unlock': 0,
        'blocks_to_unlock': 0,
        'per_subaddress': [],
      },
      'refresh': {
        'blocks_fetched': 1,
        'received_money': false,
      },
      'sign': {'signature': 'SIG'},
      'verify': {'good': true},
      'is_multisig': {
        'multisig': false,
        'ready': true,
        'threshold': 0,
        'total': 0,
      },
    };

    final server = await _startJsonRpcServer(
      responses: responses,
      onRequest: (req) {
        final method = req['method'] as String?;
        if (method != null) {
          seen[method] = req;
        }
      },
    );

    final client = WalletRpcClient(
      host: server.address.address,
      port: server.port,
      timeout: const Duration(seconds: 2),
    );

    try {
      final version = await client.getVersion();
      expect(version.version, 100);

      final langs = await client.getLanguages();
      expect(langs, contains('English'));

      await client.createWallet(filename: 'w1', password: 'pw', language: 'English');
      await client.openWallet(filename: 'w1', password: 'pw');

      final address = await client.getAddress(accountIndex: 0);
      expect(address.address, startsWith('44'));

      final validate = await client.validateAddress(
        address: address.address,
        anyNetType: true,
        allowOpenalias: true,
      );
      expect(validate.valid, isTrue);

      final balance = await client.getBalance();
      expect(balance.balance, BigInt.from(123));
      expect(balance.unlockedBalance, BigInt.from(100));

      final refresh = await client.refresh();
      expect(refresh.blocksFetched, 1);

      final sig = await client.sign('hello');
      expect(sig, 'SIG');

      final good = await client.verify(
        data: 'hello',
        address: address.address,
        signature: sig,
      );
      expect(good, isTrue);

      final multisig = await client.isMultisig();
      expect(multisig.multisig, isFalse);

      // Assert request payload shape for a couple of core calls.
      expect(seen['get_version']?['jsonrpc'], '2.0');
      expect(seen['create_wallet']?['params']?['filename'], 'w1');
      expect(seen['create_wallet']?['params']?['password'], 'pw');
      expect(seen['get_address']?['params']?['account_index'], 0);
      expect(seen['validate_address']?['params']?['allow_openalias'], true);
      expect(seen['validate_address']?['params']?['any_net_type'], true);
    } finally {
      client.close();
      await server.close(force: true);
    }
  });
}
