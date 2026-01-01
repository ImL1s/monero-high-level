import 'dart:async';
import 'dart:io';

import 'package:monero_dart/src/network/wallet_rpc_client.dart';
import 'package:test/test.dart';

bool _isEnabled() => Platform.environment['MONERO_E2E'] == '1';

Future<void> _waitForWalletRpcReady(
  WalletRpcClient client, {
  Duration timeout = const Duration(seconds: 20),
}) async {
  final deadline = DateTime.now().add(timeout);
  Object? lastError;

  while (DateTime.now().isBefore(deadline)) {
    try {
      final version = await client.getVersion();
      if (version.version > 0) return;
    } catch (e) {
      lastError = e;
    }

    await Future<void>.delayed(const Duration(milliseconds: 250));
  }

  final suffix = lastError == null ? '' : ' (last error: $lastError)';
  throw TimeoutException('wallet-rpc not ready$suffix', timeout);
}

void main() {
  final enabled = _isEnabled();

  test(
    'wallet-rpc offline E2E: create wallet + get address + sign/verify',
    () async {
      final tempWalletDir = Directory('.dart_tool/monero_e2e_wallets');
      if (!tempWalletDir.existsSync()) {
        tempWalletDir.createSync(recursive: true);
      }

      final port = 38083; // avoid clashing with default 18082

      final proc = await Process.start(
        'monero-wallet-rpc',
        [
          '--offline',
          '--no-initial-sync',
          '--wallet-dir',
          tempWalletDir.absolute.path,
          '--rpc-bind-ip',
          '127.0.0.1',
          '--rpc-bind-port',
          '$port',
          '--disable-rpc-login',
        ],
        mode: ProcessStartMode.detachedWithStdio,
      );

      // Best-effort logging for debugging.
      unawaited(proc.stdout.transform(const SystemEncoding().decoder).drain<void>());
      unawaited(proc.stderr.transform(const SystemEncoding().decoder).drain<void>());

      final client = WalletRpcClient(
        host: '127.0.0.1',
        port: port,
        timeout: const Duration(seconds: 5),
      );

      try {
        await _waitForWalletRpcReady(client);

        final walletName = 'e2e_${DateTime.now().millisecondsSinceEpoch}';
        const walletPassword = 'test-password';

        await client.createWallet(filename: walletName, password: walletPassword);
        await client.openWallet(filename: walletName, password: walletPassword);

        final languages = await client.getLanguages();
        expect(languages, isNotEmpty);

        final addr = await client.getAddress();
        expect(addr.address, isNotEmpty);

        final validate = await client.validateAddress(
          address: addr.address,
          anyNetType: true,
          allowOpenalias: true,
        );
        expect(validate.valid, isTrue);

        final integrated = await client.makeIntegratedAddress(
          standardAddress: addr.address,
          paymentId: '1234567890123456',
        );
        expect(integrated.integratedAddress, isNotEmpty);
        expect(integrated.paymentId, equals('1234567890123456'));

        final split = await client.splitIntegratedAddress(
          integratedAddress: integrated.integratedAddress,
        );
        expect(split.standardAddress, equals(addr.address));
        expect(split.paymentId, equals('1234567890123456'));

        final mnemonic = await client.queryKey('mnemonic');
        expect(mnemonic.key, isNotEmpty);

        const msg = 'hello-e2e';
        final sig = await client.sign(msg);
        expect(sig, isNotEmpty);

        final good = await client.verify(data: msg, address: addr.address, signature: sig);
        expect(good, isTrue);

        final multisig = await client.isMultisig();
        expect(multisig.multisig, isFalse);

        await client.store();
        await client.closeWallet();
        await client.openWallet(filename: walletName, password: walletPassword);
      } finally {
        client.close();
        proc.kill(ProcessSignal.sigterm);
      }
    },
    skip: enabled ? null : 'Set MONERO_E2E=1 to run E2E tests',
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
