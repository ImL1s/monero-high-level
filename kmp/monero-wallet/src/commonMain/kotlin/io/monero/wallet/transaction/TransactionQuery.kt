package io.monero.wallet.transaction

import io.monero.wallet.storage.TransactionDirection
import io.monero.wallet.storage.TransactionRecord

/**
 * Query builder for transaction filtering.
 * 
 * Supports filtering by:
 * - Transaction hash
 * - Direction (incoming/outgoing)
 * - Confirmation status
 * - Account index
 * - Subaddress indices
 * - Block height range
 * - Amount range
 * - Date range
 */
class TransactionQuery private constructor(
    val txHash: String? = null,
    val direction: TransactionDirection? = null,
    val isConfirmed: Boolean? = null,
    val minConfirmations: Int? = null,
    val accountIndex: Int? = null,
    val subaddressIndices: Set<Int>? = null,
    val minHeight: Long? = null,
    val maxHeight: Long? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val fromTimestamp: Long? = null,
    val toTimestamp: Long? = null,
    val hasPaymentId: Boolean? = null,
    val paymentId: String? = null
) {

    /**
     * Filter a list of transactions based on query criteria.
     */
    fun filter(transactions: List<TransactionRecord>): List<TransactionRecord> {
        return transactions.filter { tx -> matches(tx) }
    }

    /**
     * Check if a transaction matches all query criteria.
     */
    fun matches(tx: TransactionRecord): Boolean {
        if (txHash != null && tx.txHash != txHash) return false
        if (direction != null && tx.direction != direction) return false
        if (isConfirmed != null && tx.isConfirmed != isConfirmed) return false
        if (minConfirmations != null && tx.confirmations < minConfirmations) return false
        if (accountIndex != null && tx.accountIndex != accountIndex) return false
        if (subaddressIndices != null && tx.subaddressIndices.none { it in subaddressIndices }) return false
        if (minHeight != null && (tx.blockHeight == null || tx.blockHeight < minHeight)) return false
        if (maxHeight != null && (tx.blockHeight == null || tx.blockHeight > maxHeight)) return false
        if (minAmount != null && kotlin.math.abs(tx.amount) < minAmount) return false
        if (maxAmount != null && kotlin.math.abs(tx.amount) > maxAmount) return false
        if (fromTimestamp != null && tx.timestamp < fromTimestamp) return false
        if (toTimestamp != null && tx.timestamp > toTimestamp) return false
        if (hasPaymentId != null && (tx.paymentId != null) != hasPaymentId) return false
        if (paymentId != null && tx.paymentId != paymentId) return false
        return true
    }

    /**
     * Builder for creating TransactionQuery instances.
     */
    class Builder {
        private var txHash: String? = null
        private var direction: TransactionDirection? = null
        private var isConfirmed: Boolean? = null
        private var minConfirmations: Int? = null
        private var accountIndex: Int? = null
        private var subaddressIndices: Set<Int>? = null
        private var minHeight: Long? = null
        private var maxHeight: Long? = null
        private var minAmount: Long? = null
        private var maxAmount: Long? = null
        private var fromTimestamp: Long? = null
        private var toTimestamp: Long? = null
        private var hasPaymentId: Boolean? = null
        private var paymentId: String? = null

        fun byHash(hash: String) = apply { this.txHash = hash }
        fun incoming() = apply { this.direction = TransactionDirection.INCOMING }
        fun outgoing() = apply { this.direction = TransactionDirection.OUTGOING }
        fun confirmed() = apply { this.isConfirmed = true }
        fun unconfirmed() = apply { this.isConfirmed = false }
        fun minConfirmations(n: Int) = apply { this.minConfirmations = n }
        fun forAccount(index: Int) = apply { this.accountIndex = index }
        fun forSubaddresses(vararg indices: Int) = apply { this.subaddressIndices = indices.toSet() }
        fun heightRange(min: Long? = null, max: Long? = null) = apply {
            this.minHeight = min
            this.maxHeight = max
        }
        fun amountRange(min: Long? = null, max: Long? = null) = apply {
            this.minAmount = min
            this.maxAmount = max
        }
        fun dateRange(from: Long? = null, to: Long? = null) = apply {
            this.fromTimestamp = from
            this.toTimestamp = to
        }
        fun withPaymentId() = apply { this.hasPaymentId = true }
        fun withoutPaymentId() = apply { this.hasPaymentId = false }
        fun paymentId(id: String) = apply { this.paymentId = id }

        fun build() = TransactionQuery(
            txHash = txHash,
            direction = direction,
            isConfirmed = isConfirmed,
            minConfirmations = minConfirmations,
            accountIndex = accountIndex,
            subaddressIndices = subaddressIndices,
            minHeight = minHeight,
            maxHeight = maxHeight,
            minAmount = minAmount,
            maxAmount = maxAmount,
            fromTimestamp = fromTimestamp,
            toTimestamp = toTimestamp,
            hasPaymentId = hasPaymentId,
            paymentId = paymentId
        )
    }

    companion object {
        fun all() = Builder().build()
        fun byHash(hash: String) = Builder().byHash(hash).build()
        fun incoming() = Builder().incoming().build()
        fun outgoing() = Builder().outgoing().build()
        fun confirmed() = Builder().confirmed().build()
        fun unconfirmed() = Builder().unconfirmed().build()
        fun forAccount(index: Int) = Builder().forAccount(index).build()
        fun builder() = Builder()
    }
}

