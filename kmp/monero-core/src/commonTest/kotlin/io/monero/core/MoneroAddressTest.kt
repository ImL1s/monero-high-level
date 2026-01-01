package io.monero.core

import io.monero.crypto.Base58
import kotlin.test.*

/**
 * Comprehensive test suite for Monero address handling.
 * Covers standard addresses, subaddresses, and integrated addresses.
 */
class MoneroAddressTest {

    // ======== Parse Standard Address (using generated addresses) ========

    @Test
    fun `parse mainnet standard address`() {
        // Create a valid mainnet address from known keys
        val spendKey = ByteArray(32) { 0x01.toByte() }
        val viewKey = ByteArray(32) { 0x02.toByte() }
        val created = MoneroAddress.fromKeys(spendKey, viewKey, MoneroAddress.Network.MAINNET)

        val address = MoneroAddress.parse(created.rawAddress)
        
        assertEquals(MoneroAddress.Network.MAINNET, address.network)
        assertEquals(MoneroAddress.AddressType.STANDARD, address.type)
        assertEquals(32, address.publicSpendKey.size)
        assertEquals(32, address.publicViewKey.size)
        assertNull(address.paymentId)
        assertFalse(address.isSubaddress)
        assertFalse(address.isIntegrated)
    }

    @Test
    fun `parse stagenet standard address`() {
        val spendKey = ByteArray(32) { 0x03.toByte() }
        val viewKey = ByteArray(32) { 0x04.toByte() }
        val created = MoneroAddress.fromKeys(spendKey, viewKey, MoneroAddress.Network.STAGENET)

        val address = MoneroAddress.parse(created.rawAddress)
        
        assertEquals(MoneroAddress.Network.STAGENET, address.network)
        assertEquals(MoneroAddress.AddressType.STANDARD, address.type)
        assertEquals(32, address.publicSpendKey.size)
        assertEquals(32, address.publicViewKey.size)
    }

    @Test
    fun `parse mainnet subaddress`() {
        // Create subaddress by encoding with prefix 42
        val subData = byteArrayOf(42) + ByteArray(64) { it.toByte() }
        val encoded = Base58.encode(subData)
        
        val address = MoneroAddress.parse(encoded)
        
        assertEquals(MoneroAddress.Network.MAINNET, address.network)
        assertEquals(MoneroAddress.AddressType.SUBADDRESS, address.type)
        assertTrue(address.isSubaddress)
        assertFalse(address.isIntegrated)
    }

