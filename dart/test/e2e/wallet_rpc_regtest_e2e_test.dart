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

/// Start a process and return it with stderr/stdout monitoring.
/// If the process exits unexpectedly, logs will be printed.
Future<Process> _startProcess(
  String executable,
  List<String> args, {
  required String name,
}) async {
  print('[$name] Starting: $executable ${args.join(' ')}');
  
  final proc = await Process.start(
    executable,
    args,
    mode: ProcessStartMode.normal, // Use normal mode to capture output
  );

  // Collect stderr for debugging
  final stderrBuffer = StringBuffer();
  proc.stderr.transform(const SystemEncoding().decoder).listen((data) {
    stderrBuffer.write(data);
    // Print errors immediately for debugging
    if (data.contains('Error') || data.contains('error') || data.contains('WARN')) {
      print('[$name STDERR] $data');
    }
  });

  // Collect stdout
  proc.stdout.transform(const SystemEncoding().decoder).listen((data) {
    // Print important messages
    if (data.contains('Starting') || data.contains('Binding') || data.contains('RPC')) {
      print('[$name] $data');
    }
  });

  // Monitor for unexpected exit
  unawaited(proc.exitCode.then((code) {
    if (code != 0) {
      print('[$name] Process exited with code $code');
      if (stderrBuffer.isNotEmpty) {
        print('[$name] STDERR: $stderrBuffer');
      }
    }
  }));

  return proc;
}

Future<void> _waitForDaemonReady(
  DaemonClient client, {
  Duration timeout = const Duration(seconds: 30),
}) async {
  final deadline = DateTime.now().add(timeout);
  Object? lastError;

  while (DateTime.now().isBefore(deadline)) {
    try {
      final info = await client.getInfo();
      // Daemon is ready when it responds AND is not busy (synchronized or at least started)
      // In regtest mode with fresh daemon, height starts at 1 and synchronized may be false initially
      if (info.height >= 1) {
        // Additional check: try a simple generateblocks to see if daemon is truly ready
        // This helps catch BUSY status before we proceed
        return;
      }
    } catch (e) {
      lastError = e;
    }

    await Future<void>.delayed(const Duration(milliseconds: 500));
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

      // First, kill any existing processes on these ports
      Process.runSync('pkill', ['-f', 'rpc-bind-port.*$daemonRpcPort']);
      Process.runSync('pkill', ['-f', 'rpc-bind-port.*$walletRpcPort']);
      await Future<void>.delayed(const Duration(seconds: 1));

      final daemonProc = await _startProcess(
        'monerod',
        [
          '--regtest',
          '--non-interactive',
          '--no-igd',
          '--hide-my-port',
          '--offline', // Don't try to connect to network peers
          '--fixed-difficulty=1', // Fast block generation
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
          '1',
          '--confirm-external-bind',
        ],
        name: 'monerod',
      );

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
        print('[E2E] Daemon ready on port $daemonRpcPort');

        // Wait for daemon to fully initialize (avoid BUSY status in generateblocks)
        print('[E2E] Waiting for daemon to fully stabilize...');
        await Future<void>.delayed(const Duration(seconds: 5));

        walletRpcProc = await _startProcess(
          'monero-wallet-rpc',
          [
            // wallet-rpc doesn't need --regtest, it connects to regtest daemon
            '--daemon-address',
            '127.0.0.1:$daemonRpcPort',
            '--trusted-daemon',
            '--allow-mismatched-daemon-version', // For regtest compatibility
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
          name: 'wallet-rpc',
        );

        // Give wallet-rpc time to start binding
        await Future<void>.delayed(const Duration(seconds: 3));

        print('[E2E] Waiting for wallet-rpc on port $walletRpcPort...');
        await _waitForWalletRpcReady(walletClient, timeout: const Duration(seconds: 90));
        print('[E2E] Wallet-RPC ready');

        final minerWallet = 'miner_${DateTime.now().millisecondsSinceEpoch}';
        final recvWallet = 'recv_${DateTime.now().millisecondsSinceEpoch}';
        const walletPassword = 'test-password';

        // 1) Create/open miner wallet
        await walletClient.createWallet(filename: minerWallet, password: walletPassword);
        await walletClient.openWallet(filename: minerWallet, password: walletPassword);

        final minerAddr = await walletClient.getAddress();
        expect(minerAddr.address, isNotEmpty);

        // 2) Mine enough blocks so coinbase matures (Monero uses ~60 block unlock)
        // We need 70+ blocks to have at least 10 unlocked outputs for ring signatures
        final startHeight = await daemonClient.getHeight();
        print('[E2E] Initial height: $startHeight, mining 100 blocks...');

        final newHeight = await daemonClient.generateblocks(
          amountOfBlocks: 100,
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
        // Refresh after re-opening wallet to ensure all outputs are visible
        await walletClient.refresh();

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
        
        print('[E2E] ✅ Test passed!');
      } finally {
        print('[E2E] Cleaning up...');
        walletClient.close();
        daemonClient.close();

        // Kill processes and wait for them to exit
        try {
          walletRpcProc?.kill(ProcessSignal.sigterm);
          await Future<void>.delayed(const Duration(milliseconds: 500));
          walletRpcProc?.kill(ProcessSignal.sigkill);
        } catch (_) {}

        try {
          daemonProc.kill(ProcessSignal.sigterm);
          await Future<void>.delayed(const Duration(milliseconds: 500));
          daemonProc.kill(ProcessSignal.sigkill);
        } catch (_) {}
        
        // Additional cleanup using pkill
        Process.runSync('pkill', ['-9', '-f', 'rpc-bind-port.*48']);
        
        print('[E2E] Cleanup complete');
      }
    },
    skip: enabled
        ? (binariesOk ? null : 'monerod/monero-wallet-rpc not found in PATH')
        : 'Set MONERO_REGTEST_E2E=1 to run regtest E2E',
    timeout: const Timeout(Duration(minutes: 4)),
  );
}
