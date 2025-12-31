# Monero 錢包庫重寫需求規格書 (Requirements)

## 1. 專案概述
本專案旨在將 Monero Ecosystem 的核心 C++ 錢包庫（`monero-cpp` / `wallet2`）重寫為 **Kotlin Multiplatform (KMP)** 與 **Dart (Pure Dart)** 兩個獨立版本。

重要原則：

- 兩套實作為**分別單獨復刻**（兩個 repo/兩套 codebase 的概念），**不共享核心程式碼**。
- 一致性由**共同規格 + 測試向量 + Oracle 對照（monero-wallet-cli/rpc、monerod）**保證，而非依賴共享實作。

目標是提供現代化、跨平台且易於整合的 Monero 錢包開發套件，支援 Android, iOS, Desktop (JVM/Native) 及 Web (Dart) 平台。

## 2. 目標協議版本

> **技術棧版本（2026-01-01 更新）**
>
> | 技術 | 版本 | 備註 |
> |------|------|------|
> | Monero | v0.18.4.4 "Fluorine Fermi" | 2025-11-19 發布 |
> | Kotlin | 2.3.0 | 2025-12 發布 |
> | Ktor | 3.3.3 | HTTP Client |
> | Dart SDK | 3.10.7 | stable |
> | Flutter | 3.38 | 2025-11-12 發布 |

- **Monero Protocol**: v0.18.4.4 "Fluorine Fermi" (最新主網版本，2025-11-19)
- **關鍵密碼學協定**:
    - **Ring Signatures**: CLSAG (Concise Linkable Spontaneous Anonymous Group)
    - **Range Proofs**: Bulletproofs+ (BP+)
    - **Address Format**: Base58Check (Standard, Subaddress, Integrated)
    - **PoW (僅驗證)**: RandomX (非必要，僅若需本地驗證區塊頭)

### 2.1 協議參數速查表（Consensus & Wallet Constants）

> 以下數值以 v0.18.4.4 主網為準；若協議升級請同步更新。

| 參數 | 值 | 說明 |
|---|---|---|
| **Ring Size** | 16 | 15 decoys + 1 real input；自 v0.13.0 (2018-10) 後固定 |
| **Mixin (decoys)** | 15 | = ring_size − 1 |
| **Unlock Time (blocks)** | 10 | 收款需等待 10 個區塊才可花費 |
| **Block Time** | ~2 min | 目標出塊時間 |
| **Address Prefixes (mainnet)** | 18 (standard), 42 (subaddress), 19 (integrated) | Base58 前綴 byte |
| **Address Prefixes (stagenet)** | 24 (standard), 36 (subaddress), 25 (integrated) | |
| **Address Prefixes (testnet)** | 53 (standard), 63 (subaddress), 54 (integrated) | |
| **Fee Priority Levels** | 1–4 | 1=default/low, 4=highest |
| **Dynamic Fee Base** | 依區塊大小中位數計算 | 詳見 `get_fee_estimate` RPC |
| **Tx Extra 最大長度 (pool)** | 1060 bytes | v0.18.2.2 引入 pool 限制 |
| **Max Outputs per Tx** | 16 | 硬編碼上限 |
| **Seed Length** | 256 bits (32 bytes) | |
| **Mnemonic Words** | 25 | 含 1 checksum word |
| **Key Derivation** | Keccak-256 | view_key = H_s(spend_key) |
| **Curve** | Ed25519 (twisted Edwards) | l ≈ 2^252 |
| **Hash (general)** | Keccak-256 | CryptoNight 僅用於 PoW KDF |
| **Encryption (wallet file)** | ChaCha20-Poly1305 | KDF: CryptoNight slow-hash 或 Argon2 |
| **Bulletproofs+ Max Outputs** | 16 | 單一 range proof 可涵蓋 |
| **CLSAG Signature** | 1 per input | 環簽章 |
| **Decoy Selection** | Gamma 分佈 + 最近區塊偏好 | 詳見 wallet2.cpp `pick_*` |

