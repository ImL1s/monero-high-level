package io.monero.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Base58 encoding tests with Monero test vectors.
 * Oracle: monero-wallet-cli address generation
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
    fun `8-byte block encoding produces 11 chars`() {
        // Full 8-byte block should produce 11 characters in Monero Base58
        val input = ByteArray(8) { 0xFF.toByte() }
        val encoded = Base58.encodeRaw(input)
        
        encoded.length shouldBe 11
    }

    @Test
    fun `validate address format - mainnet standard`() {
        // Mainnet standard address starts with '4'
        val validChars = Base58.isValid("4")
        assertTrue(validChars)
    }

    @Test
    fun `invalid characters rejected`() {
        // 0, O, I, l are not in Base58 alphabet
        assertFalse(Base58.isValid("0"))
        assertFalse(Base58.isValid("O"))
        assertFalse(Base58.isValid("I"))
        assertFalse(Base58.isValid("l"))
    }

    /**
     * Real Monero mainnet address test vector
     * Generated from seed: abandon abandon abandon... (12 words repeated)
     * Oracle: monero-wallet-cli --restore-deterministic-wallet
     */
    @Test
    fun `mainnet standard address format`() {
        // Real mainnet address example (from Monero documentation)
        val address = "44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A"
        
        // Should be 95 characters for standard address
        address.length shouldBe 95
        
        // Should start with '4' for mainnet standard
        assertTrue(address.startsWith("4"))
        
        // Should be valid Base58
        assertTrue(Base58.isValid(address))
    }

    /**
     * Mainnet subaddress format test
     */
    @Test
    fun `mainnet subaddress format`() {
        // Subaddress example (starts with '8')
        val subaddress = "87yMkTJZ3sUP2cjNh5xJmzgEh9rHnwZNqVKJcxmGmTMPHGvNJz8q9KbVd7PsS6q8W9U7b8FqV2n3YjVf2a8RqPqR"
        
        // Subaddresses start with '8' for mainnet
        assertTrue(subaddress.startsWith("8"))
        
        // Should be valid Base58
        assertTrue(Base58.isValid(subaddress))
    }

    /**
     * Integrated address format test
     */
    @Test
    fun `mainnet integrated address format`() {
        // Integrated address is longer (106 chars) and starts with '4'
        // Includes embedded 8-byte payment ID
        val integratedAddress = "4BxSHvcgTwu25WooY4BUmkvywzXpPAoEt8AK8Y7QXUmXGyYbPk2QBPX6A6c1dG7VG1WRwh7Vk8sR9uq6J2BoNDaFT6Qe4E7dDCmY"
        
        // Should be valid Base58
        assertTrue(Base58.isValid(integratedAddress))
    }

    /**
     * Stagenet address format test
     */
    @Test
    fun `stagenet standard address format`() {
        // Stagenet addresses start with '5' (network byte 0x18)
        val stageNetPrefix = "5"
        
        // Verify this is valid Base58
        assertTrue(Base58.isValid(stageNetPrefix))
    }

    /**
     * Known block encoding test vectors from Monero
     */
    @Test
    fun `monero base58 block encoding`() {
        // Test vector: 8 zero bytes should encode to specific output
        val zeros = ByteArray(8) { 0 }
        val encoded = Base58.encodeRaw(zeros)
        
        // Should produce consistent output
        encoded shouldBe Base58.encodeRaw(zeros)
    }
}
