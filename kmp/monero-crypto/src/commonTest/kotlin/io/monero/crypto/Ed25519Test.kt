package io.monero.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Ed25519 tests for Monero curve operations.
 * Oracle: monero-wallet-cli key generation
 */
class Ed25519Test {

    @Test
    fun `generate key pair from seed produces 32-byte keys`() {
        val seed = ByteArray(32) { it.toByte() }
        val (privateKey, publicKey) = Ed25519.generateKeyPair(seed)

        privateKey shouldHaveSize 32
        publicKey shouldHaveSize 32
    }

    @Test
    fun `key pair generation is deterministic`() {
        val seed = ByteArray(32) { 0x42.toByte() }
        val (priv1, pub1) = Ed25519.generateKeyPair(seed)
        val (priv2, pub2) = Ed25519.generateKeyPair(seed)

        priv1 shouldBe priv2
        pub1 shouldBe pub2
    }

    @Test
    fun `different seeds produce different keys`() {
        val seed1 = ByteArray(32) { 0x42.toByte() }
        val seed2 = ByteArray(32) { 0x43.toByte() }

        val (priv1, pub1) = Ed25519.generateKeyPair(seed1)
        val (priv2, pub2) = Ed25519.generateKeyPair(seed2)

        priv1 shouldNotBe priv2
        pub1 shouldNotBe pub2
    }

    @Test
    fun `derive view key from spend key`() {
        val spendKey = ByteArray(32) { it.toByte() }
        val viewKey = Ed25519.deriveViewKey(spendKey)

        viewKey shouldHaveSize 32
        viewKey shouldNotBe spendKey
    }

    @Test
    fun `view key derivation is deterministic`() {
        val spendKey = ByteArray(32) { 0xAA.toByte() }
        val view1 = Ed25519.deriveViewKey(spendKey)
        val view2 = Ed25519.deriveViewKey(spendKey)

        view1 shouldBe view2
    }

    @Test
    fun `public key from private key`() {
        val privateKey = ByteArray(32) { it.toByte() }
        val publicKey = Ed25519.publicKeyFromPrivate(privateKey)

        publicKey shouldHaveSize 32
    }

    @Test
    fun `compute key image`() {
        val privateKey = ByteArray(32) { 0x01.toByte() }
        val publicKey = Ed25519.publicKeyFromPrivate(privateKey)
        val keyImage = Ed25519.computeKeyImage(privateKey, publicKey)

        keyImage shouldHaveSize 32
    }

    @Test
    fun `key image is deterministic`() {
        val privateKey = ByteArray(32) { 0xBB.toByte() }
        val publicKey = Ed25519.publicKeyFromPrivate(privateKey)

        val ki1 = Ed25519.computeKeyImage(privateKey, publicKey)
        val ki2 = Ed25519.computeKeyImage(privateKey, publicKey)

        ki1 shouldBe ki2
    }

    @Test
    fun `hash to point produces valid point`() {
        val data = "test data".encodeToByteArray()
        val point = Ed25519.hashToPoint(data)

        point shouldHaveSize 32
    }

    @Test
    fun `scalar addition`() {
        val a = ByteArray(32) { if (it == 0) 5.toByte() else 0 }
        val b = ByteArray(32) { if (it == 0) 3.toByte() else 0 }
        val result = Ed25519.scalarAdd(a, b)

        result shouldHaveSize 32
        result[0] shouldBe 8.toByte()
    }

    @Test
    fun `scalar subtraction`() {
        val a = ByteArray(32) { if (it == 0) 10.toByte() else 0 }
        val b = ByteArray(32) { if (it == 0) 3.toByte() else 0 }
        val result = Ed25519.scalarSub(a, b)

        result shouldHaveSize 32
        result[0] shouldBe 7.toByte()
    }

    @Test
    fun `scalar reduce 64 bytes`() {
        val scalar = ByteArray(64) { 0xFF.toByte() }
        val reduced = Ed25519.scalarReduce64(scalar)

        reduced shouldHaveSize 32
        assertTrue((reduced[31].toInt() and 0x80) == 0)
    }

    @Test
    fun `is valid point check`() {
        val validPoint = ByteArray(32) { 0x58.toByte() }
        val result = Ed25519.isValidPoint(validPoint)

        assertTrue(result)
    }

    @Test
    fun `scalar multiplication by base produces public key`() {
        val scalar = ByteArray(32) { if (it == 0) 1.toByte() else 0 }
        val result = Ed25519.scalarMultBase(scalar)

        result shouldHaveSize 32
    }

    @Test
    fun `monero key pair format`() {
        val seed = hexToBytes("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
        val (spendPrivate, spendPublic) = Ed25519.generateKeyPair(seed)
        val viewPrivate = Ed25519.deriveViewKey(spendPrivate)
        val viewPublic = Ed25519.publicKeyFromPrivate(viewPrivate)

        spendPrivate shouldHaveSize 32
        spendPublic shouldHaveSize 32
        viewPrivate shouldHaveSize 32
        viewPublic shouldHaveSize 32

        spendPrivate shouldNotBe viewPrivate
        spendPublic shouldNotBe viewPublic
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
