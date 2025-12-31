# Monero 錢包庫架構設計 (Design Document)

> **技術棧版本（2026-01-01 更新）**
>
> | 技術 | 版本 | 備註 |
> |------|------|------|
> | Monero | v0.18.4.4 "Fluorine Fermi" | 2025-11-19 發布 |
> | Kotlin | 2.3.0 | 2025-12 發布 |
> | Ktor | 3.3.3 | HTTP Client |
> | Dart SDK | 3.10.7 | stable |
> | Flutter | 3.38 | 2025-11-12 發布 |

## 0. 實作原則（重要）

本專案會產出兩套**完全獨立**的錢包庫實作：

- **Kotlin Multiplatform (KMP)**：一套 Kotlin 程式碼（含 common/expect-actual）輸出 Android/iOS/Desktop 等平台產物。
- **Dart (Pure Dart)**：一套 Dart 程式碼輸出 Flutter 全平台（含 Web）。

兩套實作**不共享核心程式碼**（No shared core implementation）。一致性由以下機制保證：

1. **共同規格**：同一份功能/資料模型/序列化/相容性要求（見 requirements）。
2. **共同測試向量**：相同 seed/mnemonic、地址、子地址、交易建構結果、proof 等測試向量。
3. **共同 Oracle 對照**：以 `monero-wallet-cli` / `monero-wallet-rpc` / `monerod` 作為對照來源，比對輸出是否一致。
4. **跨實作互驗**：KMP 與 Dart 在相同輸入下的輸出必須一致（如地址、tx hash、key images、序列化 blob）。

## 1. 系統架構概觀

本系統將分為兩個獨立的實作：**Kotlin Multiplatform (KMP)** 與 **Dart (Flutter)**。兩者遵循相同的邏輯分層架構，以確保功能的一致性與可維護性。

### 分層架構 (Layered Architecture)

```mermaid
graph TD
    UI[UI Layer (App/Frontend)] --> API[Wallet API Layer]
    API --> Core[Core Logic Layer]
    Core --> Crypto[Cryptography Layer]
    Core --> Net[Network Layer]
    Core --> Storage[Storage Layer]
```

1.  **Wallet API Layer**: 提供給開發者使用的高階介面 (Facade)，如 `MoneroWallet`。
2.  **Core Logic Layer**: 處理錢包業務邏輯，如同步狀態機、交易組裝、UTXO 管理。
3.  **Cryptography Layer**: 底層密碼學實作 (Ed25519, RingCT, Bulletproofs+)。
4.  **Network Layer**: 負責與 Monero Daemon (JSON-RPC) 通訊。
5.  **Storage Layer**: 負責錢包資料的持久化 (檔案或資料庫)。

---

## 2. Kotlin Multiplatform (KMP) 設計

### 2.1 模組劃分 (Modules)

-   **`monero-crypto`**:
    -   純 Kotlin 或透過 `expect/actual` 呼叫平台特定實作。
    -   包含：Keccak, Ed25519, Scalar arithmetic, Bulletproofs+, CLSAG。
    -   *Design Decision*: 優先使用純 Kotlin 實作以最大化可攜性，效能瓶頸處 (如 BP+ 驗證) 可考慮 Native Interop。

-   **`monero-core`**:
    -   資料模型 (`data class`): `Account`, `Subaddress`, `Transaction`, `Transfer`, `Output`。
    -   核心邏輯: `AddressGenerator`, `TxBuilder`, `DecoySelector`。

-   **`monero-net`**:
    -   使用 **Ktor Client** 實作 JSON-RPC 通訊。
    -   `DaemonClient`: 封裝 `get_blocks`, `send_raw_transaction` 等 API。
    -   支援 Coroutines 非同步呼叫。

-   **`monero-storage`**:
    -   定義 `WalletStorage` 介面。
    -   實作：基於 **SQLDelight** (SQLite) 或 **Okio** (檔案序列化) 的儲存方案。
    -   負責加密與解密錢包檔案。

-   **`monero-wallet` (Main Module)**:
    -   整合上述模組。
    -   提供 `MoneroWallet` 介面，實作 `sync()`, `transfer()`, `getBalance()`。
    -   使用 `Flow` 或是 `StateFlow` 暴露同步進度與狀態。

### 2.2 關鍵類別設計

```kotlin
interface MoneroWallet {
    val address: String
    val balance: Flow<BigInteger>
    val syncStatus: Flow<SyncStatus>

    suspend fun sync(startHeight: Long? = null)
    suspend fun createTransaction(config: TxConfig): PendingTransaction
    suspend fun submitTransaction(tx: PendingTransaction): String
    fun close()
}

class MoneroWalletImpl(
    private val crypto: CryptoUtils,
    private val network: DaemonClient,
    private val storage: WalletStorage
) : MoneroWallet { ... }
```

