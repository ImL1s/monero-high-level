package io.monero.wallet

import io.monero.wallet.internal.DefaultWalletManager
import io.monero.wallet.internal.InMemoryWallet
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WalletManagerTest {

    private lateinit var manager: DefaultWalletManager

    @BeforeTest
    fun setup() {
        manager = DefaultWalletManager()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wallet Lifecycle Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testCreateWallet() = runTest {
        val config = WalletConfig(
            path = "/test/wallet1",
            password = "test123",
            network = Network.STAGENET
        )

        val result = manager.createWallet(config)

        assertNotNull(result.wallet)
        assertEquals(25, result.mnemonic.size)
        assertTrue(result.primaryAddress.isNotEmpty())
        assertTrue(result.primaryAddress.startsWith("5")) // Stagenet starts with 5
        assertFalse(result.wallet.isViewOnly)
    }

    @Test
    fun testRestoreFromMnemonic() = runTest {
        // First create a wallet to get a valid mnemonic
        val config1 = WalletConfig("/test/wallet1", "pass1", Network.MAINNET)
        val created = manager.createWallet(config1)
        val mnemonic = created.mnemonic
        val originalAddress = created.primaryAddress

        // Restore using the same mnemonic
        val config2 = WalletConfig("/test/wallet2", "pass2", Network.MAINNET)
        val restored = manager.restoreFromMnemonic(config2, mnemonic, 1000000L)

        assertEquals(originalAddress, restored.primaryAddress)
        assertEquals(1000000L, manager.getRestoreHeight(restored))
    }

    @Test
    fun testRestoreFromKeysViewOnly() = runTest {
        // Create wallet first
        val config1 = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config1)
        val inMemory = created.wallet as InMemoryWallet

        // Get keys
        val address = created.primaryAddress
        val viewKeyHex = inMemory.state.privateViewKey.toHex()

        // Restore as view-only
        val config2 = WalletConfig("/test/wallet2", "pass2", Network.STAGENET)
        val viewOnly = manager.restoreFromKeys(
            config = config2,
            address = address,
            viewKey = viewKeyHex,
            spendKey = null, // View-only
            restoreHeight = 500000L
        )

        assertTrue(viewOnly.isViewOnly)
        assertEquals(address, viewOnly.primaryAddress)
        assertEquals(WalletType.VIEW_ONLY, manager.getWalletType(viewOnly))
    }

    @Test
    fun testRestoreFromKeysFull() = runTest {
        // Create wallet first
        val config1 = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config1)
        val inMemory = created.wallet as InMemoryWallet

        // Get keys
        val address = created.primaryAddress
        val viewKeyHex = inMemory.state.privateViewKey.toHex()
        val spendKeyHex = inMemory.state.privateSpendKey!!.toHex()

        // Restore with spend key
        val config2 = WalletConfig("/test/wallet2", "pass2", Network.STAGENET)
        val fullWallet = manager.restoreFromKeys(
            config = config2,
            address = address,
            viewKey = viewKeyHex,
            spendKey = spendKeyHex,
            restoreHeight = 500000L
        )

        assertFalse(fullWallet.isViewOnly)
        assertEquals(address, fullWallet.primaryAddress)
        assertEquals(WalletType.FULL, manager.getWalletType(fullWallet))
    }

    @Test
    fun testGetMnemonic() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1", Network.MAINNET)
        val created = manager.createWallet(config)

        val mnemonic = manager.getMnemonic(created.wallet)
        assertEquals(created.mnemonic, mnemonic)
    }

    @Test
    fun testGetMnemonicViewOnlyThrows() = runTest {
        // Create view-only wallet
        val config1 = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config1)
        val inMemory = created.wallet as InMemoryWallet

        val viewOnly = manager.restoreFromKeys(
            config = WalletConfig("/test/view", "pass", Network.STAGENET),
            address = created.primaryAddress,
            viewKey = inMemory.state.privateViewKey.toHex(),
            spendKey = null,
            restoreHeight = 0
        )

        assertFailsWith<WalletException.ViewOnlyWallet> {
            manager.getMnemonic(viewOnly)
        }
    }

    @Test
    fun testCloseWallet() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        assertTrue(manager.walletExists(config.path))

        manager.closeWallet(created.wallet)

        assertFalse(manager.walletExists(config.path))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account Management Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testGetAccounts() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        val accounts = manager.getAccounts(created.wallet)

        assertEquals(1, accounts.size)
        assertEquals(0, accounts[0].index)
        assertEquals("Primary account", accounts[0].label)
    }

    @Test
    fun testCreateAccount() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config)

        val newAccount = manager.createAccount(created.wallet, "Savings")

        assertEquals(1, newAccount.index)
        assertEquals("Savings", newAccount.label)
        assertTrue(newAccount.primaryAddress.isNotEmpty())

        val accounts = manager.getAccounts(created.wallet)
        assertEquals(2, accounts.size)
    }

    @Test
    fun testSetAccountLabel() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        manager.setAccountLabel(created.wallet, 0, "Main Wallet")

        val account = manager.getAccount(created.wallet, 0)
        assertEquals("Main Wallet", account.label)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subaddress Management Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testGetSubaddresses() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config)

        val subaddresses = manager.getSubaddresses(created.wallet, 0)

        assertEquals(1, subaddresses.size)
        assertEquals(0, subaddresses[0].addressIndex)
        assertEquals(created.primaryAddress, subaddresses[0].address)
    }

    @Test
    fun testCreateSubaddress() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config)

        val newSub = manager.createSubaddress(created.wallet, 0, "Donation")

        assertEquals(0, newSub.accountIndex)
        assertEquals(1, newSub.addressIndex)
        assertEquals("Donation", newSub.label)
        assertTrue(newSub.address.startsWith("7")) // Stagenet subaddress

        val subaddresses = manager.getSubaddresses(created.wallet, 0)
        assertEquals(2, subaddresses.size)
    }

    @Test
    fun testSetSubaddressLabel() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        // Create a subaddress first
        manager.createSubaddress(created.wallet, 0, "Old Label")

        // Change label
        manager.setSubaddressLabel(created.wallet, 0, 1, "New Label")

        val subaddress = manager.getSubaddress(created.wallet, 0, 1)
        assertEquals("New Label", subaddress.label)
    }

    @Test
    fun testFindSubaddress() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1", Network.STAGENET)
        val created = manager.createWallet(config)

        // Create some subaddresses
        val sub1 = manager.createSubaddress(created.wallet, 0, "Sub 1")
        val sub2 = manager.createSubaddress(created.wallet, 0, "Sub 2")

        // Find them
        val found1 = manager.findSubaddress(created.wallet, sub1.address)
        val found2 = manager.findSubaddress(created.wallet, sub2.address)
        val foundPrimary = manager.findSubaddress(created.wallet, created.primaryAddress)

        assertEquals(0 to 1, found1)
        assertEquals(0 to 2, found2)
        assertEquals(0 to 0, foundPrimary)
    }

    @Test
    fun testFindSubaddressNotFound() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        val found = manager.findSubaddress(created.wallet, "some_random_address")
        assertNull(found)
    }

    @Test
    fun testGetSubaddressCount() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        assertEquals(1, manager.getSubaddressCount(created.wallet, 0))

        manager.createSubaddress(created.wallet, 0, "")
        manager.createSubaddress(created.wallet, 0, "")

        assertEquals(3, manager.getSubaddressCount(created.wallet, 0))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error Handling Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testAccountNotFound() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        assertFailsWith<WalletException.AccountNotFound> {
            manager.getAccount(created.wallet, 999)
        }
    }

    @Test
    fun testSubaddressNotFound() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")
        val created = manager.createWallet(config)

        assertFailsWith<WalletException.SubaddressNotFound> {
            manager.getSubaddress(created.wallet, 0, 999)
        }
    }

    @Test
    fun testInvalidMnemonic() = runTest {
        val config = WalletConfig("/test/wallet1", "pass1")

        assertFailsWith<WalletException.InvalidMnemonic> {
            manager.restoreFromMnemonic(config, listOf("invalid", "words"), 0)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalStdlibApi::class)
    private fun ByteArray.toHex(): String = toHexString()
}