    @Test
    fun `parse integrated address`() {
        // Create standard address first, then make integrated
        val standard = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32), MoneroAddress.Network.MAINNET)
        val paymentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val integrated = MoneroAddress.createIntegrated(standard, paymentId)

        val address = MoneroAddress.parse(integrated.rawAddress)
        
        assertEquals(MoneroAddress.Network.MAINNET, address.network)
        assertEquals(MoneroAddress.AddressType.INTEGRATED, address.type)
        assertTrue(address.isIntegrated)
        assertNotNull(address.paymentId)
        assertEquals(8, address.paymentId!!.size)
    }

    // ======== Address Validation ========

    @Test
    fun `empty address throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.parse("")
        }
    }

    @Test
    fun `blank address throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.parse("   ")
        }
    }

    @Test
    fun `invalid base58 throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.parse("InvalidAddress0OIl") // Contains invalid Base58 chars
        }
    }

    @Test
    fun `unknown prefix throws exception`() {
        // Create address with invalid prefix
        val invalidPrefix = byteArrayOf(99) + ByteArray(64)
        val encoded = Base58.encode(invalidPrefix)

        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.parse(encoded)
        }
    }

    // ======== Create Address from Keys ========

    @Test
    fun `fromKeys creates valid mainnet address`() {
        val spendKey = ByteArray(32) { 0x11.toByte() }
        val viewKey = ByteArray(32) { 0x22.toByte() }

        val address = MoneroAddress.fromKeys(spendKey, viewKey, MoneroAddress.Network.MAINNET)

        assertEquals(MoneroAddress.Network.MAINNET, address.network)
        assertEquals(MoneroAddress.AddressType.STANDARD, address.type)
        assertContentEquals(spendKey, address.publicSpendKey)
        assertContentEquals(viewKey, address.publicViewKey)
        assertTrue(address.rawAddress.startsWith("4"))
    }

    @Test
    fun `fromKeys creates valid stagenet address`() {
        val spendKey = ByteArray(32) { 0x33.toByte() }
        val viewKey = ByteArray(32) { 0x44.toByte() }

        val address = MoneroAddress.fromKeys(spendKey, viewKey, MoneroAddress.Network.STAGENET)

        assertEquals(MoneroAddress.Network.STAGENET, address.network)
        assertTrue(address.rawAddress.startsWith("5"))
    }

    @Test
    fun `fromKeys with invalid key size throws`() {
        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.fromKeys(ByteArray(31), ByteArray(32))
        }
        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.fromKeys(ByteArray(32), ByteArray(33))
        }
    }

    // ======== Integrated Address ========

    @Test
    fun `createIntegrated from standard address`() {
        val spendKey = ByteArray(32) { 0x55.toByte() }
        val viewKey = ByteArray(32) { 0x66.toByte() }
        val standard = MoneroAddress.fromKeys(spendKey, viewKey, MoneroAddress.Network.MAINNET)

        val paymentId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val integrated = MoneroAddress.createIntegrated(standard, paymentId)

        assertEquals(MoneroAddress.AddressType.INTEGRATED, integrated.type)
        assertTrue(integrated.isIntegrated)
        assertContentEquals(paymentId, integrated.paymentId)
        assertContentEquals(standard.publicSpendKey, integrated.publicSpendKey)
        assertContentEquals(standard.publicViewKey, integrated.publicViewKey)
    }

    @Test
    fun `createIntegrated requires 8 byte payment ID`() {
        val standard = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32))

        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.createIntegrated(standard, ByteArray(7))
        }
        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.createIntegrated(standard, ByteArray(9))
        }
    }

    @Test
    fun `createIntegrated from non-standard throws`() {
        val subaddressData = byteArrayOf(42) + ByteArray(64)
        val encoded = Base58.encode(subaddressData)
        val subaddress = MoneroAddress.parse(encoded)

        assertFailsWith<IllegalArgumentException> {
            MoneroAddress.createIntegrated(subaddress, ByteArray(8))
        }
    }

    // ======== Address Roundtrip ========

    @Test
    fun `address roundtrip preserves data`() {
        val spendKey = ByteArray(32) { it.toByte() }
        val viewKey = ByteArray(32) { (255 - it).toByte() }

        val original = MoneroAddress.fromKeys(spendKey, viewKey, MoneroAddress.Network.MAINNET)
        val parsed = MoneroAddress.parse(original.rawAddress)

        assertEquals(original.network, parsed.network)
        assertEquals(original.type, parsed.type)
        assertContentEquals(original.publicSpendKey, parsed.publicSpendKey)
        assertContentEquals(original.publicViewKey, parsed.publicViewKey)
        assertEquals(original.rawAddress, parsed.rawAddress)
    }

    @Test
    fun `integrated address roundtrip preserves payment ID`() {
        val standard = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32))
        val paymentId = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
                                    0xEE.toByte(), 0xFF.toByte(), 0x11.toByte(), 0x22.toByte())
        
        val integrated = MoneroAddress.createIntegrated(standard, paymentId)
        val parsed = MoneroAddress.parse(integrated.rawAddress)

        assertContentEquals(paymentId, parsed.paymentId)
    }

    // ======== Network Prefix Detection ========

    @Test
    fun `all mainnet prefixes detected correctly`() {
        // Standard (18)
        val standard = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32), MoneroAddress.Network.MAINNET)
        assertEquals(MoneroAddress.AddressType.STANDARD, standard.type)

        // Subaddress (42) - create by encoding directly
        val subData = byteArrayOf(42) + ByteArray(64)
        val subaddress = MoneroAddress.parse(Base58.encode(subData))
        assertEquals(MoneroAddress.AddressType.SUBADDRESS, subaddress.type)

        // Integrated (19)
        val integrated = MoneroAddress.createIntegrated(standard, ByteArray(8))
        assertEquals(MoneroAddress.AddressType.INTEGRATED, integrated.type)
    }

    @Test
    fun `all stagenet prefixes detected correctly`() {
        // Standard (24)
        val standard = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32), MoneroAddress.Network.STAGENET)
        assertEquals(MoneroAddress.Network.STAGENET, standard.network)
        assertEquals(MoneroAddress.AddressType.STANDARD, standard.type)

        // Subaddress (36)
        val subData = byteArrayOf(36) + ByteArray(64)
        val subaddress = MoneroAddress.parse(Base58.encode(subData))
        assertEquals(MoneroAddress.Network.STAGENET, subaddress.network)
        assertEquals(MoneroAddress.AddressType.SUBADDRESS, subaddress.type)

        // Integrated (25)
        val integrated = MoneroAddress.createIntegrated(standard, ByteArray(8))
        assertEquals(MoneroAddress.AddressType.INTEGRATED, integrated.type)
    }

    @Test
    fun `all testnet prefixes detected correctly`() {
        // Standard (53)
        val standard = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32), MoneroAddress.Network.TESTNET)
        assertEquals(MoneroAddress.Network.TESTNET, standard.network)

        // Subaddress (63)
        val subData = byteArrayOf(63) + ByteArray(64)
        val subaddress = MoneroAddress.parse(Base58.encode(subData))
        assertEquals(MoneroAddress.Network.TESTNET, subaddress.network)
        assertEquals(MoneroAddress.AddressType.SUBADDRESS, subaddress.type)
    }

    // ======== Equality and HashCode ========

    @Test
    fun `same address equals`() {
        val addr1 = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32))
        val addr2 = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32))

        assertEquals(addr1, addr2)
        assertEquals(addr1.hashCode(), addr2.hashCode())
    }

    @Test
    fun `different addresses not equal`() {
        val addr1 = MoneroAddress.fromKeys(ByteArray(32) { 0 }, ByteArray(32))
        val addr2 = MoneroAddress.fromKeys(ByteArray(32) { 1 }, ByteArray(32))

        assertNotEquals(addr1, addr2)
    }

    @Test
    fun `toString returns raw address`() {
        val address = MoneroAddress.fromKeys(ByteArray(32), ByteArray(32))
        assertEquals(address.rawAddress, address.toString())
    }
}
