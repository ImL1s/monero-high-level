package io.monero.wallet.outputs

import io.monero.wallet.storage.OutputRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Output manager for handling wallet outputs (UTXOs).
 * 
 * Supports:
 * - Freezing/thawing outputs (exclude from spending)
 * - Exporting outputs for view-only wallet sync
 * - Exporting key images for offline signing workflow
 * - Importing signed key images
 */
interface OutputManager {
    /**
     * Get all outputs.
     */
    suspend fun getOutputs(): List<OutputRecord>

    /**
     * Get outputs matching criteria.
     */
    suspend fun queryOutputs(query: OutputQuery): List<OutputRecord>

    /**
     * Get spendable outputs (not frozen, not spent, unlocked).
     */
    suspend fun getSpendableOutputs(): List<OutputRecord>

    /**
     * Get total balance from all outputs.
     */
    suspend fun getBalance(): OutputBalance

    /**
     * Freeze an output (exclude from spending).
     */
    suspend fun freezeOutput(txHash: String, outputIndex: Int): Boolean

    /**
     * Thaw a frozen output (include in spending).
     */
    suspend fun thawOutput(txHash: String, outputIndex: Int): Boolean

    /**
     * Freeze multiple outputs.
     */
    suspend fun freezeOutputs(outputs: List<OutputId>): Int

    /**
     * Thaw multiple outputs.
     */
    suspend fun thawOutputs(outputs: List<OutputId>): Int

    /**
     * Check if an output is frozen.
     */
    suspend fun isFrozen(txHash: String, outputIndex: Int): Boolean

    /**
     * Export outputs for view-only wallet.
     * View-only wallets need this data to know about received outputs.
     */
    suspend fun exportOutputs(all: Boolean = false): ExportedOutputs

    /**
     * Import outputs into a view-only wallet.
     */
    suspend fun importOutputs(data: ExportedOutputs): Int

    /**
     * Export key images for offline signing.
     * Only available for full wallets (with spend key).
     */
    suspend fun exportKeyImages(all: Boolean = false): ExportedKeyImages

    /**
     * Import signed key images from offline wallet.
     * Updates spent status of outputs.
     */
    suspend fun importKeyImages(data: ExportedKeyImages): KeyImageImportResult

    /**
     * Mark an output as spent.
     */
    suspend fun markSpent(txHash: String, outputIndex: Int, spentHeight: Long)

    /**
     * Add a new output.
     */
    suspend fun addOutput(output: OutputRecord)

    /**
     * Update output's key image.
     */
    suspend fun setKeyImage(txHash: String, outputIndex: Int, keyImage: String)
}

/**
 * Output identifier.
 */
data class OutputId(
    val txHash: String,
    val outputIndex: Int
)

/**
 * Query builder for output filtering.
 */
data class OutputQuery(
    val accountIndex: Int? = null,
    val subaddressIndex: Int? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val isSpent: Boolean? = null,
    val isFrozen: Boolean? = null,
    val hasKeyImage: Boolean? = null
) {
    fun matches(output: OutputRecord): Boolean {
        if (accountIndex != null && output.accountIndex != accountIndex) return false
        if (subaddressIndex != null && output.subaddressIndex != subaddressIndex) return false
        if (minAmount != null && output.amount < minAmount) return false
        if (maxAmount != null && output.amount > maxAmount) return false
        if (isSpent != null && output.isSpent != isSpent) return false
        if (isFrozen != null && output.isFrozen != isFrozen) return false
        if (hasKeyImage != null && (output.keyImage != null) != hasKeyImage) return false
        return true
    }

    companion object {
        fun all() = OutputQuery()
        fun spendable() = OutputQuery(isSpent = false, isFrozen = false)
        fun frozen() = OutputQuery(isFrozen = true)
        fun forAccount(index: Int) = OutputQuery(accountIndex = index)
    }
}

/**
 * Balance breakdown by output status.
 */
