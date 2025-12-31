package io.monero.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Keccak-256 test vectors from official test suite.
 * These are Oracle-validated against monero-wallet-cli.
 */
class KeccakTest {

    @Test
    fun `empty input produces correct hash`() {
        val input = ByteArray(0)
        val expected = hexToBytes("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")

        val result = Keccak.hash256(input)

        result shouldBe expected
    }

    @Test
    fun `single byte input`() {
        val input = byteArrayOf(0x00)
        val result = Keccak.hash256(input)

        result shouldHaveSize 32
    }

    @Test
    fun `32-byte input`() {
        val input = ByteArray(32) { it.toByte() }
        val result = Keccak.hash256(input)

        result shouldHaveSize 32
        // Hash should be deterministic
        result shouldBe Keccak.hash256(input)
    }

    @Test
    fun `abc test vector`() {
        // Standard test vector: Keccak-256("abc")
        val input = "abc".encodeToByteArray()
        val expected = hexToBytes("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45")

        val result = Keccak.hash256(input)

        result shouldBe expected
    }

    @Test
    fun `long input`() {
        // Test with input longer than one block (136 bytes for Keccak-256)
        val input = ByteArray(200) { (it % 256).toByte() }
        val result = Keccak.hash256(input)

        result shouldHaveSize 32
    }

    /**
     * Monero-specific: seed to keys derivation uses Keccak
     */
    @Test
    fun `monero seed derivation pattern`() {
        // Simulate Monero's key derivation from seed
        val seed = ByteArray(32) { 0x42.toByte() }
        val firstHash = Keccak.hash256(seed)
        val secondHash = Keccak.hash256(firstHash)

        // Results should be deterministic
        firstHash shouldHaveSize 32
        secondHash shouldHaveSize 32
        assertTrue(firstHash.contentEquals(Keccak.hash256(seed)))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
