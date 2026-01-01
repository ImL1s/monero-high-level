package io.monero.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Key derivation tests.
 * Oracle: monero-wallet-cli key generation
 */
class KeyDerivationTest {

    @Test
    fun `derive wallet keys produces all 32-byte keys`() {
        val seed = ByteArray(32) { it.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)

        keys.privateSpendKey shouldHaveSize 32
        keys.privateViewKey shouldHaveSize 32
        keys.publicSpendKey shouldHaveSize 32
        keys.publicViewKey shouldHaveSize 32
    }

    @Test
    fun `key derivation is deterministic`() {
        val seed = ByteArray(32) { 0x42.toByte() }

        val keys1 = KeyDerivation.deriveWalletKeys(seed)
        val keys2 = KeyDerivation.deriveWalletKeys(seed)

        keys1.privateSpendKey shouldBe keys2.privateSpendKey
        keys1.privateViewKey shouldBe keys2.privateViewKey
        keys1.publicSpendKey shouldBe keys2.publicSpendKey
        keys1.publicViewKey shouldBe keys2.publicViewKey
    }

    @Test
    fun `different seeds produce different keys`() {
        val seed1 = ByteArray(32) { 0x01.toByte() }
        val seed2 = ByteArray(32) { 0x02.toByte() }

        val keys1 = KeyDerivation.deriveWalletKeys(seed1)
        val keys2 = KeyDerivation.deriveWalletKeys(seed2)

        keys1.privateSpendKey shouldNotBe keys2.privateSpendKey
    }

    @Test
    fun `private and public keys are different`() {
        val seed = ByteArray(32) { it.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)

        keys.privateSpendKey shouldNotBe keys.publicSpendKey
        keys.privateViewKey shouldNotBe keys.publicViewKey
    }

    @Test
    fun `spend and view keys are different`() {
        val seed = ByteArray(32) { it.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)

        keys.privateSpendKey shouldNotBe keys.privateViewKey
        keys.publicSpendKey shouldNotBe keys.publicViewKey
    }

    @Test
    fun `derive from mnemonic produces valid keys`() {
        val seed = ByteArray(32) { it.toByte() }
        val mnemonic = Mnemonic.entropyToMnemonic(seed)
        val keys = KeyDerivation.deriveFromMnemonic(mnemonic)

        keys.privateSpendKey shouldHaveSize 32
        keys.publicSpendKey shouldHaveSize 32
    }

    @Test
    fun `derive standard address produces valid address`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)
        val address = KeyDerivation.deriveStandardAddress(keys)

        address.type shouldBe MoneroAddress.AddressType.STANDARD
        address.network shouldBe MoneroAddress.Network.MAINNET
        address.publicSpendKey shouldBe keys.publicSpendKey
        address.publicViewKey shouldBe keys.publicViewKey
    }

    @Test
    fun `derive stagenet address`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)
        val address = KeyDerivation.deriveStandardAddress(keys, MoneroAddress.Network.STAGENET)

        address.network shouldBe MoneroAddress.Network.STAGENET
    }

    @Test
    fun `derive subaddress at 0-0 returns main address keys`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)
        val subKeys = KeyDerivation.deriveSubaddress(keys, 0, 0)

        subKeys.publicSpendKey shouldBe keys.publicSpendKey
        subKeys.publicViewKey shouldBe keys.publicViewKey
    }

    @Test
    fun `derive subaddress at 0-1 produces different keys`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)
        val subKeys = KeyDerivation.deriveSubaddress(keys, 0, 1)

        subKeys.publicSpendKey shouldNotBe keys.publicSpendKey
    }

    @Test
    fun `different subaddress indices produce different keys`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)

        val sub1 = KeyDerivation.deriveSubaddress(keys, 0, 1)
        val sub2 = KeyDerivation.deriveSubaddress(keys, 0, 2)

        sub1.publicSpendKey shouldNotBe sub2.publicSpendKey
    }

    @Test
    fun `subaddress derivation is deterministic`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)

        val sub1 = KeyDerivation.deriveSubaddress(keys, 1, 5)
        val sub2 = KeyDerivation.deriveSubaddress(keys, 1, 5)

        sub1.publicSpendKey shouldBe sub2.publicSpendKey
        sub1.publicViewKey shouldBe sub2.publicViewKey
    }

    @Test
    fun `subaddress has 32-byte keys`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)
        val subKeys = KeyDerivation.deriveSubaddress(keys, 2, 10)

        subKeys.publicSpendKey shouldHaveSize 32
        subKeys.publicViewKey shouldHaveSize 32
    }
}
