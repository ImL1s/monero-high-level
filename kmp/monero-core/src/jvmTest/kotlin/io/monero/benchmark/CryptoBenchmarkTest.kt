package io.monero.benchmark

import io.monero.crypto.*
import io.monero.core.*
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.system.measureTimeMillis
import kotlin.system.measureNanoTime

/**
 * Performance benchmarks for Monero KMP library.
 * 
 * T4: Profiling and optimization baseline.
 * 
 * Run with: ./gradlew :monero-crypto:jvmTest --tests "*BenchmarkTest*"
 */
class CryptoBenchmarkTest {

    companion object {
        const val ITERATIONS = 1000
        const val WARMUP = 100
    }

    // ======== Keccak Benchmarks ========

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark Keccak-256 32 bytes`() {
        val input = ByteArray(32) { it.toByte() }
        
        // Warmup
        repeat(WARMUP) { Keccak.keccak(input, 32) }
        
        // Benchmark
        val elapsed = measureTimeMillis {
            repeat(ITERATIONS) { Keccak.keccak(input, 32) }
        }
        
        val opsPerSec = ITERATIONS * 1000.0 / elapsed
        println("=== Keccak-256 (32 bytes) ===")
        println("Iterations: $ITERATIONS")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / ITERATIONS}μs")
        println("Throughput: ${opsPerSec.toInt()} ops/sec")
    }

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark Keccak-256 1KB`() {
        val input = ByteArray(1024) { it.toByte() }
        
        repeat(WARMUP) { Keccak.keccak(input, 32) }
        
        val elapsed = measureTimeMillis {
            repeat(ITERATIONS) { Keccak.keccak(input, 32) }
        }
        
        val throughputMBs = ITERATIONS * 1024.0 / elapsed / 1000
        println("=== Keccak-256 (1KB) ===")
        println("Iterations: $ITERATIONS")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / ITERATIONS}μs")
        println("Throughput: ${throughputMBs.format(2)} MB/s")
    }

    // ======== Ed25519 Benchmarks ========

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark Ed25519 scalar mult base`() {
        val scalar = ByteArray(32) { it.toByte() }
        
        repeat(WARMUP / 10) { Ed25519.scalarMultBase(scalar) }
        
        val iterations = ITERATIONS / 10
        val elapsed = measureTimeMillis {
            repeat(iterations) { Ed25519.scalarMultBase(scalar) }
        }
        
        println("=== Ed25519 Scalar Mult Base ===")
        println("Iterations: $iterations")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / iterations}μs")
        println("Throughput: ${iterations * 1000 / elapsed} ops/sec")
    }

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark Ed25519 point add`() {
        val p1 = Ed25519.scalarMultBase(ByteArray(32) { 1 })
        val p2 = Ed25519.scalarMultBase(ByteArray(32) { 2 })
        
        repeat(WARMUP) { Ed25519.pointAdd(p1, p2) }
        
        val elapsed = measureTimeMillis {
            repeat(ITERATIONS) { Ed25519.pointAdd(p1, p2) }
        }
        
        println("=== Ed25519 Point Add ===")
        println("Iterations: $ITERATIONS")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / ITERATIONS}μs")
        println("Throughput: ${ITERATIONS * 1000 / elapsed} ops/sec")
    }

    // ======== Base58 Benchmarks ========

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark Base58 encode address`() {
        val data = ByteArray(65) { it.toByte() } // Standard address size
        
        repeat(WARMUP) { Base58.encode(data) }
        
        val elapsed = measureTimeMillis {
            repeat(ITERATIONS) { Base58.encode(data) }
        }
        
        println("=== Base58 Encode (65 bytes) ===")
        println("Iterations: $ITERATIONS")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / ITERATIONS}μs")
        println("Throughput: ${ITERATIONS * 1000 / elapsed} ops/sec")
    }

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark Base58 decode address`() {
        val data = ByteArray(65) { it.toByte() }
        val encoded = Base58.encode(data)
        
        repeat(WARMUP) { Base58.decode(encoded) }
        
        val elapsed = measureTimeMillis {
            repeat(ITERATIONS) { Base58.decode(encoded) }
        }
        
        println("=== Base58 Decode (65 bytes) ===")
        println("Iterations: $ITERATIONS")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / ITERATIONS}μs")
        println("Throughput: ${ITERATIONS * 1000 / elapsed} ops/sec")
    }

    // ======== Key Derivation Benchmarks ========

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark full key derivation`() {
        val seed = ByteArray(32) { it.toByte() }
        
        repeat(WARMUP / 10) { KeyDerivation.deriveWalletKeys(seed) }
        
        val iterations = ITERATIONS / 10
        val elapsed = measureTimeMillis {
            repeat(iterations) { KeyDerivation.deriveWalletKeys(seed) }
        }
        
        println("=== Full Key Derivation ===")
        println("Iterations: $iterations")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / iterations}μs")
        println("Throughput: ${iterations * 1000 / elapsed} ops/sec")
    }

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark subaddress derivation`() {
        val seed = ByteArray(32) { it.toByte() }
        val keys = KeyDerivation.deriveWalletKeys(seed)
        
        repeat(WARMUP / 10) { KeyDerivation.deriveSubaddress(keys, 0, 1) }
        
        val iterations = ITERATIONS / 10
        val elapsed = measureTimeMillis {
            repeat(iterations) { index ->
                KeyDerivation.deriveSubaddress(keys, 0, index)
            }
        }
        
        println("=== Subaddress Derivation ===")
        println("Iterations: $iterations")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / iterations}μs")
        println("Throughput: ${iterations * 1000 / elapsed} subaddresses/sec")
    }

    // ======== Memory Usage ========

    @Test
    @Ignore // Remove to run benchmark
    fun `memory usage key derivation`() {
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(100)
        
        val beforeMem = runtime.totalMemory() - runtime.freeMemory()
        
        val keys = mutableListOf<KeyDerivation.WalletKeys>()
        repeat(100) { i ->
            val seed = ByteArray(32) { (it + i).toByte() }
            keys.add(KeyDerivation.deriveWalletKeys(seed))
        }
        
        System.gc()
        Thread.sleep(100)
        val afterMem = runtime.totalMemory() - runtime.freeMemory()
        
        val memPerKey = (afterMem - beforeMem) / 100.0 / 1024
        
        println("=== Memory Usage ===")
        println("Keys created: 100")
        println("Memory before: ${beforeMem / 1024} KB")
        println("Memory after: ${afterMem / 1024} KB")
        println("Memory per key: ${memPerKey.format(2)} KB")
    }

    // ======== ChaCha20-Poly1305 Benchmarks ========

    @Test
    @Ignore // Remove to run benchmark
    fun `benchmark ChaCha20Poly1305 encrypt 1KB`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = ByteArray(1024) { it.toByte() }
        val aad = ByteArray(16)
        
        repeat(WARMUP) { ChaCha20Poly1305.encrypt(key, nonce, plaintext, aad) }
        
        val elapsed = measureTimeMillis {
            repeat(ITERATIONS) { ChaCha20Poly1305.encrypt(key, nonce, plaintext, aad) }
        }
        
        val throughputMBs = ITERATIONS * 1024.0 / elapsed / 1000
        println("=== ChaCha20-Poly1305 Encrypt (1KB) ===")
        println("Iterations: $ITERATIONS")
        println("Total time: ${elapsed}ms")
        println("Per operation: ${elapsed * 1000.0 / ITERATIONS}μs")
        println("Throughput: ${throughputMBs.format(2)} MB/s")
    }

    // ======== Comprehensive Summary ========

    @Test
    @Ignore // Remove to run all benchmarks
    fun `run all benchmarks`() {
        println("=" .repeat(60))
        println("          MONERO KMP PERFORMANCE BENCHMARKS")
        println("=" .repeat(60))
        println()
        
        `benchmark Keccak-256 32 bytes`()
        println()
        `benchmark Keccak-256 1KB`()
        println()
        `benchmark Ed25519 scalar mult base`()
        println()
        `benchmark Base58 encode address`()
        println()
        `benchmark full key derivation`()
        println()
        `benchmark subaddress derivation`()
        println()
        `benchmark ChaCha20Poly1305 encrypt 1KB`()
        println()
        `memory usage key derivation`()
        
        println()
        println("=" .repeat(60))
        println("                    BENCHMARK COMPLETE")
        println("=" .repeat(60))
    }

    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}
