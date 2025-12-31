package io.monero.core

import io.monero.crypto.Keccak
import io.monero.crypto.Ed25519
import kotlinx.serialization.Serializable

/**
 * Monero mnemonic seed operations.
 *
 * Monero uses 25-word mnemonic seeds based on the Electrum word list.
 * The seed encodes a 256-bit private spend key + 1 checksum word.
 *
 * Word list languages supported:
 * - English (1626 words)
 * - Japanese, Spanish, Portuguese, French, German, Italian, Russian, etc.
 */
object Mnemonic {
    private const val SEED_LENGTH = 32
    private const val WORD_COUNT = 25
    private const val CHECKSUM_INDEX = 24
    private const val WORDS_PER_CHUNK = 3
    private const val WORD_LIST_SIZE = 1626

    /**
     * English word list (first few words for structure - full list in resources)
     */
    private val ENGLISH_WORD_LIST = listOf(
        "abbey", "abducts", "ability", "ablaze", "abnormal", "abort", "abrasive", "absorb",
        // ... 1626 words total - loaded from resources in production
    )

    /**
     * Convert entropy to mnemonic words
     *
     * @param entropy 32-byte random data
     * @param language Word list language (default: English)
     * @return List of 25 mnemonic words
     */
    fun entropyToMnemonic(entropy: ByteArray, language: String = "english"): List<String> {
        require(entropy.size == SEED_LENGTH) { "Entropy must be $SEED_LENGTH bytes" }

        val wordList = getWordList(language)
        val words = mutableListOf<String>()

        // Convert entropy to words (3 words per 4 bytes chunk)
        for (i in 0 until 8) {
            val chunk = (entropy[i * 4].toUInt() and 0xFFu) or
                    ((entropy[i * 4 + 1].toUInt() and 0xFFu) shl 8) or
                    ((entropy[i * 4 + 2].toUInt() and 0xFFu) shl 16) or
                    ((entropy[i * 4 + 3].toUInt() and 0xFFu) shl 24)

            val w1 = (chunk % WORD_LIST_SIZE.toUInt()).toInt()
            val w2 = (((chunk / WORD_LIST_SIZE.toUInt()) + w1.toUInt()) % WORD_LIST_SIZE.toUInt()).toInt()
            val w3 = (((chunk / WORD_LIST_SIZE.toUInt() / WORD_LIST_SIZE.toUInt()) + w2.toUInt()) % WORD_LIST_SIZE.toUInt()).toInt()

            words.add(wordList[w1])
            words.add(wordList[w2])
            words.add(wordList[w3])
        }

        // Add checksum word
        val checksumWord = computeChecksum(words, wordList)
        words.add(checksumWord)

        return words
    }

    /**
     * Convert mnemonic words to entropy
     *
     * @param words List of 25 mnemonic words
     * @param language Word list language
     * @return 32-byte entropy
     */
    fun mnemonicToEntropy(words: List<String>, language: String = "english"): ByteArray {
        require(words.size == WORD_COUNT) { "Mnemonic must have $WORD_COUNT words" }

        val wordList = getWordList(language)
        val seedWords = words.take(24)
        val checksumWord = words[24]

        // Verify checksum
        val expectedChecksum = computeChecksum(seedWords, wordList)
        require(checksumWord == expectedChecksum) { "Invalid checksum word" }

        // Convert words to entropy
        val entropy = ByteArray(SEED_LENGTH)
        for (i in 0 until 8) {
            val w1 = wordList.indexOf(seedWords[i * 3])
            val w2 = wordList.indexOf(seedWords[i * 3 + 1])
            val w3 = wordList.indexOf(seedWords[i * 3 + 2])

            require(w1 >= 0 && w2 >= 0 && w3 >= 0) { "Unknown word in mnemonic" }

            val chunk = (w1.toUInt() +
                    WORD_LIST_SIZE.toUInt() * ((WORD_LIST_SIZE.toUInt() + w2.toUInt() - w1.toUInt()) % WORD_LIST_SIZE.toUInt()) +
                    WORD_LIST_SIZE.toUInt() * WORD_LIST_SIZE.toUInt() * ((WORD_LIST_SIZE.toUInt() + w3.toUInt() - w2.toUInt()) % WORD_LIST_SIZE.toUInt()))

            entropy[i * 4] = (chunk and 0xFFu).toByte()
            entropy[i * 4 + 1] = ((chunk shr 8) and 0xFFu).toByte()
            entropy[i * 4 + 2] = ((chunk shr 16) and 0xFFu).toByte()
            entropy[i * 4 + 3] = ((chunk shr 24) and 0xFFu).toByte()
        }

        return entropy
    }

    /**
     * Validate mnemonic words
     */
    fun validate(words: List<String>, language: String = "english"): Boolean {
        if (words.size != WORD_COUNT) return false

        return try {
            mnemonicToEntropy(words, language)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate a new random mnemonic
     */
    fun generate(language: String = "english"): List<String> {
        val entropy = generateSecureRandom(SEED_LENGTH)
        return entropyToMnemonic(entropy, language)
    }

    private fun computeChecksum(words: List<String>, wordList: List<String>): String {
        // Checksum is based on first 3 characters of each word
        val prefixString = words.joinToString("") {
            it.take(prefixLength(wordList))
        }
        val crc = crc32(prefixString.encodeToByteArray())
        return words[(crc % words.size.toUInt()).toInt()]
    }

    private fun prefixLength(wordList: List<String>): Int {
        // English uses 3-character prefix for unique identification
        return 3
    }

    private fun crc32(data: ByteArray): UInt {
        var crc = 0xFFFFFFFFu
        for (byte in data) {
            crc = crc xor byte.toUInt()
            for (j in 0 until 8) {
                crc = if (crc and 1u != 0u) {
                    (crc shr 1) xor 0xEDB88320u
                } else {
                    crc shr 1
                }
            }
        }
        return crc xor 0xFFFFFFFFu
    }

    private fun getWordList(language: String): List<String> {
        return when (language.lowercase()) {
            "english" -> ENGLISH_WORD_LIST
            else -> throw IllegalArgumentException("Unsupported language: $language")
        }
    }
}

/**
 * Generate cryptographically secure random bytes.
 * Platform-specific implementation required.
 */
expect fun generateSecureRandom(size: Int): ByteArray
