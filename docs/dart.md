# Dart 套件使用說明（monero_dart）

本文件說明如何使用本專案的 Dart 套件（純 Dart 實作）來做：
- 助記詞/金鑰/地址
- 與 `monerod`（daemon RPC）互動
- 與 `monero-wallet-rpc`（wallet RPC）互動
- 進行 E2E / regtest 測試

> 注意：本 repo 的 KMP 與 Dart 是兩套完全獨立實作，不共享核心程式碼。一致性靠測試向量與 Oracle 對照。

## 安裝方式

### 1) Repo 內開發（最簡單）

在 repo 內：

```bash
cd dart
dart pub get
```

### 2) 以 `path` 方式引用（同一台機器）

你的專案 `pubspec.yaml`：

```yaml
dependencies:
  monero_dart:
    path: ../monero_high_level/dart
```

### 3) 以 `git` 方式引用

你的專案 `pubspec.yaml`：

```yaml
dependencies:
  monero_dart:
    git:
      url: https://github.com/ImL1s/monero-high-level
      path: dart
```

## 公開 API 入口

建議從單一入口匯入：

```dart
import 'package:monero_dart/monero_dart.dart';
```

此入口會 re-export 常用模組（crypto/core/network/storage/transaction/sync/wallet）。

## 範例：助記詞、金鑰與地址

```dart
import 'dart:math';
import 'dart:typed_data';

import 'package:monero_dart/monero_dart.dart';

void main() {
  // 產生 32 bytes entropy（示範用；正式應用請確保是 CSPRNG）
  final rng = Random.secure();
  final entropy = Uint8List.fromList(List<int>.generate(32, (_) => rng.nextInt(256)));

  final mnemonic = Mnemonic.entropyToMnemonic(entropy);
  print(mnemonic.join(' '));

  final keys = MoneroKeys.fromSeed(entropy);
  final address = MoneroAddress.fromKeys(
    publicSpendKey: keys.publicSpendKey,
    publicViewKey: keys.publicViewKey,
    network: Network.mainnet,
  );

  print(address.address);
}
```

> 金額單位：Monero 常用最小單位為 atomic units（piconero），1 XMR = 10^12 atomic units。

## 範例：呼叫 `monerod`（Daemon RPC）

```dart
import 'package:monero_dart/monero_dart.dart';

Future<void> main() async {
  final daemon = DaemonClient(host: '127.0.0.1', port: 18081);

  final info = await daemon.getInfo();
  print('height=${info.height} version=${info.version}');

  final fee = await daemon.getFeeEstimate();
  print('feePerByte=${fee.feePerByte}');

  daemon.close();
}
```

### Regtest 快速挖礦：`generateblocks`

在 regtest（或支援 generateblocks 的測試環境）可以用：

```dart
final height = await daemon.generateblocks(
  amountOfBlocks: 100,
  walletAddress: minerAddress,
);
```

此方法內建對 `status: "BUSY"` 的重試，用於 daemon 剛啟動尚未 ready 的時候。

## 範例：呼叫 `monero-wallet-rpc`（Wallet RPC）

> 這裡示範的是「控制官方 wallet-rpc」的 RPC client，並非本專案的純 Dart 錢包核心。

```dart
import 'package:monero_dart/monero_dart.dart';

Future<void> main() async {
  final wallet = WalletRpcClient(host: '127.0.0.1', port: 18082);

  final version = await wallet.getVersion();
  print('wallet-rpc version=${version.version}');

  // 需先 create/open wallet，才可做大部分操作
  await wallet.createWallet(filename: 'demo', password: 'pw');
  await wallet.openWallet(filename: 'demo', password: 'pw');

  final addr = await wallet.getAddress();
  print('address=${addr.address}');

  final balance = await wallet.getBalance();
  print('balance=${balance.balance} unlocked=${balance.unlockedBalance}');

  await wallet.store();
  await wallet.closeWallet();
  wallet.close();
}
```

## 測試

在 repo 內執行（Dart）：

```bash
cd dart

dart test
```

E2E / regtest 的執行方式與注意事項請看 [docs/e2e.md](e2e.md)。
