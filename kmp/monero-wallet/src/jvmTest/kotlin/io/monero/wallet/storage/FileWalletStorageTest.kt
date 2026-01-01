package io.monero.wallet.storage

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class FileWalletStorageTest {

    private lateinit var storage: FileWalletStorage
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        storage = FileWalletStorage()
        tempDir = Files.createTempDirectory("wallet_test").toFile()
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndLoadWallet() = runTest {
        val path = "${tempDir.absolutePath}/test_wallet"
        val data = createTestWalletData()

        storage.save(path, data, "password123")

        assertTrue(storage.exists(path))

        val loaded = storage.load(path, "password123")

        assertEquals(data.network, loaded.network)
        assertEquals(data.primaryAddress, loaded.primaryAddress)
        assertEquals(data.privateViewKey, loaded.privateViewKey)
        assertEquals(data.publicViewKey, loaded.publicViewKey)
        assertEquals(data.privateSpendKey, loaded.privateSpendKey)
        assertEquals(data.publicSpendKey, loaded.publicSpendKey)
        assertEquals(data.mnemonic, loaded.mnemonic)
        assertEquals(data.restoreHeight, loaded.restoreHeight)
        assertEquals(data.accounts.size, loaded.accounts.size)
    }

    @Test
    fun testSaveAndLoadViewOnlyWallet() = runTest {
        val path = "${tempDir.absolutePath}/view_only"
        val data = createTestWalletData().copy(
            privateSpendKey = null,
            mnemonic = null
        )

        storage.save(path, data, "password")

        val loaded = storage.load(path, "password")

        assertNull(loaded.privateSpendKey)
        assertNull(loaded.mnemonic)
        assertEquals(data.primaryAddress, loaded.primaryAddress)
    }

    @Test
    fun testExistsReturnsFalseForNonexistent() = runTest {
        assertFalse(storage.exists("${tempDir.absolutePath}/nonexistent"))
    }

    @Test
    fun testLoadThrowsForNonexistent() = runTest {
        assertFailsWith<WalletStorageException.FileNotFound> {
            storage.load("${tempDir.absolutePath}/nonexistent", "password")
        }
    }

    @Test
    fun testLoadWithWrongPassword() = runTest {
        val path = "${tempDir.absolutePath}/encrypted_wallet"
        storage.save(path, createTestWalletData(), "correct_password")

        assertFailsWith<WalletStorageException.InvalidPassword> {
            storage.load(path, "wrong_password")
        }
    }

    @Test
    fun testDelete() = runTest {
        val path = "${tempDir.absolutePath}/to_delete"
        storage.save(path, createTestWalletData(), "password")

        assertTrue(storage.exists(path))

        val deleted = storage.delete(path)
        assertTrue(deleted)

        assertFalse(storage.exists(path))
    }

    @Test
    fun testDeleteNonexistentReturnsFalse() = runTest {
        val deleted = storage.delete("${tempDir.absolutePath}/nonexistent")
        assertFalse(deleted)
    }

    @Test
    fun testListWallets() = runTest {
        // Create multiple wallets
        storage.save("${tempDir.absolutePath}/wallet1", createTestWalletData(), "pass")
        storage.save("${tempDir.absolutePath}/wallet2", createTestWalletData(), "pass")
        storage.save("${tempDir.absolutePath}/wallet3", createTestWalletData(), "pass")

        val wallets = storage.listWallets(tempDir.absolutePath)

        assertEquals(3, wallets.size)
        assertTrue(wallets.contains("wallet1"))
        assertTrue(wallets.contains("wallet2"))
        assertTrue(wallets.contains("wallet3"))
    }

    @Test
    fun testListWalletsEmptyDirectory() = runTest {
        val emptyDir = File(tempDir, "empty").also { it.mkdirs() }

        val wallets = storage.listWallets(emptyDir.absolutePath)

        assertTrue(wallets.isEmpty())
    }

    @Test
    fun testListWalletsNonexistentDirectory() = runTest {
        val wallets = storage.listWallets("${tempDir.absolutePath}/nonexistent")

        assertTrue(wallets.isEmpty())
    }

    @Test
    fun testGetWalletInfo() = runTest {
        val path = "${tempDir.absolutePath}/info_test"
        val data = createTestWalletData()

        storage.save(path, data, "password")

        val info = storage.getWalletInfo(path)

        assertNotNull(info)
        assertEquals(data.network, info.network)
        assertEquals(data.primaryAddress, info.primaryAddress)
        assertFalse(info.isViewOnly)
    }

    @Test
    fun testGetWalletInfoViewOnly() = runTest {
        val path = "${tempDir.absolutePath}/view_only_info"
        val data = createTestWalletData().copy(privateSpendKey = null)

        storage.save(path, data, "password")

        val info = storage.getWalletInfo(path)

        assertNotNull(info)
        assertTrue(info.isViewOnly)
    }

    @Test
    fun testWalletWithTransactions() = runTest {
        val path = "${tempDir.absolutePath}/with_txs"
        val data = createTestWalletData().copy(
            transactions = listOf(
                TransactionRecord(
                    txHash = "abc123",
                    blockHeight = 100000,
                    timestamp = System.currentTimeMillis(),
                    amount = 1000000000000L,
                    fee = 10000000,
                    direction = TransactionDirection.INCOMING,
                    accountIndex = 0,
                    subaddressIndices = listOf(0),
                    paymentId = null,
                    note = "Test payment",
                    isConfirmed = true,
                    confirmations = 10
                )
            )
        )

        storage.save(path, data, "password")
        val loaded = storage.load(path, "password")

        assertEquals(1, loaded.transactions.size)
        assertEquals("abc123", loaded.transactions[0].txHash)
        assertEquals(100000, loaded.transactions[0].blockHeight)
        assertEquals(TransactionDirection.INCOMING, loaded.transactions[0].direction)
    }

    @Test
    fun testWalletWithAddressBook() = runTest {
        val path = "${tempDir.absolutePath}/with_addressbook"
        val data = createTestWalletData().copy(
            addressBook = listOf(
                AddressBookEntry(
                    id = "entry1",
                    address = "888tNkZrPN6JsEgekjMnABU4TBzc2Dt29EPAvkRxbANsAnjyPbb3iQ1YBRk1UXcdRsiKc9dhwMVgN5S9cQUiyoogDavup3H",
                    label = "My Friend",
                    description = "Work colleague",
                    paymentId = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        )

        storage.save(path, data, "password")
        val loaded = storage.load(path, "password")

        assertEquals(1, loaded.addressBook.size)
        assertEquals("My Friend", loaded.addressBook[0].label)
    }

    @Test
    fun testWalletWithOutputs() = runTest {
        val path = "${tempDir.absolutePath}/with_outputs"
        val data = createTestWalletData().copy(
            outputs = listOf(
                OutputRecord(
                    txHash = "def456",
                    outputIndex = 0,
                    amount = 5000000000000L,
                    keyImage = "keyimage123",
                    globalIndex = 50000000,
                    accountIndex = 0,
                    subaddressIndex = 0,
                    isSpent = false,
                    isFrozen = false,
                    spentHeight = null,
                    unlockTime = 0
                )
            )
        )

        storage.save(path, data, "password")
        val loaded = storage.load(path, "password")

        assertEquals(1, loaded.outputs.size)
        assertEquals("def456", loaded.outputs[0].txHash)
        assertEquals(5000000000000L, loaded.outputs[0].amount)
        assertFalse(loaded.outputs[0].isSpent)
    }

    @Test
    fun testMultipleAccounts() = runTest {
        val path = "${tempDir.absolutePath}/multi_account"
        val data = createTestWalletData().copy(
            accounts = listOf(
                AccountData(
                    index = 0,
                    label = "Primary",
                    subaddresses = listOf(
                        SubaddressData(0, "address0", "Main", false),
                        SubaddressData(1, "address1", "Savings", true)
                    )
                ),
                AccountData(
                    index = 1,
                    label = "Business",
                    subaddresses = listOf(
                        SubaddressData(0, "address2", "Invoices", false)
                    )
                )
            )
        )

        storage.save(path, data, "password")
        val loaded = storage.load(path, "password")

        assertEquals(2, loaded.accounts.size)
        assertEquals("Primary", loaded.accounts[0].label)
        assertEquals("Business", loaded.accounts[1].label)
        assertEquals(2, loaded.accounts[0].subaddresses.size)
        assertEquals(1, loaded.accounts[1].subaddresses.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun createTestWalletData(): WalletData {
        return WalletData(
            version = 1,
            network = NetworkType.STAGENET,
            createdAt = System.currentTimeMillis(),
            restoreHeight = 0,
            primaryAddress = "5GxwDCYLLK1iXqNfMrxABQpF5FokzqFJQX1nPEWcjJUHnHvQBbVq2pJbfPVHGJQG4kckhFDWzZFCPKJbUKkURzELVTXTMd3",
            privateViewKey = "a".repeat(64),
            publicViewKey = "b".repeat(64),
            privateSpendKey = "c".repeat(64),
            publicSpendKey = "d".repeat(64),
            mnemonic = listOf(
                "abbey", "abbey", "abbey", "abbey", "abbey",
                "abbey", "abbey", "abbey", "abbey", "abbey",
                "abbey", "abbey", "abbey", "abbey", "abbey",
                "abbey", "abbey", "abbey", "abbey", "abbey",
                "abbey", "abbey", "abbey", "abbey", "abbey"
            ),
            accounts = listOf(
                AccountData(
                    index = 0,
                    label = "Primary account",
                    subaddresses = listOf(
                        SubaddressData(
                            addressIndex = 0,
                            address = "5GxwDCYLLK1iXqNfMrxABQpF5FokzqFJQX1nPEWcjJUHnHvQBbVq2pJbfPVHGJQG4kckhFDWzZFCPKJbUKkURzELVTXTMd3",
                            label = "Primary address",
                            isUsed = false
                        )
                    )
                )
            ),
            transactions = emptyList(),
            addressBook = emptyList(),
            outputs = emptyList()
        )
    }
}
