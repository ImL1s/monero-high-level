package io.monero.crypto

import kotlin.test.*

class ChaCha20Poly1305Test {

    // ─────────────────────────────────────────────────────────────────────────
    // ChaCha20 Tests (RFC 8439 test vectors)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testChaCha20Block() {
        // RFC 8439 Section 2.3.2 test vector
        val key = hexToBytes(
            "000102030405060708090a0b0c0d0e0f" +
            "101112131415161718191a1b1c1d1e1f"
        )
        val nonce = hexToBytes("000000090000004a00000000")
        val counter = 1u

        val block = ChaCha20.generateBlock(key, nonce, counter)

        // RFC specifies output as 32-bit little-endian words
        // First word is 0xe4e7f110 which serializes to bytes: 10 f1 e7 e4
        assertEquals("10f1e7e4", block.copyOfRange(0, 4).toHex())
        assertEquals("d13b5915", block.copyOfRange(4, 8).toHex())
        assertEquals("500fdd1f", block.copyOfRange(8, 12).toHex())
        assertEquals("a32071c4", block.copyOfRange(12, 16).toHex())
    }

    @Test
    fun testChaCha20Cipher() {
        // RFC 8439 Section 2.4.2 test vector
        val key = hexToBytes(
            "000102030405060708090a0b0c0d0e0f" +
            "101112131415161718191a1b1c1d1e1f"
        )
        val nonce = hexToBytes("000000000000004a00000000")
        val counter = 1u

        val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it."
            .encodeToByteArray()

        val ciphertext = ChaCha20.cipher(key, nonce, counter, plaintext)

        val expectedStart = "6e2e359a2568f98041ba0728dd0d6981"
        assertEquals(expectedStart, ciphertext.copyOfRange(0, 16).toHex())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Poly1305 Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testPoly1305Mac() {
        // RFC 8439 Section 2.5.2 test vector
        val key = hexToBytes(
            "85d6be7857556d337f4452fe42d506a8" +
            "0103808afb0db2fd4abff6af4149f51b"
        )
        val message = "Cryptographic Forum Research Group".encodeToByteArray()

        val tag = Poly1305.mac(key, message)

        assertEquals("a8061dc1305136c6c22b8baf0c0127a9", tag.toHex())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ChaCha20-Poly1305 AEAD Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testAeadEncryptDecrypt() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = "Hello, Monero!".encodeToByteArray()
        val aad = "Associated Data".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext, aad)
        val decrypted = ChaCha20Poly1305.decrypt(key, nonce, encrypted, aad)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testAeadWithoutAad() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val nonce = ByteArray(12) { (it * 5).toByte() }
        val plaintext = "Secret message without AAD".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext)
        val decrypted = ChaCha20Poly1305.decrypt(key, nonce, encrypted)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testAeadEmptyPlaintext() {
        val key = ByteArray(32) { 0x42.toByte() }
        val nonce = ByteArray(12) { 0x24.toByte() }
        val plaintext = ByteArray(0)

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext)
        assertEquals(16, encrypted.size)  // Only tag

        val decrypted = ChaCha20Poly1305.decrypt(key, nonce, encrypted)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testAeadTamperedCiphertext() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = "Do not tamper!".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext)

        // Tamper with ciphertext
        encrypted[0] = (encrypted[0].toInt() xor 0xFF).toByte()

        assertFailsWith<ChaCha20Poly1305.AuthenticationException> {
            ChaCha20Poly1305.decrypt(key, nonce, encrypted)
        }
    }

    @Test
    fun testAeadTamperedTag() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = "Do not tamper!".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext)

        // Tamper with tag (last 16 bytes)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        assertFailsWith<ChaCha20Poly1305.AuthenticationException> {
            ChaCha20Poly1305.decrypt(key, nonce, encrypted)
        }
    }

    @Test
    fun testAeadWrongKey() {
        val key = ByteArray(32) { it.toByte() }
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = "Secret".encodeToByteArray()

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext)

        assertFailsWith<ChaCha20Poly1305.AuthenticationException> {
            ChaCha20Poly1305.decrypt(wrongKey, nonce, encrypted)
        }
    }

    @Test
    fun testAeadLargeData() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = ByteArray(10000) { (it % 256).toByte() }

        val encrypted = ChaCha20Poly1305.encrypt(key, nonce, plaintext)
        val decrypted = ChaCha20Poly1305.decrypt(key, nonce, encrypted)

        assertContentEquals(plaintext, decrypted)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WalletEncryption Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testWalletEncryptDecrypt() {
        val password = "MySecretPassword123!"
        val walletData = """{"version":1,"network":"STAGENET"}""".encodeToByteArray()

        val encrypted = WalletEncryption.encrypt(password, walletData)
        val decrypted = WalletEncryption.decrypt(password, encrypted)

        assertContentEquals(walletData, decrypted)
    }

    @Test
    fun testWalletEncryptionDifferentOutput() {
        val password = "password"
        val data = "same data".encodeToByteArray()

        val encrypted1 = WalletEncryption.encrypt(password, data)
        val encrypted2 = WalletEncryption.encrypt(password, data)

        // Should produce different outputs due to random salt/nonce
        assertFalse(encrypted1.contentEquals(encrypted2))

        // But both should decrypt to same data
        assertContentEquals(data, WalletEncryption.decrypt(password, encrypted1))
        assertContentEquals(data, WalletEncryption.decrypt(password, encrypted2))
    }

    @Test
    fun testWalletEncryptionWrongPassword() {
        val password = "correct"
        val wrongPassword = "incorrect"
        val data = "sensitive data".encodeToByteArray()

        val encrypted = WalletEncryption.encrypt(password, data)

        assertFailsWith<ChaCha20Poly1305.AuthenticationException> {
            WalletEncryption.decrypt(wrongPassword, encrypted)
        }
    }

    @Test
    fun testWalletEncryptionEmptyPassword() {
        val password = ""
        val data = "data".encodeToByteArray()

        val encrypted = WalletEncryption.encrypt(password, data)
        val decrypted = WalletEncryption.decrypt(password, encrypted)

        assertContentEquals(data, decrypted)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
