package io.monero.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WalletStorageModelsTest {
    @Test
    fun encryptedKeysEquality_ignoresEncryptedFields() {
        val a = EncryptedKeys(
            encryptedSpendKey = byteArrayOf(1, 2, 3),
            encryptedViewKey = byteArrayOf(4, 5, 6),
            publicSpendKey = byteArrayOf(9),
            publicViewKey = byteArrayOf(8),
            salt = byteArrayOf(7),
            nonce = byteArrayOf(6)
        )

        val b = EncryptedKeys(
            encryptedSpendKey = byteArrayOf(99),
            encryptedViewKey = byteArrayOf(88),
            publicSpendKey = byteArrayOf(9),
            publicViewKey = byteArrayOf(8),
            salt = byteArrayOf(1),
            nonce = byteArrayOf(2)
        )

        // equality is defined only by public keys
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun storedOutputEquality_isKeyImageBased() {
        val out1 = StoredOutput(
            keyImage = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(9),
            amount = 1,
            globalIndex = 2,
            txHash = byteArrayOf(4),
            localIndex = 0,
            height = 1,
            accountIndex = 0,
            subaddressIndex = 0,
            spent = false,
            spendingTxHash = null,
            frozen = false,
            unlockTime = 0
        )

        val out2 = out1.copy(amount = 999)
        val out3 = out1.copy(keyImage = byteArrayOf(9, 9, 9))

        assertEquals(out1, out2)
        assertNotEquals(out1, out3)
    }

    @Test
    fun storedTransactionEquality_isHashBased() {
        val tx1 = StoredTransaction(
            hash = byteArrayOf(1),
            height = 1,
            timestamp = 0,
            fee = 0,
            incoming = true,
            accountIndex = 0,
            subaddressIndices = listOf(0),
            amount = 1,
            paymentId = null,
            note = null
        )

        val tx2 = tx1.copy(amount = 999, fee = 123)
        val tx3 = tx1.copy(hash = byteArrayOf(2))

        assertEquals(tx1, tx2)
        assertNotEquals(tx1, tx3)
        assertEquals(true, tx1.isConfirmed)
    }
}