/**
 * Transaction history manager interface.
 */
interface TransactionHistory {
    /**
     * Get all transactions.
     */
    suspend fun getTransactions(): List<TransactionRecord>

    /**
     * Query transactions with filters.
     */
    suspend fun queryTransactions(query: TransactionQuery): List<TransactionRecord>

    /**
     * Get a specific transaction by hash.
     */
    suspend fun getTransaction(txHash: String): TransactionRecord?

    /**
     * Get transaction count.
     */
    suspend fun getTransactionCount(): Int

    /**
     * Get incoming transaction count.
     */
    suspend fun getIncomingCount(): Int

    /**
     * Get outgoing transaction count.
     */
    suspend fun getOutgoingCount(): Int

    /**
     * Get total balance change from transactions (incoming - outgoing - fees).
     */
    suspend fun calculateBalance(): Long

    /**
     * Add a transaction to history.
     */
    suspend fun addTransaction(tx: TransactionRecord)

    /**
     * Update transaction confirmation status.
     */
    suspend fun updateConfirmations(txHash: String, blockHeight: Long, confirmations: Int)
}

/**
 * In-memory transaction history implementation.
 */
class InMemoryTransactionHistory : TransactionHistory {

    private val transactions = mutableListOf<TransactionRecord>()

    override suspend fun getTransactions(): List<TransactionRecord> {
        return transactions.sortedByDescending { it.timestamp }
    }

    override suspend fun queryTransactions(query: TransactionQuery): List<TransactionRecord> {
        return query.filter(transactions).sortedByDescending { it.timestamp }
    }

    override suspend fun getTransaction(txHash: String): TransactionRecord? {
        return transactions.find { it.txHash == txHash }
    }

    override suspend fun getTransactionCount(): Int = transactions.size

    override suspend fun getIncomingCount(): Int =
        transactions.count { it.direction == TransactionDirection.INCOMING }

    override suspend fun getOutgoingCount(): Int =
        transactions.count { it.direction == TransactionDirection.OUTGOING }

    override suspend fun calculateBalance(): Long {
        return transactions.sumOf { tx ->
            when (tx.direction) {
                TransactionDirection.INCOMING -> tx.amount
                TransactionDirection.OUTGOING -> -tx.amount - tx.fee
            }
        }
    }

    override suspend fun addTransaction(tx: TransactionRecord) {
        // Remove existing if updating
        transactions.removeAll { it.txHash == tx.txHash }
        transactions.add(tx)
    }

    override suspend fun updateConfirmations(txHash: String, blockHeight: Long, confirmations: Int) {
        val index = transactions.indexOfFirst { it.txHash == txHash }
        if (index >= 0) {
            val tx = transactions[index]
            transactions[index] = tx.copy(
                blockHeight = blockHeight,
                confirmations = confirmations,
                isConfirmed = confirmations > 0
            )
        }
    }

    fun loadFromRecords(records: List<TransactionRecord>) {
        transactions.clear()
        transactions.addAll(records)
    }

    fun toRecords(): List<TransactionRecord> = transactions.toList()
}
