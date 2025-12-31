package io.monero.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Base58 encoding tests with Monero test vectors.
 */
class Base58Test {

    @Test
    fun `encode empty array`() {
        val input = ByteArray(0)
        val encoded = Base58.encodeRaw(input)
        
        encoded shouldBe ""
    }

    @Test
    fun `encode single byte`() {
        val input = byteArrayOf(0x00)
        val encoded = Base58.encodeRaw(input)
        
        encoded shouldNotBe ""
    }

    @Test
    fun `round trip encoding`() {
        val input = ByteArray(32) { it.toByte() }
        val encoded = Base58.encode(input)
        val decoded = Base58.decode(encoded)
        
        decoded shouldBe input
    }

    @Test
    fun `round trip raw encoding`() {
        val input = ByteArray(32) { it.toByte() }
        val encoded = Base58.encodeRaw(input)
        val decoded = Base58.decodeRaw(encoded)
        
        decoded shouldBe input
    }

    @Test
    fun `8-byte block encoding`() {
        // Full block should produce 11 characters
        val input = ByteArray(8) { 0xFF.toByte() }
        val encoded = Base58.encodeRaw(input)
        
        encoded.length shouldBe 11
    }

    @Test
    fun `validate address format - mainnet standard`() {
        // Mainnet standard address starts with '4'
        // This is a placeholder - need real test vectors
        val validChars = Base58.isValid("4")
        assertTrue(validChars)
    }

    @Test
    fun `invalid characters rejected`() {
        val hasInvalid = !Base58.isValid("0OIl")
        assertTrue(hasInvalid)
    }

    @Test
    fun `mainnet address validation placeholder`() {
        // TODO: Add real Monero address test vectors
        // Example mainnet address starts with '4' (tag 18)
        // Subaddress starts with '8' (tag 42)
    }

    @Test
    fun `stagenet address validation placeholder`() {
        // TODO: Add real stagenet address test vectors
        // Stagenet address starts with '5' (tag 24)
    }
}
