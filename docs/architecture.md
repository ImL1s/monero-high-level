# 架構與模組導覽

本 repo 主要包含兩套**獨立實作**：KMP 與 Dart。兩者不共享核心程式碼，僅共享「規格/向量/驗收」的概念。

## 目錄結構（高階）

- `kmp/`：Kotlin Multiplatform 實作
  - `monero-crypto/`：密碼學 primitives
  - `monero-core/`：地址/助記詞/金鑰/交易核心
  - `monero-net/`：daemon RPC client
  - `monero-storage/`：持久化
  - `monero-wallet/`：高階錢包 API
- `dart/`：純 Dart 實作
  - `lib/src/crypto/`：Keccak、Ed25519、Base58、BP+、CLSAG…
  - `lib/src/core/`：地址/助記詞/金鑰
  - `lib/src/network/`：`DaemonClient`、`WalletRpcClient`、RPC utils
  - `lib/src/transaction/`：交易建構/序列化/掃描
  - `lib/src/sync/`：同步管理
  - `lib/src/wallet/`：高階錢包 API
- `vectors/`：測試向量（地址派生、tx 範例等）
- `docs/`：規格與設計文件

## 重要文件

- 設計：`docs/design.md`
- 需求/範圍：`docs/requirements.md`
- 任務清單：`docs/task.md`

## 一致性策略

- **共同規格**：兩邊在同協議版本下遵循同一份參數與資料模型約束。
- **共同向量**：相同 seed/mnemonic 下的地址、子地址、金鑰、（以及進階時）交易建構結果。
- **Oracle 對照**：以 `monerod`、`monero-wallet-cli`、`monero-wallet-rpc` 的輸出作為標準答案。

> 實務上，這代表你可以用官方工具驗證本庫的輸出是否正確。