> 完整定義請參閱 [monero/src/cryptonote_config.h](https://github.com/monero-project/monero/blob/master/src/cryptonote_config.h)。

## 3. 功能需求 (Functional Requirements)

### 3.0 範圍分級（Must / Should / Optional）

由於 `wallet2` 功能面積非常大，本專案以「最終可達到完整對齊」為方向，但實作上採用分級交付：

- **Must（第一個可用版本必備）**：可建立/恢復錢包、同步、查餘額、建立與廣播一般轉帳交易、基礎持久化與加密、核心 RPC。
- **Should（接近 monero-cpp 常用能力）**：離線/觀察錢包資料交換、交易/輸出查詢與管理、背景同步與事件通知、掃鏈與 reorg 更完整處理。
- **Optional（高風險/高成本或情境功能）**：多簽全流程、各式證明（tx/reserve/message）、官方錢包檔（`.keys`）完全相容、Web 上的完整同步等。

> 註：若專案目標被設定為「完全對齊 monero-cpp」，則 Optional 也將進入範圍，但可晚於 Must/Should。

### 3.0.1 功能對照表（Scope Matrix：需求分級 × Task IDs）

> 本表用來把需求分級落到可開票任務；Task IDs 定義於 `docs/task.md`。

| 功能面向 | 功能 | 分級 | Kotlin (KMP) Task IDs | Dart Task IDs | 驗收條件（Given/When/Then） |
|---|---|---|---|---|---|
| Keys/Address | mnemonic/seed、key derivation、address/subaddress | Must | K1.3–K1.7 | D1.4–D1.6 | Given: 固定向量集；When: 生成 primary + N 組 subaddress；Then: 輸出與向量/Oracle 相同（C1–C4） |
| Wallet Core | create/open/close/restore；view-only/offline；account/subaddress/labels | Must | K5.0 | D5.0 | Given: seed/mnemonic+password；When: create/restore/open/close 並新增 account/subaddress；Then: 餘額與 label 可查、狀態可持久化再開啟不變 |
| RPC | daemon JSON-RPC 基礎能力（get_info/get_block/send_raw_transaction…） | Must | K2.1–K2.4 | D2.1–D2.4 | Given: stagenet 節點；When: 呼叫核心 methods；Then: 正確解析成功/失敗回應、可重試且不崩潰 |
| Sync | 掃鏈、辨識 outputs、reorg | Must | K3.1–K3.4 | D3.1–D3.4 | Given: 有已知收款的測試錢包；When: 從高度 H refresh 並製造 reorg 情境；Then: 高度回退/重掃正確、輸出與餘額一致 |
| Events | sync progress / new tx / received output | Should | K3.5 | D3.5 | Given: 同步進行中；When: 高度前進/出現新交易；Then: 事件按序發出且不重複、可取消訂閱/停止 |
| Tx Build | input selection、decoy、serialization、TxBuilder | Must | K4.1–K4.7 | D4.1–D4.5 | Given: 足夠 UTXO；When: 建構並簽名一般轉帳；Then: tx blob 可被 `monerod` 接受、並可在鏈上確認（C5） |
| Tx Types | transfer / batch / sweep / sweep_all | Should | K4.8 | D4.6 | Given: 多筆 recipients 或需清空餘額；When: 建構對應交易類型；Then: 金額/手續費/找零符合預期且可上鏈 |
| Offline Signing | export unsigned / import signed / relay | Should | K4.9 | D4.7 | Given: online + offline 兩環境；When: export unsigned → offline sign → import signed → relay；Then: tx 可上鏈且 key images/UTXO 狀態正確 |
| Storage | wallet storage + 加密保存 | Must | K5.1–K5.2 | D5.1–D5.2 | Given: 密碼；When: 儲存後重啟載入；Then: 無密碼不可解密、資料 round-trip、一致且不洩漏明文 keys |
| Query | tx/transfer/output 查詢（含 account/subaddress 條件） | Should | K5.4 | D5.4 | Given: 已產生多筆收付；When: 以條件查詢；Then: 結果集合/排序/狀態（confirmed/in-pool）正確 |
| Wallet Data | address book、tx notes | Should | K5.5 | D5.5 | Given: 新增/更新資料；When: 重啟載入並查詢；Then: 欄位完整保留且不影響核心同步/交易 |
| Output Mgmt | freeze/thaw；匯出/匯入 outputs 與 key images | Should | K5.6 | D5.6 | Given: 可花費輸出；When: freeze 後建 tx、再 thaw；Then: freeze 的輸出不被選入、匯出入後花費狀態一致 |
| Proofs | tx proof / reserve proof / message sign/verify | Optional | K5.7 | D5.7 | Given: 既有 tx/地址；When: 生成 proof 並用官方或另一實作驗證；Then: verify 通過、錯誤輸入會失敗 |
| Multisig | N-of-M、多簽交易流程 | Optional | K5.3 | D5.3 | Given: N 個參與者；When: 完整跑完 prepare/make/exchange/sign；Then: 可產生可上鏈交易且各輪資料可互通 |
| Testing | unit/integration/stagenet/perf/docs | Must | T1–T5、C1–C5 | T1–T5、C1–C5 | Given: CI/本機可跑測試；When: 執行測試套件；Then: 必要路徑全綠、效能不退化、文件範例可跑 |

### 3.0.2 交付驗收清單（不開票版）

> 將 Scope Matrix 改寫成可「逐項完成/驗收」的清單；若你不開票，也可以直接用這一節來控進度。

**Must**

- Keys/Address：向量集可重現；primary + N 組 subaddress 與 Oracle 一致（C1–C4）。
- Wallet Core：create/restore/open/close 可用；account/subaddress/labels 可查且可持久化。
- RPC：可連 stagenet；核心 RPC methods 可用且錯誤可控（timeout/retry/429/5xx）。
- Sync：可從高度 H refresh 到最新；遇到 reorg 能回退並重掃且餘額一致。
- Tx（最小路徑）：一般轉帳可 build/sign/relay；tx blob 可被 `monerod` 接受並上鏈（C5）。
- Storage：錢包資料可加密保存；無密碼不可解密；重啟 round-trip 不變。
- Testing：T1–T5 與 C1–C5 至少覆蓋核心路徑。

**Should**

- Events：同步進度/新交易/收款事件可訂閱，且可停止/取消訂閱不漏資源。
- Tx Types：batch / sweep / sweep_all 至少可用一種並可逐步擴充。
- Offline Signing：export unsigned → offline sign → import signed → relay 流程可跑通。
- Query：tx/transfer/output 查詢可依條件過濾（含 account/subaddress）。
- Wallet Data：address book、tx notes 可寫入/更新/持久化。
- Output Mgmt：freeze/thaw 正確影響 input selection；outputs/key images 匯出入可讓 view-only/offline 交換狀態一致。

**Optional**

- Proofs：tx/reserve/message proofs 可與官方工具或另一實作互驗。
- Multisig：完成 N-of-M 全流程（含多輪交換）並可上鏈。

### 3.9 同步/交易策略（實作決策，避免返工）

以下決策需要在兩套獨立實作中保持一致，否則會造成行為差異與驗收困難：

- **Reorg 回溯策略**：以「可配置 rollback depth」處理；偵測到分叉時回退到安全高度再重掃，並重算輸出/花費狀態。
- **Ring size / decoys**：以目標協議版本（v0.18.x）為基準實作；撰寫成可配置但預設符合主網共識參數（一般表述為 ring size = 16，即 15 decoys + 1 real）。
- **Fee/priority**：費率估算與 priority 規則要固定（至少在同輸入下可重現），並記錄到交易 metadata 以利除錯與互驗。
- **Mempool/in-pool 狀態**：同步與查詢需能辨識交易在 pool/confirmed/failed 的狀態，避免 UI/業務層誤判。
- **掃描邊界**：明確起始高度（restore height）與「落後高度追趕」行為；避免每次啟動全鏈重掃。

### 3.10 Web 支援邊界（Dart Web）

- **RPC 存取**：瀏覽器環境通常需要 RPC proxy（CORS、憑證、IP 限制），不假設可直接連公開節點。
- **同步模式**：Web 預設採「輕量化同步/受限同步」；完整掃鏈可能受限於 CPU、記憶體與背景執行能力。
- **密碼學運算**：Web 端運算需可在 Worker/Isolate 中執行，避免阻塞 UI；必要時提供降級策略（例如降低同時處理區塊數）。

### 3.1 密鑰與地址管理 (Key & Address Management)
- **助記詞 (Mnemonic)**:
    - 支援 25 字助記詞 (Polyglot/多語言支援)。
    - 支援從種子 (Seed) 生成助記詞，及從助記詞恢復種子。
    - 支援 Checksum 驗證。
- **金鑰衍生 (Key Derivation)**:
    - 實作 Ed25519 曲線運算 (sc_reduce32, ge_scalarmult_base 等)。
    - 生成 Private/Public Spend Key & View Key。
    - 支援 Subaddress (子地址) 衍生演算法 (Major/Minor index)。
- **地址生成**:
    - 生成標準地址 (Standard Address)。
    - 生成子地址 (Subaddress)。
    - 生成整合地址 (Integrated Address，含 Payment ID)。
    - 地址格式驗證與解析。

### 3.2 錢包與帳戶/子地址管理 (Wallet / Account / Subaddress)

- 建立/恢復/開啟/關閉錢包（從 seed/mnemonic；支援密碼）。
- 帳戶（account）管理：列出帳戶、建立新帳戶、查詢各帳戶餘額。
- 子地址（subaddress）管理：建立/列出子地址、設定/讀取 label。
- 支援 view-only（觀察錢包）與 offline（離線簽署錢包）的錢包型態。

### 3.3 區塊鏈同步 (Blockchain Synchronization)
- **Daemon 通訊**:
    - 實作 JSON-RPC 客戶端 (支援 `get_info`, `get_block`, `get_o_indexes`, `send_raw_transaction` 等)。
    - 支援與遠端節點 (Remote Node) 通訊。
    - 支援 SSL/TLS 連線。
- **區塊掃描 (Scanning)**:
    - 實作 View Key 掃描機制，識別屬於錢包的 Outputs。
    - 支援 `refresh` 流程：從指定高度同步至最新區塊。
    - 支援輕量級同步 (Light Wallet) 或完整區塊頭同步模式。
    - 處理區塊重組 (Reorg) 與分叉。

### 3.4 交易處理 (Transaction Handling)
- **餘額查詢**:
    - 計算解鎖餘額 (Unlocked Balance) 與總餘額。
    - 識別已花費 (Spent) 與未花費 (Unspent) 的 Outputs。
- **交易建構 (Tx Construction)**:
    - **Input Selection**: 實作 UTXO 選擇演算法 (考慮金額、年齡等)。
    - **Decoy Selection**: 實作誘餌選擇演算法 (Gamma 分佈)，確保隱私性。
    - **Fee Calculation**: 依據網路擁塞狀況計算動態手續費。
- **交易簽署 (Signing)**:
    - 實作 CLSAG 環簽章生成。
    - 實作 Bulletproofs+ 範圍證明生成。
    - 生成 Key Images 以防止雙重花費。
    - 支援離線簽名 (Cold Signing) 流程：
        - 匯出未簽名交易 (Unsigned Tx)。
        - 匯入已簽名交易 (Signed Tx)。
- **交易廣播**:
    - 透過 Daemon RPC (`send_raw_transaction`) 廣播交易。

### 3.5 交易查詢與錢包資料管理 (Tx Query & Wallet Data)

- 交易/轉帳/輸出查詢 API：
    - 依 hash、高度範圍、時間、方向（in/out）、確認狀態、account/subaddress 等條件查詢。
    - 查詢 tx pool（in-pool）狀態（至少在同步/掃描時能辨識）。
- 交易備註（tx notes）與基本 metadata 的讀寫。
- Address Book：新增/移除/查詢聯絡人（名稱、地址、描述/備註）。

### 3.6 輸出管理與離線資料交換 (Outputs / Key Images / Offline Exchange)

- 取得未花費輸出（UTXO）清單。
- 凍結/解凍特定輸出（freeze/thaw）避免被花費。
- 匯出/導入 outputs 與 key images：支援 view-only 與 offline wallet 交換花費狀態。
- 離線簽名工作流：
    - 匯出未簽名交易（unsigned tx set / 等價格式）。
    - 匯入已簽名交易並廣播。

### 3.7 證明與訊息簽章 (Proofs & Message Signing)（Optional / 視範圍）

若目標為與官方錢包在驗證工具上互通，需支援：

- 取得 tx key / 生成與驗證 tx proof。
- reserve proof（餘額證明）的生成與驗證。
- arbitrary message 的 sign/verify（地址所有權證明）。

### 3.8 多重簽名 (Multisig)（Optional / 高風險模組）
- 支援 N-of-M 多簽方案 (如 2/3, 3/5)。
- **流程支援**:
    - `prepare_multisig`: 生成初始交換資料。
    - `make_multisig`: 交換公鑰並生成多簽地址。
    - `exchange_multisig_keys`: 交換部分簽名金鑰 (Round 1 & 2)。
    - `sign_multisig`: 協同簽署交易。

### 3.11 錢包持久化 (Persistence)
- **檔案格式**:
    - 支援將錢包狀態 (Keys, Transaction History, Address Book) 序列化儲存。
    - **加密**: 使用 ChaCha20-Poly1305 或 AES-GCM 進行檔案加密 (KDF: Argon2 或 CryptoNight)。
    - (選用) 相容官方 `.keys` 檔案格式。

### 3.12 事件通知與輔助功能 (Auxiliary)

- 同步進度/新交易/收款/確認/解鎖等事件通知（listener/flow/stream）。
- (選用) start/stop mining（透過 daemon RPC 觸發；Web 可能不適用）。
- (選用) wallet properties（自訂屬性讀寫）、account tag/label 等輔助能力。

## 4. 非功能需求 (Non-Functional Requirements)

### 4.1 效能 (Performance)
- **大數運算**: 使用平台最佳化的大數庫 (Kotlin: IonSpin/BouncyCastle, Dart: `BigInt` optimized)。
- **背景執行**:
    - Kotlin: 使用 Coroutines (`Dispatchers.Default/IO`) 進行同步與運算，不阻塞 UI Thread。
    - Dart: 使用 Isolates 處理區塊掃描與密碼學運算。
- **記憶體管理**: 避免在同步過程中產生過多物件導致 OOM，特別是在行動裝置上。

### 4.2 安全性 (Security)
- **隨機數**: 必須使用加密安全的隨機數生成器 (CSPRNG)。
- **記憶體清除**: 敏感資料 (Seed, Private Keys) 使用後應盡可能從記憶體中清除 (Best Effort)。
- **依賴管理**: 最小化外部依賴，關鍵密碼學演算法優先採用經過驗證的實作或純語言重寫。

### 4.3 跨平台相容性 (Cross-Platform)
- **Kotlin**:
    - Android (API 24+)
    - iOS (Kotlin/Native)
    - Desktop (JVM: Windows, macOS, Linux)
- **Dart**:
    - Flutter (Android, iOS, Windows, macOS, Linux)
    - Web (需注意 WASM 支援與 CORS 限制)

## 5. 參考標準
- Monero Source Code (`src/wallet`, `src/crypto`)
- `monero-cpp` Library
- `monero_dart` Package
- Zero to Monero (ZtM) Technical Paper

## 6. 一致性與驗收 (Conformance & Acceptance)

兩套實作（KMP 與 Dart）需符合以下一致性要求：

- **Key/Address 一致性**：同 mnemonic/seed/network type 下，primary address 與固定數量的 subaddress 結果一致。
- **序列化一致性**：同一資料結構在各自語言的序列化/反序列化可穩定 round-trip；對外的交易 blob 必須可被 `monerod` 接受。
- **Oracle 對照**：以 `monero-wallet-cli` / `monero-wallet-rpc` 產生的結果作為標準（至少在核心路徑：地址生成、同步高度、交易簽名/廣播）。