data class OutputBalance(
    val total: Long,
    val unlocked: Long,
    val frozen: Long,
    val spent: Long
)

/**
 * Exported outputs data for view-only wallet sync.
 */
@Serializable
data class ExportedOutputs(
    val version: Int = 1,
    val outputs: List<ExportedOutput>
)

@Serializable
data class ExportedOutput(
    val txHash: String,
    val outputIndex: Int,
    val amount: Long,
    val globalIndex: Long?,
    val accountIndex: Int,
    val subaddressIndex: Int,
    val txPubKey: String,       // For deriving output key
    val unlockTime: Long
)

/**
 * Exported key images for offline signing workflow.
 */
@Serializable
data class ExportedKeyImages(
    val version: Int = 1,
    val keyImages: List<KeyImageEntry>
)

@Serializable
data class KeyImageEntry(
    val txHash: String,
    val outputIndex: Int,
    val keyImage: String,
    val signature: String?      // Proof of key image ownership
)

/**
 * Result of importing key images.
 */
data class KeyImageImportResult(
    val imported: Int,
    val spent: Long,            // Total amount now marked as spent
    val unspent: Long           // Total amount still unspent
)

/**
 * In-memory output manager implementation.
 */
class InMemoryOutputManager : OutputManager {

    private val outputs = mutableListOf<OutputRecord>()

    override suspend fun getOutputs(): List<OutputRecord> {
        return outputs.toList()
    }

    override suspend fun queryOutputs(query: OutputQuery): List<OutputRecord> {
        return outputs.filter { query.matches(it) }
    }

    override suspend fun getSpendableOutputs(): List<OutputRecord> {
        val currentHeight = 0L  // TODO: Get from sync state
        return outputs.filter { output ->
            !output.isSpent &&
            !output.isFrozen &&
            (output.unlockTime == 0L || output.unlockTime <= currentHeight)
        }
    }

    override suspend fun getBalance(): OutputBalance {
        var total = 0L
        var unlocked = 0L
        var frozen = 0L
        var spent = 0L

        for (output in outputs) {
            when {
                output.isSpent -> spent += output.amount
                output.isFrozen -> frozen += output.amount
                else -> {
                    total += output.amount
                    if (output.unlockTime == 0L) {
                        unlocked += output.amount
                    }
                }
            }
        }

        return OutputBalance(
            total = total,
            unlocked = unlocked,
            frozen = frozen,
            spent = spent
        )
    }

    override suspend fun freezeOutput(txHash: String, outputIndex: Int): Boolean {
        val index = findOutputIndex(txHash, outputIndex) ?: return false
        outputs[index] = outputs[index].copy(isFrozen = true)
        return true
    }

    override suspend fun thawOutput(txHash: String, outputIndex: Int): Boolean {
        val index = findOutputIndex(txHash, outputIndex) ?: return false
        outputs[index] = outputs[index].copy(isFrozen = false)
        return true
    }

    override suspend fun freezeOutputs(outputs: List<OutputId>): Int {
        var count = 0
        for (id in outputs) {
            if (freezeOutput(id.txHash, id.outputIndex)) count++
        }
        return count
    }

    override suspend fun thawOutputs(outputs: List<OutputId>): Int {
        var count = 0
        for (id in outputs) {
            if (thawOutput(id.txHash, id.outputIndex)) count++
        }
        return count
    }

    override suspend fun isFrozen(txHash: String, outputIndex: Int): Boolean {
        return outputs.find { it.txHash == txHash && it.outputIndex == outputIndex }?.isFrozen ?: false
    }

    override suspend fun exportOutputs(all: Boolean): ExportedOutputs {
        val toExport = if (all) {
            outputs
        } else {
            outputs.filter { !it.isSpent }
        }

        return ExportedOutputs(
            outputs = toExport.map { output ->
                ExportedOutput(
                    txHash = output.txHash,
                    outputIndex = output.outputIndex,
                    amount = output.amount,
                    globalIndex = output.globalIndex,
                    accountIndex = output.accountIndex,
                    subaddressIndex = output.subaddressIndex,
                    txPubKey = "",  // TODO: Store tx pub key
                    unlockTime = output.unlockTime
                )
            }
        )
    }

