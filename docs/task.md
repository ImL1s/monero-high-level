# 開發任務清單 (Task List)

本文件列出實作 Monero Kotlin 與 Dart 錢包庫的具體任務，建議依序執行。

重要原則：

- Kotlin(KMP) 與 Dart 為**兩套獨立復刻實作**，不共享核心程式碼。
- 共同點以「共同規格 + 共同測試向量 + Oracle 對照」確保一致性。

## Phase 1: 基礎建設與密碼學 (Foundation & Crypto)

### Kotlin (KMP)
- [x] **K1.1**: 建立 KMP 專案結構 (`monero-core`, `monero-crypto`, `monero-net`).
- [x] **K1.2**: 實作/整合大數運算庫 (BigInt). *(使用 ByteArray 純運算)*
- [x] **K1.3**: 實作 Keccak-256 (SHA-3) 哈希函數.
- [x] **K1.4**: 實作 Ed25519 基礎運算 (Scalar reduction, Point addition/multiplication).
- [x] **K1.5**: 實作 Base58Check 編碼與解碼.
- [x] **K1.6**: 實作助記詞 (Mnemonic) 轉換邏輯 (Seed <-> Mnemonic, Checksum).
- [x] **K1.7**: 實作金鑰衍生 (Seed -> Spend/View Keys) 與地址生成 (Standard/Subaddress).

### Dart
- [x] **D1.1**: 建立 Dart Package 專案結構.
- [x] **D1.2**: 引入 `crypto`, `pointycastle` 或使用 `monero_dart` 作為基礎.
- [x] **D1.3**: 驗證 BigInt 效能與精度.
- [x] **D1.4**: 實作/驗證 Ed25519 與 Keccak 運算.
- [x] **D1.5**: 實作 Base58Check 與地址生成邏輯.
- [ ] **D1.6**: 實作助記詞管理.

## Phase 2: 網路與 RPC (Network & RPC)

### Kotlin (KMP)
- [ ] **K2.1**: 引入 Ktor Client.
- [ ] **K2.2**: 定義 JSON-RPC Request/Response 資料模型 (`@Serializable`).
- [ ] **K2.3**: 實作 `DaemonClient` (get_info, get_block, send_raw_transaction).
- [ ] **K2.4**: 撰寫連線測試 (連接 Stagenet 節點).

### Dart
- [ ] **D2.1**: 引入 `http` 或 `dio`.
- [ ] **D2.2**: 定義 RPC 資料模型.
- [ ] **D2.3**: 實作 `DaemonRpcClient`.
- [ ] **D2.4**: 實作 RPC 錯誤處理與重試機制.

## Phase 3: 區塊同步與掃描 (Sync & Scanning)

### Kotlin (KMP)
- [ ] **K3.1**: 定義 `Transaction` 與 `Output` 資料結構.
- [ ] **K3.2**: 實作 `ViewKeyScanner`: 判斷 Output 是否屬於錢包 (Derive Public Key).
- [ ] **K3.3**: 實作 `SyncManager`: 協調區塊下載與掃描流程 (Coroutines).
- [ ] **K3.4**: 實作區塊鏈重組 (Reorg) 偵測與處理.
- [ ] **K3.5**: 實作同步事件通知（sync progress / new tx / received output）(Flow/Listener).

### Dart
- [ ] **D3.1**: 定義錢包資料結構.
- [ ] **D3.2**: 實作 `Scanner` 邏輯.
- [ ] **D3.3**: 建立 Isolate Worker 機制，將掃描運算移至背景.
- [ ] **D3.4**: 實作同步進度 Stream.
- [ ] **D3.5**: 實作事件通知（sync progress / new tx / received output）(Stream/Callbacks).

## Phase 4: 交易建構與簽署 (Transaction & Signing)

### Kotlin (KMP)
- [ ] **K4.1**: 實作 UTXO 選擇策略 (Input Selection).
- [ ] **K4.2**: 實作 `DecoySelector` (從 RPC 獲取 Ring Members).
- [ ] **K4.3**: 實作 Pedersen Commitments.
- [ ] **K4.4**: 實作 Bulletproofs+ (Range Proof) 生成與驗證.
- [ ] **K4.5**: 實作 CLSAG 環簽章演算法.
- [ ] **K4.6**: 實作交易序列化 (Binary format).
- [ ] **K4.7**: 整合 `TxBuilder`: 組合上述步驟產生 Signed Tx.
- [ ] **K4.8**: 支援常用交易類型：一般轉帳、批次轉帳、sweep/sweep_all（視需求分批完成）。
- [ ] **K4.9**: 離線簽名格式：export unsigned / import signed / relay.

### Dart
- [ ] **D4.1**: 移植/實作 UTXO 選擇與 Decoy 獲取.
- [ ] **D4.2**: 實作/驗證 Bulletproofs+ (參考 `monero_dart`).
- [ ] **D4.3**: 實作/驗證 CLSAG.
- [ ] **D4.4**: 實作交易序列化.
- [ ] **D4.5**: 實作 `TxBuilder`.
- [ ] **D4.6**: 支援常用交易類型：一般轉帳、批次轉帳、sweep/sweep_all（視需求分批完成）。
- [ ] **D4.7**: 離線簽名格式：export unsigned / import signed / relay.

