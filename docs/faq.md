# FAQ

## Q: regtest E2E 為什麼 daemon 要加 `--offline`？

A: 在本機 regtest 測試時不需要對外連網。`--offline` 可以避免 daemon 啟動階段嘗試做 peer/DNS/網路相關工作，這些常造成 `generateblocks` 回 `status: "BUSY"` 很久。

## Q: wallet-rpc 要不要加 `--regtest`？

A: 不需要。wallet-rpc 只要連到 regtest 的 daemon（`--daemon-address 127.0.0.1:<port>`），它就會跟隨 daemon 的網路模式。

## Q: 遇到 `Unexpected hard fork version ...` 或版本不匹配？

A: regtest 環境常見會被硬分叉/版本檢查擋住。本 repo 的 regtest E2E 會用 `--allow-mismatched-daemon-version` 讓 wallet-rpc 可以繼續工作。

## Q: 轉帳時出現 `not enough outputs to use Please use sweep_dust.`

A: 通常是挖的 blocks 太少，coinbase 還沒成熟或可用 outputs 不夠。regtest E2E 會先挖 100 blocks，並在轉帳前 refresh。

## Q: atomic units 是什麼？

A: Monero 的最小單位（piconero）。

- $1$ XMR $= 10^{12}$ atomic units
- 例如 $0.001$ XMR $= 1{,}000{,}000{,}000$ atomic units

## Q: E2E 用了哪些固定 ports？

A: regtest E2E 使用：
- daemon RPC：48081
- daemon P2P：48080
- wallet-rpc：48084

若你本機已有服務佔用，請先停止或修改測試檔。
