#!/usr/bin/env bash
#
# generate_vectors.sh
# 使用 monero-wallet-cli 產生測試向量（Oracle 對照用）
#
# Prerequisites:
#   - monero-wallet-cli 已安裝並位於 PATH
#   - jq（用於產生 JSON）
#
# Usage:
#   ./scripts/generate_vectors.sh [mainnet|stagenet]
#
# Output:
#   寫入 vectors/{network}/address_derivation.json
#
set -euo pipefail

NETWORK="${1:-stagenet}"
MNEMONIC="vivid birth enjoy arrange bicycle lumber myth gawk pyramid hockey orange kangaroo puffin abbey ugly birth enjoy arrange bicycle lumber myth gawk pyramid hockey vipers vipers"
OUTPUT_DIR="$(dirname "$0")/../vectors/${NETWORK}"
OUTPUT_FILE="${OUTPUT_DIR}/address_derivation.json"

mkdir -p "${OUTPUT_DIR}"

echo "=== Generating ${NETWORK} address derivation vectors ==="
echo "Mnemonic: ${MNEMONIC}"
echo ""

# TODO: 實際產生邏輯
# 1. 使用 monero-wallet-cli --restore-deterministic-wallet --electrum-seed "$MNEMONIC" (或類似)
# 2. 執行 address all 取得 primary + subaddresses
# 3. 執行 viewkey / spendkey 取得金鑰
# 4. 用 jq 組裝成 JSON 並寫入 $OUTPUT_FILE

echo "⚠️  此腳本為範本，請補充 monero-wallet-cli 互動/自動化邏輯。"
echo "   完成後會將結果寫入: ${OUTPUT_FILE}"