## Phase 5: 儲存與進階功能 (Storage & Advanced)

### Kotlin (KMP)
- [ ] **K5.0**: 錢包生命週期與帳戶/子地址管理（create/open/close/restore；view-only/offline 類型；accounts/subaddresses/labels；各帳戶餘額）.
- [ ] **K5.1**: 實作 `WalletStorage` (SQLDelight 或 File).
- [ ] **K5.2**: 實作錢包檔案加密 (ChaCha20-Poly1305).
- [ ] **K5.3**: 實作多重簽名 (Multisig) 流程 (Optional).
- [ ] **K5.4**: 交易/轉帳/輸出查詢 API（依 hash/方向/確認狀態/account/subaddress）.
- [ ] **K5.5**: Address Book + Tx Notes（新增/刪除/查詢/更新）.
- [ ] **K5.6**: Outputs 管理：freeze/thaw、匯出/匯入 outputs 與 key images（支援 view-only/offline 交換）.
- [ ] **K5.7**: 證明工具（tx proof / reserve proof / message sign/verify）(Optional).

### Dart
- [ ] **D5.0**: 錢包生命週期與帳戶/子地址管理（create/open/close/restore；view-only/offline 類型；accounts/subaddresses/labels；各帳戶餘額）.
- [ ] **D5.1**: 實作本地儲存 (Hive, SQLite, or File).
- [ ] **D5.2**: 實作錢包加密保存.
- [ ] **D5.3**: 實作多重簽名支援 (Optional).
- [ ] **D5.4**: 交易/轉帳/輸出查詢 API（依 hash/方向/確認狀態/account/subaddress）.
- [ ] **D5.5**: Address Book + Tx Notes（新增/刪除/查詢/更新）.
- [ ] **D5.6**: Outputs 管理：freeze/thaw、匯出/匯入 outputs 與 key images（支援 view-only/offline 交換）.
- [ ] **D5.7**: 證明工具（tx proof / reserve proof / message sign/verify）(Optional).

## Phase 6: 整合與測試 (Integration & Testing)

- [ ] **T1**: 建立 Unit Test Suite (針對 Crypto 與 Address).
	- DoD: 最少覆蓋 Keccak/Ed25519/Base58/mnemonic/key derivation/address/subaddress；可重現且不依賴網路。
- [ ] **T2**: 建立 Integration Test (針對 RPC 與 Sync).
	- DoD: 對 stagenet 節點可跑通 get_info/get_block/send_raw_transaction；sync 可從指定高度跑到最新且可中斷/續跑。
- [ ] **T3**: Stagenet 實戰測試 (發送/接收交易).
	- DoD: 至少完成一次「A→B 轉帳」：B 掃到入帳、等待確認、餘額/狀態正確；並驗證 tx 在 pool/confirmed 的狀態轉移。
- [ ] **T4**: 效能優化 (Profiling & Optimization).
	- DoD: 有一份基準（同步 N 個區塊或掃描 M 筆 tx 的耗時/記憶體）；針對瓶頸提出並落地至少 1 個優化。
- [ ] **T5**: 撰寫 API 文件與範例程式碼.
	- DoD: 最少提供 create/restore、connect daemon、sync、get balance、send tx（或 build+relay）範例；並標出平台限制（含 Web）。

## Phase 7: 兩套實作一致性驗收 (KMP vs Dart Conformance)

- [ ] **C1**: 建立共用測試向量集（不共享程式碼，僅共享向量檔/預期輸出）：mnemonic/seed/address/subaddress。
	- DoD: 向量檔至少包含 1 組主網 + 1 組 stagenet；每組含 primary + N 組 subaddress 預期輸出。
- [ ] **C2**: 建立 Oracle 對照腳本：同輸入下呼叫 `monero-wallet-cli` / `monero-wallet-rpc` 取得標準結果。
	- DoD: 同一份輸入（seed/restore height/config）可產出「標準答案」並可重複執行；輸出格式固定（JSON/檔案皆可）。
- [ ] **C3**: KMP 對向量驗收：地址/子地址/key derivation 結果全數通過。
	- DoD: 以 C1 的向量跑過；任何差異都能被定位到欄位（哪個地址/哪個 index）。
- [ ] **C4**: Dart 對向量驗收：地址/子地址/key derivation 結果全數通過。
	- DoD: 同 C3；並確保 Web/VM/Flutter（如有）行為一致。
- [ ] **C5**: 交易最小路徑一致性：同 inputs/outputs/config 下，兩套產生的 tx 必須可被 `monerod` 接受；並對照 Oracle（費用、hash、key images）。
	- DoD: 兩套各自產出可被 `monerod` 接受的 tx；並能對照 Oracle 的 fee/key images 等關鍵輸出在可比範圍內一致。
