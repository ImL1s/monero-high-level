# 貢獻指南（Contributing）

歡迎 PR / Issue。這份指南聚焦在「怎麼在本 repo 有效開發與驗證」。

## 開發原則

- KMP 與 Dart 是兩套獨立實作，不共享核心程式碼。
- 任何演算法變更請同步更新：
  - 測試（至少單元測試與向量測試）
  - 文件（若行為/參數有變）

## 跑測試

### Dart

```bash
cd dart

dart test
```

E2E（需要本機有 monero binaries）：

```bash
cd dart

# offline wallet-rpc E2E
MONERO_E2E=1 dart test test/e2e/wallet_rpc_e2e_test.dart

# regtest E2E
MONERO_REGTEST_E2E=1 dart test test/e2e/wallet_rpc_regtest_e2e_test.dart
```

### KMP

```bash
cd kmp
./gradlew build
```

（更完整指引請參考 `kmp/README.md`）

## 新增/修改 RPC methods（Dart）

- `DaemonClient`：`dart/lib/src/network/daemon_client.dart`
- `WalletRpcClient`：`dart/lib/src/network/wallet_rpc_client.dart`

建議做法：
1. 先找官方 RPC 文件或 `monero-wallet-rpc`/`monerod` 的對應 method 名稱與參數。
2. 新增 request/response model（若需要），並確保大數欄位使用 `BigInt` 或以字串解析。
3. 補測試：
   - 純 parsing/序列化可用單元測試
   - 需要真實 daemon/wallet-rpc 的，放 E2E

## PR checklist

- 單元測試通過：Dart `dart test`；KMP `./gradlew build`
- 若動到 regtest/E2E：請至少在本機跑一次 `MONERO_REGTEST_E2E=1 ...`
- 文件更新：若新增 env var、port、或啟動參數，請同步更新 `docs/e2e.md`
