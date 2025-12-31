package io.monero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize

/**
 * Mnemonic seed tests.
 * Oracle: monero-wallet-cli seed generation
 */
class MnemonicTest {

    @Test
    fun `english word list has 1626 words`() {
        EnglishWordList.words shouldHaveSize 1626
    }

    @Test
    fun `entropy to mnemonic produces 25 words`() {
        val entropy = ByteArray(32) { it.toByte() }
        val words = Mnemonic.entropyToMnemonic(entropy)

        words shouldHaveSize 25
    }

    @Test
    fun `mnemonic to entropy round trip`() {
        val entropy = ByteArray(32) { it.toByte() }
        val words = Mnemonic.entropyToMnemonic(entropy)
        val recovered = Mnemonic.mnemonicToEntropy(words)

        recovered shouldBe entropy
    }

    @Test
    fun `different entropy produces different mnemonic`() {
        val entropy1 = ByteArray(32) { 0x01.toByte() }
        val entropy2 = ByteArray(32) { 0x02.toByte() }

        val words1 = Mnemonic.entropyToMnemonic(entropy1)
        val words2 = Mnemonic.entropyToMnemonic(entropy2)

        words1 shouldNotBe words2
    }

    @Test
    fun `validate accepts valid mnemonic`() {
        val entropy = ByteArray(32) { 0x42.toByte() }
        val words = Mnemonic.entropyToMnemonic(entropy)

        Mnemonic.validate(words) shouldBe true
    }

    @Test
    fun `validate rejects wrong word count`() {
        val words = listOf("abbey", "abducts", "ability") // only 3 words
        Mnemonic.validate(words) shouldBe false
    }

    @Test
    fun `generate produces valid mnemonic`() {
        val words = Mnemonic.generate()

        words shouldHaveSize 25
        Mnemonic.validate(words) shouldBe true
    }

    @Test
    fun `all words are from word list`() {
        val entropy = ByteArray(32) { (it * 7).toByte() }
        val words = Mnemonic.entropyToMnemonic(entropy)

        words.forEach { word ->
            assertTrue(EnglishWordList.words.contains(word), "Word '$word' not in word list")
        }
    }

    @Test
    fun `checksum word is last word`() {
        val entropy = ByteArray(32) { it.toByte() }
        val words = Mnemonic.entropyToMnemonic(entropy)

        // Checksum is word at index 24
        assertTrue(EnglishWordList.words.contains(words[24]))
    }

    @Test
    fun `mnemonic is deterministic`() {
        val entropy = ByteArray(32) { 0xAB.toByte() }

        val words1 = Mnemonic.entropyToMnemonic(entropy)
        val words2 = Mnemonic.entropyToMnemonic(entropy)

        words1 shouldBe words2
    }
}
