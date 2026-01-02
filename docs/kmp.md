# KMP 模組使用說明

KMP 端的文件主要集中在：`kmp/README.md`。

本文件作為「入口頁」，協助你快速找到：如何 build、跑測試、以及模組對應。

## Quick start

```bash
cd kmp
./gradlew build
```

跑測試（依模組）：

```bash
cd kmp

# 例：crypto 模組
./gradlew :monero-crypto:jvmTest
```

## 模組地圖

- `kmp/monero-crypto`：密碼學 primitives
- `kmp/monero-core`：地址/助記詞/金鑰/交易核心
- `kmp/monero-net`：daemon RPC client
- `kmp/monero-storage`：持久化/加密
- `kmp/monero-wallet`：高階錢包 API

## 延伸閱讀

- `kmp/README.md`：完整 KMP 使用範例與平台說明
- `docs/design.md`：分層架構設計
- `docs/requirements.md`：需求分級與驗收標準
