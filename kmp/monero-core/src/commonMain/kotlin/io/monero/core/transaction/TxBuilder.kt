package io.monero.core.transaction

import io.monero.core.MoneroAddress
import io.monero.core.generateSecureRandom
import io.monero.crypto.Ed25519
import io.monero.crypto.Keccak
import io.monero.crypto.PedersenCommitment

/**
 * High-level transaction builder that integrates:
 * - UTXO selection (`InputSelector`)
 * - Decoy selection (`DecoySelector`)
 * - Basic RingCT base fields (type/fee/ecdh/outPk)
 * - Serialization (`TransactionSerializer`)
 *
 * Note: This builder constructs the "base" RingCT fields only (matching
 * `TransactionParser.parseRctSignature`). Full Monero proof/signature
 * serialization is out of scope for this stage.
 */
class TxBuilder(
    private val outputProvider: OutputProvider,
    private val selectionConfig: SelectionConfig,
    private val decoyConfig: DecoySelectionConfig = DecoySelectionConfig()
) {
    data class Destination(
        val address: MoneroAddress,
        val amount: Long
    )

    data class BuiltTx(
        val tx: Transaction,
        val txBlob: ByteArray,
        val txHash: ByteArray,
        val fee: Long,
        val change: Long,
        /** Per-input rings and the real index within the ring. */
        val rings: List<InputRing>
    )

    data class InputRing(
        val realGlobalIndex: Long,
        val ringMembers: List<RingMember>,
        val realIndex: Int
    )

    /**
     * Build a RingCT v2 transaction.
     *
     * Requirements:
     * - Each selected [SpendableOutput] must have a non-null [SpendableOutput.keyImage].
     */
    suspend fun build(
        availableOutputs: List<SpendableOutput>,
        destinations: List<Destination>,
        changeAddress: MoneroAddress,
        rctType: RctType = RctType.CLSAG,
        paymentId: ByteArray? = null,
        selectionStrategy: SelectionStrategy = SelectionStrategy.CLOSEST_MATCH
    ): BuiltTx {
        require(destinations.isNotEmpty()) { "At least one destination required" }
        destinations.forEach { require(it.amount > 0) { "Destination amount must be > 0" } }

        val sendAmount = destinations.sumOf { it.amount }

        val selector = InputSelector(selectionConfig.copy(numOutputs = destinations.size + 1))
        val selection = selector.selectInputs(
            availableOutputs = availableOutputs,
            targetAmount = sendAmount,
            strategy = selectionStrategy
        ) ?: throw IllegalStateException("Insufficient funds")

        // Build rings and input key offsets.
        val decoySelector = DecoySelector(outputProvider, decoyConfig)
        val currentHeight = outputProvider.getCurrentHeight()

        val inputRings = mutableListOf<InputRing>()
        val inputs = selection.selectedOutputs.map { real ->
            val keyImage = real.keyImage ?: throw IllegalStateException("Missing key image for selected output")

            val ring = decoySelector.selectDecoys(real, currentHeight)
            val sorted = ring.sortedBy { it.globalIndex }
            val realIndex = sorted.indexOfFirst { it.globalIndex == real.globalIndex }
            require(realIndex >= 0) { "Real output missing from ring" }

            inputRings.add(
                InputRing(
                    realGlobalIndex = real.globalIndex,
                    ringMembers = sorted,
                    realIndex = realIndex
                )
            )

            val absolute = sorted.map { it.globalIndex }
            val relative = toRelativeOffsets(absolute)

            TxInput(
                amount = 0L,
                keyOffsets = relative,
                keyImage = keyImage
            )
        }

        // Create tx keypair (R and r).
        val (txSecretKey, txPubKey) = Ed25519.generateKeyPair(generateSecureRandom(32))

        // Build outputs (RingCT: amount=0; real amounts live in commitments/ECDH).
        val outputs = mutableListOf<TxOutput>()
        val outputAmounts = mutableListOf<Long>()

        // Destinations first.
        destinations.forEachIndexed { index, dst ->
            val targetKey = deriveOneTimeOutputKey(
                txSecretKey = txSecretKey,
                recipientViewKey = dst.address.publicViewKey,
                recipientSpendKey = dst.address.publicSpendKey,
                outputIndex = index
            )
            val viewTag = computeViewTag(
                txSecretKey = txSecretKey,
                recipientViewKey = dst.address.publicViewKey,
                outputIndex = index
            )

            outputs.add(
                TxOutput(
                    index = index,
                    amount = 0L,
                    target = TxOutTarget(targetKey, viewTag)
                )
            )
            outputAmounts.add(dst.amount)
        }

        // Change output if any.
        val change = selection.change
        if (change > 0) {
            val changeIndex = outputs.size
            val changeKey = deriveOneTimeOutputKey(
                txSecretKey = txSecretKey,
                recipientViewKey = changeAddress.publicViewKey,
                recipientSpendKey = changeAddress.publicSpendKey,
                outputIndex = changeIndex
            )
            val changeViewTag = computeViewTag(
                txSecretKey = txSecretKey,
                recipientViewKey = changeAddress.publicViewKey,
                outputIndex = changeIndex
            )

            outputs.add(
                TxOutput(
                    index = changeIndex,
                    amount = 0L,
                    target = TxOutTarget(changeKey, changeViewTag)
                )
            )
            outputAmounts.add(change)
        }

        // Extra: tx pubkey + optional payment id.
        val extra = TransactionSerializer.buildExtra(
            txPubKey = txPubKey,
            additionalPubKeys = emptyList(),
            encryptedPaymentId = paymentId
        )

        // RingCT base fields for outputs.
        val (ecdhInfo, outPk) = buildRctOutputBase(outputAmounts)
        val rctSig = RctSignature(
            type = rctType,
            txnFee = selection.fee,
            ecdhInfo = ecdhInfo,
            outPk = outPk
        )

        // Build tx and blob.
        val tx = Transaction(
            hash = ByteArray(32), // filled below
            version = 2,
            unlockTime = 0,
            inputs = inputs,
            outputs = outputs,
            extra = extra,
            rctSignature = rctSig,
            isCoinbase = false
        )

        val blob = TransactionSerializer.serialize(tx)
        val hash = Keccak.hash256(blob)

        return BuiltTx(
            tx = tx.copy(hash = hash),
            txBlob = blob,
            txHash = hash,
            fee = selection.fee,
            change = change,
            rings = inputRings
        )
    }

    private fun buildRctOutputBase(amounts: List<Long>): Pair<List<EcdhInfo>, List<ByteArray>> {
        val ecdh = ArrayList<EcdhInfo>(amounts.size)
        val outPk = ArrayList<ByteArray>(amounts.size)

        amounts.forEach { amount ->
            val (commitment, mask) = PedersenCommitment.commitWithRandomMask(amount)
            outPk.add(commitment)

            // Compact format (as parsed by TransactionParser): only 8 bytes amount.
            // This is a placeholder: real Monero uses ECDH encryption.
            val amountBytes = longToLittleEndian8(amount)
            ecdh.add(EcdhInfo(mask = mask, amount = amountBytes))
        }

        return ecdh to outPk
    }

    private fun deriveOneTimeOutputKey(
        txSecretKey: ByteArray,
        recipientViewKey: ByteArray,
        recipientSpendKey: ByteArray,
        outputIndex: Int
    ): ByteArray {
        // shared = r * A
        val shared = Ed25519.scalarMult(txSecretKey, recipientViewKey)
        val derivationData = shared + outputIndex.toVarint()
        val hash = Keccak.hash256(derivationData)
        val scalar = Ed25519.scalarReduce64(hash + ByteArray(32))
        val scalarG = Ed25519.scalarMultBase(scalar)
        return Ed25519.pointAdd(scalarG, recipientSpendKey)
    }

    private fun computeViewTag(
        txSecretKey: ByteArray,
        recipientViewKey: ByteArray,
        outputIndex: Int
    ): Byte {
        val shared = Ed25519.scalarMult(txSecretKey, recipientViewKey)
        val data = byteArrayOf(0x76, 0x69, 0x65, 0x77, 0x5f, 0x74, 0x61, 0x67) + // "view_tag"
            shared +
            outputIndex.toVarint()
        return Keccak.hash256(data)[0]
    }

    private fun toRelativeOffsets(sortedAbsolute: List<Long>): List<Long> {
        require(sortedAbsolute.isNotEmpty())
        val result = ArrayList<Long>(sortedAbsolute.size)
        var prev = 0L
        for (i in sortedAbsolute.indices) {
            val cur = sortedAbsolute[i]
            val rel = if (i == 0) cur else (cur - prev)
            result.add(rel)
            prev = cur
        }
        return result
    }

    private fun Int.toVarint(): ByteArray {
        val result = mutableListOf<Byte>()
        var value = this
        while (value >= 0x80) {
            result.add(((value and 0x7F) or 0x80).toByte())
            value = value ushr 7
        }
        result.add(value.toByte())
        return result.toByteArray()
    }

    private fun longToLittleEndian8(value: Long): ByteArray {
        val out = ByteArray(8)
        var v = value
        for (i in 0 until 8) {
            out[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────
    // K4.8 – sweep_all: send all available outputs to a single destination
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sweep all spendable outputs to a single destination address.
     *
     * This is equivalent to Monero's `sweep_all` command. The entire wallet
     * balance (minus fee) is sent to [destinationAddress]. No change output
     * is created.
     *
     * @param availableOutputs All spendable outputs (each must have keyImage).
     * @param destinationAddress Where to sweep funds.
     * @param rctType RingCT type (default CLSAG).
     * @param paymentId Optional encrypted payment id (8 bytes).
     * @return Built transaction with fee deducted from send amount.
     */
    suspend fun sweepAll(
        availableOutputs: List<SpendableOutput>,
        destinationAddress: MoneroAddress,
        rctType: RctType = RctType.CLSAG,
        paymentId: ByteArray? = null
    ): BuiltTx {
        require(availableOutputs.isNotEmpty()) { "No outputs to sweep" }

        val selector = InputSelector(selectionConfig.copy(numOutputs = 1))
        val selection = selector.selectAll(availableOutputs)
            ?: throw IllegalStateException("Cannot sweep: insufficient funds after fee")

        val decoySelector = DecoySelector(outputProvider, decoyConfig)
        val currentHeight = outputProvider.getCurrentHeight()

        val inputRings = mutableListOf<InputRing>()
        val inputs = selection.selectedOutputs.map { real ->
            val keyImage = real.keyImage
                ?: throw IllegalStateException("Missing key image for output at globalIndex=${real.globalIndex}")

            val ring = decoySelector.selectDecoys(real, currentHeight)
            val sorted = ring.sortedBy { it.globalIndex }
            val realIndex = sorted.indexOfFirst { it.globalIndex == real.globalIndex }
            require(realIndex >= 0) { "Real output missing from ring" }

            inputRings.add(InputRing(real.globalIndex, sorted, realIndex))

            val relative = toRelativeOffsets(sorted.map { it.globalIndex })
            TxInput(amount = 0L, keyOffsets = relative, keyImage = keyImage)
        }

        val (txSecretKey, txPubKey) = Ed25519.generateKeyPair(generateSecureRandom(32))

        // Single output – entire sendAmount goes to destination.
        val targetKey = deriveOneTimeOutputKey(
            txSecretKey = txSecretKey,
            recipientViewKey = destinationAddress.publicViewKey,
            recipientSpendKey = destinationAddress.publicSpendKey,
            outputIndex = 0
        )
        val viewTag = computeViewTag(
            txSecretKey = txSecretKey,
            recipientViewKey = destinationAddress.publicViewKey,
            outputIndex = 0
        )

        val outputs = listOf(
            TxOutput(index = 0, amount = 0L, target = TxOutTarget(targetKey, viewTag))
        )
        val outputAmounts = listOf(selection.sendAmount)

        val extra = TransactionSerializer.buildExtra(
            txPubKey = txPubKey,
            additionalPubKeys = emptyList(),
            encryptedPaymentId = paymentId
        )

        val (ecdhInfo, outPk) = buildRctOutputBase(outputAmounts)
        val rctSig = RctSignature(
            type = rctType,
            txnFee = selection.fee,
            ecdhInfo = ecdhInfo,
            outPk = outPk
        )

        val tx = Transaction(
            hash = ByteArray(32),
            version = 2,
            unlockTime = 0,
            inputs = inputs,
            outputs = outputs,
            extra = extra,
            rctSignature = rctSig,
            isCoinbase = false
        )

        val blob = TransactionSerializer.serialize(tx)
        val hash = Keccak.hash256(blob)

        return BuiltTx(
            tx = tx.copy(hash = hash),
            txBlob = blob,
            txHash = hash,
            fee = selection.fee,
            change = 0L,
            rings = inputRings
        )
    }
}