    override suspend fun importOutputs(data: ExportedOutputs): Int {
        var imported = 0
        for (exportedOutput in data.outputs) {
            val existing = outputs.find {
                it.txHash == exportedOutput.txHash && it.outputIndex == exportedOutput.outputIndex
            }

            if (existing == null) {
                outputs.add(OutputRecord(
                    txHash = exportedOutput.txHash,
                    outputIndex = exportedOutput.outputIndex,
                    amount = exportedOutput.amount,
                    keyImage = null,
                    globalIndex = exportedOutput.globalIndex,
                    accountIndex = exportedOutput.accountIndex,
                    subaddressIndex = exportedOutput.subaddressIndex,
                    isSpent = false,
                    isFrozen = false,
                    spentHeight = null,
                    unlockTime = exportedOutput.unlockTime
                ))
                imported++
            }
        }
        return imported
    }

    override suspend fun exportKeyImages(all: Boolean): ExportedKeyImages {
        val toExport = outputs.filter { output ->
            output.keyImage != null && (all || !output.isSpent)
        }

        return ExportedKeyImages(
            keyImages = toExport.map { output ->
                KeyImageEntry(
                    txHash = output.txHash,
                    outputIndex = output.outputIndex,
                    keyImage = output.keyImage!!,
                    signature = null  // TODO: Generate proof
                )
            }
        )
    }

    override suspend fun importKeyImages(data: ExportedKeyImages): KeyImageImportResult {
        var imported = 0
        var spentAmount = 0L
        var unspentAmount = 0L

        for (entry in data.keyImages) {
            val index = findOutputIndex(entry.txHash, entry.outputIndex)
            if (index != null) {
                val output = outputs[index]
                outputs[index] = output.copy(keyImage = entry.keyImage)
                imported++

                if (output.isSpent) {
                    spentAmount += output.amount
                } else {
                    unspentAmount += output.amount
                }
            }
        }

        return KeyImageImportResult(
            imported = imported,
            spent = spentAmount,
            unspent = unspentAmount
        )
    }

    override suspend fun markSpent(txHash: String, outputIndex: Int, spentHeight: Long) {
        val index = findOutputIndex(txHash, outputIndex) ?: return
        outputs[index] = outputs[index].copy(
            isSpent = true,
            spentHeight = spentHeight
        )
    }

    override suspend fun addOutput(output: OutputRecord) {
        val existing = findOutputIndex(output.txHash, output.outputIndex)
        if (existing != null) {
            outputs[existing] = output
        } else {
            outputs.add(output)
        }
    }

    override suspend fun setKeyImage(txHash: String, outputIndex: Int, keyImage: String) {
        val index = findOutputIndex(txHash, outputIndex) ?: return
        outputs[index] = outputs[index].copy(keyImage = keyImage)
    }

    private fun findOutputIndex(txHash: String, outputIndex: Int): Int? {
        return outputs.indexOfFirst { 
            it.txHash == txHash && it.outputIndex == outputIndex 
        }.takeIf { it >= 0 }
    }

    fun loadFromRecords(records: List<OutputRecord>) {
        outputs.clear()
        outputs.addAll(records)
    }

    fun toRecords(): List<OutputRecord> = outputs.toList()
}

/**
 * JSON serialization helpers for export/import.
 */
object OutputExportFormat {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serializeOutputs(data: ExportedOutputs): String = json.encodeToString(data)
    fun deserializeOutputs(jsonString: String): ExportedOutputs = json.decodeFromString(jsonString)

    fun serializeKeyImages(data: ExportedKeyImages): String = json.encodeToString(data)
    fun deserializeKeyImages(jsonString: String): ExportedKeyImages = json.decodeFromString(jsonString)
}
