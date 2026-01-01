package io.monero.wallet.transaction

import io.monero.wallet.storage.TransactionDirection
import io.monero.wallet.storage.TransactionRecord
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

class TransactionQueryTest {

    private lateinit var history: InMemoryTransactionHistory
    private val now = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        history = InMemoryTransactionHistory()
        history.loadFromRecords(createTestTransactions())
    }

    private fun createTestTransactions(): List<TransactionRecord> = listOf(
        TransactionRecord(
            txHash = "tx1",
            blockHeight = 1000,
            timestamp = now - 86400000,  // Yesterday
            amount = 1_000_000_000_000,   // 1 XMR
            fee = 10_000_000,
            direction = TransactionDirection.INCOMING,
            accountIndex = 0,
            subaddressIndices = listOf(0),
            paymentId = null,
            note = null,
            isConfirmed = true,
            confirmations = 100
        ),
        TransactionRecord(
            txHash = "tx2",
            blockHeight = 1001,
            timestamp = now - 43200000,  // 12 hours ago
            amount = 500_000_000_000,     // 0.5 XMR
            fee = 8_000_000,
            direction = TransactionDirection.OUTGOING,
            accountIndex = 0,
            subaddressIndices = listOf(0),
            paymentId = "abc123",
            note = "Payment for services",
            isConfirmed = true,
            confirmations = 50
        ),
        TransactionRecord(
            txHash = "tx3",
            blockHeight = null,
            timestamp = now - 3600000,    // 1 hour ago
            amount = 250_000_000_000,     // 0.25 XMR
            fee = 5_000_000,
            direction = TransactionDirection.INCOMING,
            accountIndex = 1,
            subaddressIndices = listOf(1, 2),
            paymentId = null,
            note = null,
            isConfirmed = false,
            confirmations = 0
        ),
        TransactionRecord(
            txHash = "tx4",
            blockHeight = 1002,
            timestamp = now - 1800000,    // 30 min ago
            amount = 2_000_000_000_000,   // 2 XMR
            fee = 15_000_000,
            direction = TransactionDirection.INCOMING,
            accountIndex = 0,
            subaddressIndices = listOf(3),
            paymentId = "def456",
            note = "Donation",
            isConfirmed = true,
            confirmations = 10
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Query Filter Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testQueryAll() = runTest {
        val result = history.queryTransactions(TransactionQuery.all())
        assertEquals(4, result.size)
    }

    @Test
    fun testQueryByHash() = runTest {
        val result = history.queryTransactions(TransactionQuery.byHash("tx2"))
        assertEquals(1, result.size)
        assertEquals("tx2", result[0].txHash)
    }

    @Test
    fun testQueryIncoming() = runTest {
        val result = history.queryTransactions(TransactionQuery.incoming())
        assertEquals(3, result.size)
        assertTrue(result.all { it.direction == TransactionDirection.INCOMING })
    }

    @Test
    fun testQueryOutgoing() = runTest {
        val result = history.queryTransactions(TransactionQuery.outgoing())
        assertEquals(1, result.size)
        assertEquals("tx2", result[0].txHash)
    }

    @Test
    fun testQueryConfirmed() = runTest {
        val result = history.queryTransactions(TransactionQuery.confirmed())
        assertEquals(3, result.size)
        assertTrue(result.all { it.isConfirmed })
    }

    @Test
    fun testQueryUnconfirmed() = runTest {
        val result = history.queryTransactions(TransactionQuery.unconfirmed())
        assertEquals(1, result.size)
        assertEquals("tx3", result[0].txHash)
    }

    @Test
    fun testQueryForAccount() = runTest {
        val result = history.queryTransactions(TransactionQuery.forAccount(1))
        assertEquals(1, result.size)
        assertEquals("tx3", result[0].txHash)
    }

    @Test
    fun testQueryForSubaddresses() = runTest {
        val query = TransactionQuery.builder()
            .forSubaddresses(1, 2)
            .build()
        val result = history.queryTransactions(query)
        assertEquals(1, result.size)
        assertEquals("tx3", result[0].txHash)
    }

    @Test
    fun testQueryMinConfirmations() = runTest {
        val query = TransactionQuery.builder()
            .minConfirmations(20)
            .build()
        val result = history.queryTransactions(query)
        assertEquals(2, result.size)
        assertTrue(result.all { it.confirmations >= 20 })
    }

    @Test
    fun testQueryHeightRange() = runTest {
        val query = TransactionQuery.builder()
            .heightRange(1001, 1002)
            .build()
        val result = history.queryTransactions(query)
        assertEquals(2, result.size)
    }

    @Test
    fun testQueryAmountRange() = runTest {
        val query = TransactionQuery.builder()
            .amountRange(min = 800_000_000_000, max = 1_500_000_000_000)
            .build()
        val result = history.queryTransactions(query)
        assertEquals(1, result.size)
        assertEquals("tx1", result[0].txHash)
    }

    @Test
    fun testQueryWithPaymentId() = runTest {
        val query = TransactionQuery.builder()
            .withPaymentId()
            .build()
        val result = history.queryTransactions(query)
        assertEquals(2, result.size)
        assertTrue(result.all { it.paymentId != null })
    }

    @Test
    fun testQuerySpecificPaymentId() = runTest {
        val query = TransactionQuery.builder()
            .paymentId("abc123")
            .build()
        val result = history.queryTransactions(query)
        assertEquals(1, result.size)
        assertEquals("tx2", result[0].txHash)
    }

    @Test
    fun testQueryCombined() = runTest {
        val query = TransactionQuery.builder()
            .incoming()
            .confirmed()
            .forAccount(0)
            .build()
        val result = history.queryTransactions(query)
        assertEquals(2, result.size)
        assertTrue(result.all {
            it.direction == TransactionDirection.INCOMING &&
            it.isConfirmed &&
            it.accountIndex == 0
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction History Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testGetTransaction() = runTest {
        val tx = history.getTransaction("tx1")
        assertNotNull(tx)
        assertEquals("tx1", tx.txHash)
    }

    @Test
    fun testGetTransactionNotFound() = runTest {
        val tx = history.getTransaction("nonexistent")
        assertNull(tx)
    }

    @Test
    fun testGetTransactionCount() = runTest {
        assertEquals(4, history.getTransactionCount())
    }

    @Test
    fun testGetIncomingCount() = runTest {
        assertEquals(3, history.getIncomingCount())
    }

    @Test
    fun testGetOutgoingCount() = runTest {
        assertEquals(1, history.getOutgoingCount())
    }

    @Test
    fun testCalculateBalance() = runTest {
        // Incoming: 1 + 0.25 + 2 = 3.25 XMR
        // Outgoing: 0.5 + fees
        val balance = history.calculateBalance()
        val expectedIncoming = 1_000_000_000_000L + 250_000_000_000L + 2_000_000_000_000L
        val expectedOutgoing = 500_000_000_000L + 8_000_000L
        assertEquals(expectedIncoming - expectedOutgoing, balance)
    }

    @Test
    fun testAddTransaction() = runTest {
        val newTx = TransactionRecord(
            txHash = "tx5",
            blockHeight = 1003,
            timestamp = now,
            amount = 100_000_000_000,
            fee = 5_000_000,
            direction = TransactionDirection.INCOMING,
            accountIndex = 0,
            subaddressIndices = listOf(0),
            paymentId = null,
            note = null,
            isConfirmed = true,
            confirmations = 5
        )

        history.addTransaction(newTx)

        assertEquals(5, history.getTransactionCount())
        val found = history.getTransaction("tx5")
        assertNotNull(found)
    }

    @Test
    fun testUpdateConfirmations() = runTest {
        history.updateConfirmations("tx3", 1003, 5)

        val tx = history.getTransaction("tx3")
        assertNotNull(tx)
        assertEquals(1003, tx.blockHeight)
        assertEquals(5, tx.confirmations)
        assertTrue(tx.isConfirmed)
    }

    @Test
    fun testTransactionsOrderedByTimestamp() = runTest {
        val result = history.getTransactions()

        for (i in 0 until result.size - 1) {
            assertTrue(result[i].timestamp >= result[i + 1].timestamp)
        }
    }
}
