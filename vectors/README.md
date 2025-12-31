# Test Vectors

本資料夾存放 **KMP 與 Dart 共用的測試向量**（僅共享向量檔/預期輸出，不共享程式碼）。

## 目錄結構

```
vectors/
├── README.md
├── mainnet/
│   └── address_derivation.json   # 主網向量
├── stagenet/
│   └── address_derivation.json   # Stagenet 向量
└── tx/
    └── minimal_transfer.json     # 最小路徑交易向量（選用）
```

## 使用方式

1. 測試程式讀取 JSON 向量。
2. 執行對應函式（如 `deriveAddress`）。
3. 比對輸出與 `expected` 欄位。
4. 任何差異應可定位到欄位（哪個地址/哪個 index）。

## 向量來源

- 以 `monero-wallet-cli` / `monero-wallet-rpc` 產生並人工驗證。
- 可用 `scripts/generate_vectors.sh`（稍後建立）自動化產出。
