# E2E / regtest 測試指南（Dart）

本專案的 Dart 端提供兩種 E2E：

1. **Offline E2E**：只啟動 `monero-wallet-rpc --offline`（不需要 daemon）。
2. **Regtest E2E**：自動啟動 `monerod --regtest` + `monero-wallet-rpc`，用 daemon RPC `generateblocks` 挖礦，完成「挖礦 → 轉帳 → 確認」的端到端流程。

## 前置需求

- Dart SDK（在 repo 內 `dart/` 目錄可執行 `dart test`）
- `monerod` 與 `monero-wallet-rpc` 可在 PATH 找到
  - macOS（Homebrew）：

```bash
brew install monero
```

> 檢查：`which monerod` / `which monero-wallet-rpc`。

## Offline E2E（wallet-rpc 離線）

測試檔：`dart/test/e2e/wallet_rpc_e2e_test.dart`

執行：

```bash
cd dart
MONERO_E2E=1 dart test test/e2e/wallet_rpc_e2e_test.dart
```

做了什麼：
- 自動啟動 `monero-wallet-rpc`（參數包含 `--offline --no-initial-sync`）
- 透過 `WalletRpcClient` 驗證常見 RPC：create/open wallet、getAddress、validateAddress、integrated address、sign/verify…

常見問題：
- 如果 `wallet-rpc not ready`：多半是 `monero-wallet-rpc` 還沒 bind port，或 PATH 找不到 binary。

## Regtest E2E（完整本地私鏈）

測試檔：`dart/test/e2e/wallet_rpc_regtest_e2e_test.dart`

執行：

```bash
cd dart
MONERO_REGTEST_E2E=1 dart test test/e2e/wallet_rpc_regtest_e2e_test.dart
```

做了什麼（摘要）：
- 啟動 daemon：
  - `monerod --regtest --offline --fixed-difficulty=1`
  - RPC：`127.0.0.1:48081`
  - P2P：`127.0.0.1:48080`
- 啟動 wallet-rpc：
  - 連到上面的 regtest daemon
  - RPC：`127.0.0.1:48084`
  - 重要參數：`--allow-mismatched-daemon-version`（避免 regtest 環境下的版本/硬分叉檢查卡住）
- 建立 miner/receiver wallets
- 透過 daemon RPC `generateblocks` 挖 100 blocks（確保 coinbase maturity 與可用 outputs）
- 進行轉帳，再挖 10 blocks 確認，receiver refresh 後看到入帳

### 為什麼要挖 100 blocks？

Monero 的 coinbase 需要成熟（約 60 blocks 才可花費），而轉帳建構也需要足夠可用 outputs。挖太少常見會看到 `not enough outputs to use Please use sweep_dust.`。

### Daemon `generateblocks` 回 `status: "BUSY"`

daemon 剛啟動時，`generateblocks` 可能回：
- `status: "BUSY"`
- `height: 0`

本專案的 `DaemonClient.generateblocks()` 已加入重試；此外 regtest daemon 以 `--offline` 啟動，避免因網路/DNS/peer 行為導致長時間 busy。

## Port/Process 衝突

regtest E2E 會使用固定 ports：
- daemon RPC：48081
- daemon P2P：48080
- wallet-rpc：48084

如果你本機已有其他 monero 相關程序佔用這些 ports，測試會嘗試 `pkill` 清理，但仍建議：
- 關掉你手動啟動的 monerod / wallet-rpc
- 或修改測試檔的 ports（若你有自訂需求）

## 進階：只跑單元測試

```bash
cd dart

dart test test/core test/crypto test/network
```
