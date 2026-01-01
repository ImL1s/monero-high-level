import 'dart:async';
import 'dart:io';

import 'package:monero_dart/src/network/daemon_client.dart';
import 'package:monero_dart/src/network/wallet_rpc_client.dart';
import 'package:test/test.dart';

bool _isEnabled() => Platform.environment['MONERO_REGTEST_E2E'] == '1';

bool _hasBinaries() {
  final monerod = Process.runSync('which', ['monerod']);
  final walletRpc = Process.runSync('which', ['monero-wallet-rpc']);
  return monerod.exitCode == 0 && walletRpc.exitCode == 0;
}

Future<int> _pickFreePort() async {
  final socket = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
  final port = socket.port;
  await socket.close();
  return port;
}

Future<void> _waitForDaemonReady(
  DaemonClient client, {
  Duration timeout = const Duration(seconds: 30),
}) async {
  final deadline = DateTime.now().add(timeout);
  Object? lastError;

  while (DateTime.now().isBefore(deadline)) {
    try {
      final height = await client.getHeight();
      if (height >= 0) return;
    } catch (e) {
      lastError = e;
    }

    await Future<void>.delayed(const Duration(milliseconds: 250));
  }

  final suffix = lastError == null ? '' : ' (last error: $lastError)';
  throw TimeoutException('daemon not ready$suffix', timeout);
}

Future<void> _waitForWalletRpcReady(
  WalletRpcClient client, {
  Duration timeout = const Duration(seconds: 30),
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
  final binariesOk = _hasBinaries();

  test(
    'wallet-rpc regtest E2E: mine -> transfer -> confirm',
    () async {
      final baseDir = Directory(
        '.dart_tool/monero_regtest_${DateTime.now().millisecondsSinceEpoch}',
      );
      final daemonDir = Directory('${baseDir.path}/daemon');
      final walletDir = Directory('${baseDir.path}/wallets');

      daemonDir.createSync(recursive: true);
      walletDir.createSync(recursive: true);

      // Use fixed high ports to avoid clashing (dynamic ports can race).
      const daemonRpcPort = 48081;
      const daemonP2pPort = 48080;
      const walletRpcPort = 48084;

      final daemonProc = await Process.start(
        'monerod',
        [
          '--regtest',
          '--non-interactive',
          '--no-igd',
          '--hide-my-port',
          '--data-dir',
          daemonDir.absolute.path,
          '--rpc-bind-ip',
          '127.0.0.1',
          '--rpc-bind-port',
          '$daemonRpcPort',
          '--p2p-bind-ip',
          '127.0.0.1',
          '--p2p-bind-port',
          '$daemonP2pPort',
          '--log-level',
          '0',
        ],
        mode: ProcessStartMode.detachedWithStdio,
      );

      // Best-effort logging for debugging.
      unawaited(daemonProc.stdout.transform(const SystemEncoding().decoder).drain<void>());
      unawaited(daemonProc.stderr.transform(const SystemEncoding().decoder).drain<void>());

      final daemonClient = DaemonClient(
        host: '127.0.0.1',
        port: daemonRpcPort,
        timeout: const Duration(seconds: 5),
      );

      Process? walletRpcProc;
      final walletClient = WalletRpcClient(
        host: '127.0.0.1',
        port: walletRpcPort,
        timeout: const Duration(seconds: 10),
      );

      try {
        await _waitForDaemonReady(daemonClient, timeout: const Duration(seconds: 60));
        print('[E2E] Daemon ready');

        walletRpcProc = await Process.start(
          'monero-wallet-rpc',
          [
            '--regtest',
            '--daemon-address',
            '127.0.0.1:$daemonRpcPort',
            '--trusted-daemon',
            '--wallet-dir',
            walletDir.absolute.path,
            '--rpc-bind-ip',
            '127.0.0.1',
            '--rpc-bind-port',
            '$walletRpcPort',
            '--disable-rpc-login',
            '--log-level',
            '1',
          ],
          mode: ProcessStartMode.detachedWithStdio,
        );

        unawaited(walletRpcProc.stdout.transform(const SystemEncoding().decoder).drain<void>());
        unawaited(walletRpcProc.stderr.transform(const SystemEncoding().decoder).drain<void>());

        print('[E2E] Waiting for wallet-rpc on port $walletRpcPort...');
        await _waitForWalletRpcReady(walletClient, timeout: const Duration(seconds: 90));

        final minerWallet = 'miner_${DateTime.now().millisecondsSinceEpoch}';
        final recvWallet = 'recv_${DateTime.now().millisecondsSinceEpoch}';
        const walletPassword = 'test-password';

        // 1) Create/open miner wallet
        await walletClient.createWallet(filename: minerWallet, password: walletPassword);
        await walletClient.openWallet(filename: minerWallet, password: walletPassword);

        final minerAddr = await walletClient.getAddress();
        expect(minerAddr.address, isNotEmpty);

        // 2) Mine enough blocks so coinbase matures (Monero uses ~60 block unlock)
        final startHeight = await daemonClient.getHeight();
        print('[E2E] Initial height: $startHeight, mining 70 blocks...');

        final newHeight = await daemonClient.generateblocks(
          amountOfBlocks: 70,
          walletAddress: minerAddr.address,
        );
        print('[E2E] Generated blocks, new height: $newHeight');

        await walletClient.refresh();
        final minerBalance = await walletClient.getBalance();
        print('[E2E] Miner balance: ${minerBalance.unlockedBalance}');
        expect(minerBalance.unlockedBalance > BigInt.zero, isTrue);

        // 3) Create/open receiver wallet and get address
        await walletClient.createWallet(filename: recvWallet, password: walletPassword);
        await walletClient.openWallet(filename: recvWallet, password: walletPassword);

        final recvAddr = await walletClient.getAddress();
        expect(recvAddr.address, isNotEmpty);

        // 4) Send from miner -> receiver
        await walletClient.openWallet(filename: minerWallet, password: walletPassword);

        final amount = BigInt.from(1000000000); // 0.001 XMR in atomic units
        print('[E2E] Transferring $amount from miner to receiver...');
        final transfer = await walletClient.transfer(
          destinations: [
            TransferDestination(address: recvAddr.address, amount: amount),
          ],
        );
        print('[E2E] Transfer TX hash: ${transfer.txHash}');
        expect(transfer.txHash, isNotEmpty);

        // 5) Mine 10 blocks to confirm and unlock
        print('[E2E] Mining 10 blocks to confirm transaction...');
        await daemonClient.generateblocks(
          amountOfBlocks: 10,
          walletAddress: minerAddr.address,
        );

        await walletClient.openWallet(filename: recvWallet, password: walletPassword);
        await walletClient.refresh();
        final recvBalance = await walletClient.getBalance();
        print('[E2E] Receiver balance: ${recvBalance.balance}');
        expect(recvBalance.balance >= amount, isTrue);
      } finally {
        walletClient.close();
        daemonClient.close();

        try {
          walletRpcProc?.kill(ProcessSignal.sigterm);
        } catch (_) {}

        try {
          daemonProc.kill(ProcessSignal.sigterm);
        } catch (_) {}
      }
    },
    skip: enabled
        ? (binariesOk ? null : 'monerod/monero-wallet-rpc not found in PATH')
        : 'Set MONERO_REGTEST_E2E=1 to run regtest E2E',
    timeout: const Timeout(Duration(minutes: 3)),
  );
}
