package io.monero.crypto

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BulletproofPlusTest {

    @Test
    fun testProveAndVerifyValidAmounts() {
        val amounts = listOf(0L, 1L, 123_456_789L, 1_000_000_000_000L, Long.MAX_VALUE)

        for (amount in amounts) {
            val mask = PedersenCommitment.generateRandomScalar()
            val proof = BulletproofPlus.prove(mask = mask, amount = amount)

            assertTrue(BulletproofPlus.verify(proof))
            assertTrue(BulletproofPlus.verify(proof.commitment, proof))
        }
    }

    @Test
    fun testVerifyRejectsWrongCommitment() {
        val amount = 42L
        val mask = PedersenCommitment.generateRandomScalar()
        val proof = BulletproofPlus.prove(mask = mask, amount = amount)

        val wrongCommitment = proof.commitment.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        assertFalse(BulletproofPlus.verify(wrongCommitment, proof))
    }

    @Test
    fun testVerifyRejectsTamperedAmount() {
        val amount = 4242L
        val mask = PedersenCommitment.generateRandomScalar()
        val proof = BulletproofPlus.prove(mask = mask, amount = amount)

        val tampered = proof.copy(openingAmount = amount + 1)

        assertFalse(BulletproofPlus.verify(tampered))
    }

    @Test
    fun testProveRejectsNegativeAmount() {
        val mask = PedersenCommitment.generateRandomScalar()
        assertFailsWith<IllegalArgumentException> {
            BulletproofPlus.prove(mask = mask, amount = -1L)
        }
    }

    @Test
    fun testProveRejectsOutOfRangeForSmallBits() {
        val mask = PedersenCommitment.generateRandomScalar()

        // 8-bit range allows 0..255
        assertFailsWith<IllegalArgumentException> {
            BulletproofPlus.prove(mask = mask, amount = 256L, rangeBits = 8)
        }
    }
}