---

## 3. Dart (Flutter) 設計

### 3.1 套件結構 (Package Structure)

-   **`monero_dart`** (單一套件或 Monorepo):
    -   `lib/src/crypto/`: 密碼學實作。使用 `BigInt` 與 `typed_data`。
    -   `lib/src/rpc/`: `DaemonRpcClient`, `WalletRpcClient`。使用 `http` 或 `dio` 套件。
    -   `lib/src/wallet/`: 錢包核心邏輯。
    -   `lib/src/utils/`: 序列化工具 (Levin, Binary)、輔助函式。

### 3.2 併發模型 (Concurrency Model)

由於 Dart 是單執行緒模型 (Single Threaded Event Loop)，密碼學運算與區塊處理必須移至 **Isolates** 以避免阻塞 UI。

-   **SyncManager**:
    -   負責生成與管理 Worker Isolate。
    -   主 Isolate 透過 `SendPort`/`ReceivePort` 與 Worker 通訊。
    -   Worker 負責：解析區塊資料、計算 Key Image、驗證交易輸出。
    -   Worker 回傳：發現的 `Transfer` 物件與更新後的 `SyncHeight`。

### 3.3 關鍵類別設計

```dart
abstract class MoneroWallet {
  String get primaryAddress;
  Stream<BigInt> get balance;
  Stream<double> get syncProgress;

  Future<void> startSync({int? startHeight});
  Future<PendingTx> createTx(TxConfig config);
  Future<String> relayTx(PendingTx tx);
}

class MoneroWalletDart implements MoneroWallet {
  final MoneroRpcClient _rpc;
  final WalletStore _store;
  // ...
}
```

---

## 4. 跨平台共通邏輯 (Common Logic)

雖然語言不同且互不共用核心程式碼，但演算法邏輯與序列化結果必須嚴格一致。

### 4.1 交易建構流程 (Tx Construction Pipeline)
1.  **Selection**: 從 `UnspentOutputs` 中選取足夠金額的 Inputs (優先選取舊的、非塵埃的)。
2.  **Decoy Fetching**: 針對每個 Input，從區塊鏈 (RPC) 獲取 15 個 (目前協議) 隨機 Ring Members。
3.  **Construction**:
    -   生成 One-time destination keys (Stealth Address)。
    -   計算 Amount Commitments (Pedersen)。
    -   生成 Bulletproofs+ (Range Proof)。
    -   計算 CLSAG 簽名。
4.  **Serialization**: 將簽名後的交易序列化為 Blob，準備廣播。

### 4.2 同步流程 (Sync Process)
1.  **Get Info**: 取得鏈上最新高度。
2.  **Batch Fetch**: 分批 (如每次 100 個區塊) 下載區塊雜湊或完整區塊。
3.  **Scan**:
    -   使用 View Key 嘗試解密每個交易的 Output (Derive Public Key)。
    -   若匹配，則記錄該 Output 為 `OwnedOutput`。
    -   查詢 Key Image 狀態 (是否已花費)。
4.  **Save**: 更新本地高度與 Output 列表。

## 5. 資料儲存格式 (Data Persistence)

為了簡化與相容性，建議定義一套通用的 JSON 或 Binary 結構作為內部交換格式，但在儲存層可各自優化。

-   **Keys File**: 儲存加密後的 Seed, SpendKey, ViewKey。
-   **Cache File**: 儲存已掃描的區塊高度、Transaction History、Address Book。

## 6. 測試策略 (Testing Strategy)

-   **Unit Tests**: 針對 Crypto 模組 (Seed 生成、簽名驗證) 撰寫大量測試向量 (Test Vectors)。
-   **Integration Tests**: 使用 `monero-wallet-rpc` 作為 Oracle，比對相同 Seed 下的地址生成與交易簽名結果。
-   **Stagenet Testing**: 連接 Stagenet 進行實際的同步與轉帳測試。

### 6.1 一致性驗收（KMP vs Dart）

以下項目作為「兩套獨立復刻」的最小一致性驗收清單：

1. 給定相同 mnemonic/seed/network type，產生的：
    - primary address
    - 前 N 個 subaddress（至少 N=10）
    - view/spend keys（必要時以標準格式輸出比對）
2. 給定相同鏈上輸出與相同轉帳設定（收款/金額/priority），建構交易結果的：
    - tx hash
    - key images
    - serialized tx blob（可選：若兩套實作採不同排序策略但最終網路接受，至少需保證可被 `monerod` 接受；若目標為「不可區分於官方錢包」，則需強制同排序/同策略）
3. 重要輔助證明（若納入範圍）：tx proof / reserve proof / message sign/verify。
