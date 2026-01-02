import 'dart:typed_data';

import 'package:monero_dart/monero_dart.dart';
import 'package:test/test.dart';

void main() {
  test('public API smoke: monero_dart.dart can be imported', () {
    // This test exists to ensure our public umbrella export stays conflict-free
    // and that a few core types remain reachable.

    final daemon = DaemonClient(host: '127.0.0.1', port: 18081);
    daemon.close();

    final wallet = WalletRpcClient(host: '127.0.0.1', port: 18082);
    wallet.close();

    // Basic value types
    expect(Network.mainnet, isA<Network>());

    // Core crypto/core objects compile
    final seed = List<int>.filled(32, 0);
    final words = Mnemonic.entropyToMnemonic(Uint8List.fromList(seed));
    expect(words.length, 25);

    final keys = MoneroKeys.fromSeed(Uint8List.fromList(seed));
    final address = MoneroAddress.fromKeys(
      publicSpendKey: keys.publicSpendKey,
      publicViewKey: keys.publicViewKey,
      network: Network.mainnet,
    );
    expect(address.address, isNotEmpty);
  });
}
