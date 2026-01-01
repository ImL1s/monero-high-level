package io.monero.wallet.outputs

import io.monero.wallet.storage.OutputRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OutputManagerTest {

    private lateinit var manager: InMemoryOutputManager

    @BeforeTest
    fun setup() {
        manager = InMemoryOutputManager()
    }

    // ======== Basic Operations ========

    @Test
    fun `addOutput and getOutputs`() = runTest {
        val output = createOutput("tx1", 0, 1000000L)
        manager.addOutput(output)

        val outputs = manager.getOutputs()
        assertEquals(1, outputs.size)
        assertEquals(output, outputs[0])
    }

    @Test
    fun `addOutput replaces existing output`() = runTest {
        val output1 = createOutput("tx1", 0, 1000000L)
        val output2 = createOutput("tx1", 0, 2000000L) // Same ID, different amount

        manager.addOutput(output1)
        manager.addOutput(output2)

        val outputs = manager.getOutputs()
        assertEquals(1, outputs.size)
        assertEquals(2000000L, outputs[0].amount)
    }

    @Test
    fun `getSpendableOutputs excludes frozen and spent`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L, isSpent = true))
        manager.addOutput(createOutput("tx3", 0, 3000000L, isFrozen = true))

        val spendable = manager.getSpendableOutputs()
        assertEquals(1, spendable.size)
        assertEquals("tx1", spendable[0].txHash)
    }

    // ======== Freeze/Thaw ========

    @Test
    fun `freezeOutput marks output as frozen`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))

        val result = manager.freezeOutput("tx1", 0)
        assertTrue(result)
        assertTrue(manager.isFrozen("tx1", 0))
    }

    @Test
    fun `thawOutput unfreezes output`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, isFrozen = true))

        val result = manager.thawOutput("tx1", 0)
        assertTrue(result)
        assertFalse(manager.isFrozen("tx1", 0))
    }

    @Test
    fun `freezeOutput returns false for non-existent output`() = runTest {
        val result = manager.freezeOutput("nonexistent", 0)
        assertFalse(result)
    }

    @Test
    fun `freezeOutputs batch operation`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L))
        manager.addOutput(createOutput("tx3", 0, 3000000L))

        val ids = listOf(OutputId("tx1", 0), OutputId("tx2", 0))
        val count = manager.freezeOutputs(ids)

        assertEquals(2, count)
        assertTrue(manager.isFrozen("tx1", 0))
        assertTrue(manager.isFrozen("tx2", 0))
        assertFalse(manager.isFrozen("tx3", 0))
    }

    @Test
    fun `thawOutputs batch operation`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, isFrozen = true))
        manager.addOutput(createOutput("tx2", 0, 2000000L, isFrozen = true))

        val ids = listOf(OutputId("tx1", 0), OutputId("tx2", 0))
        val count = manager.thawOutputs(ids)

        assertEquals(2, count)
        assertFalse(manager.isFrozen("tx1", 0))
        assertFalse(manager.isFrozen("tx2", 0))
    }

    // ======== Balance ========

    @Test
    fun `getBalance calculates correctly`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L))
        manager.addOutput(createOutput("tx3", 0, 3000000L, isFrozen = true))
        manager.addOutput(createOutput("tx4", 0, 4000000L, isSpent = true))

        val balance = manager.getBalance()
        assertEquals(3000000L, balance.total) // tx1 + tx2
        assertEquals(3000000L, balance.unlocked)
        assertEquals(3000000L, balance.frozen)
        assertEquals(4000000L, balance.spent)
    }

    // ======== Query ========

    @Test
    fun `queryOutputs with account filter`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, accountIndex = 0))
        manager.addOutput(createOutput("tx2", 0, 2000000L, accountIndex = 1))

        val results = manager.queryOutputs(OutputQuery.forAccount(0))
        assertEquals(1, results.size)
        assertEquals("tx1", results[0].txHash)
    }

    @Test
    fun `queryOutputs with amount range`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000L))
        manager.addOutput(createOutput("tx2", 0, 5000L))
        manager.addOutput(createOutput("tx3", 0, 10000L))

        val results = manager.queryOutputs(OutputQuery(minAmount = 2000L, maxAmount = 8000L))
        assertEquals(1, results.size)
        assertEquals("tx2", results[0].txHash)
    }

    @Test
    fun `queryOutputs spendable`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L, isSpent = true))
        manager.addOutput(createOutput("tx3", 0, 3000000L, isFrozen = true))

        val results = manager.queryOutputs(OutputQuery.spendable())
        assertEquals(1, results.size)
        assertEquals("tx1", results[0].txHash)
    }

    @Test
    fun `queryOutputs frozen`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, isFrozen = true))
        manager.addOutput(createOutput("tx2", 0, 2000000L))

        val results = manager.queryOutputs(OutputQuery.frozen())
        assertEquals(1, results.size)
        assertEquals("tx1", results[0].txHash)
    }

    @Test
    fun `queryOutputs hasKeyImage`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, keyImage = "ki1"))
        manager.addOutput(createOutput("tx2", 0, 2000000L))

        val withKI = manager.queryOutputs(OutputQuery(hasKeyImage = true))
        assertEquals(1, withKI.size)
        assertEquals("tx1", withKI[0].txHash)

        val withoutKI = manager.queryOutputs(OutputQuery(hasKeyImage = false))
        assertEquals(1, withoutKI.size)
        assertEquals("tx2", withoutKI[0].txHash)
    }

    // ======== Export/Import Outputs ========

    @Test
    fun `exportOutputs returns all unspent by default`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L, isSpent = true))

        val exported = manager.exportOutputs()
        assertEquals(1, exported.outputs.size)
        assertEquals("tx1", exported.outputs[0].txHash)
    }

    @Test
    fun `exportOutputs with all flag includes spent`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L, isSpent = true))

        val exported = manager.exportOutputs(all = true)
        assertEquals(2, exported.outputs.size)
    }

    @Test
    fun `importOutputs adds new outputs`() = runTest {
        val exported = ExportedOutputs(
            outputs = listOf(
                ExportedOutput("tx1", 0, 1000000L, 100L, 0, 0, "pubkey", 0L),
                ExportedOutput("tx2", 0, 2000000L, 200L, 0, 1, "pubkey2", 0L)
            )
        )

        val imported = manager.importOutputs(exported)
        assertEquals(2, imported)
        assertEquals(2, manager.getOutputs().size)
    }

    @Test
    fun `importOutputs skips existing outputs`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))

        val exported = ExportedOutputs(
            outputs = listOf(
                ExportedOutput("tx1", 0, 1000000L, 100L, 0, 0, "pubkey", 0L),
                ExportedOutput("tx2", 0, 2000000L, 200L, 0, 0, "pubkey2", 0L)
            )
        )

        val imported = manager.importOutputs(exported)
        assertEquals(1, imported) // Only tx2 imported
        assertEquals(2, manager.getOutputs().size)
    }

    // ======== Export/Import Key Images ========

    @Test
    fun `exportKeyImages returns outputs with key images`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, keyImage = "ki1"))
        manager.addOutput(createOutput("tx2", 0, 2000000L)) // No key image

        val exported = manager.exportKeyImages()
        assertEquals(1, exported.keyImages.size)
        assertEquals("ki1", exported.keyImages[0].keyImage)
    }

    @Test
    fun `exportKeyImages excludes spent by default`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L, keyImage = "ki1"))
        manager.addOutput(createOutput("tx2", 0, 2000000L, keyImage = "ki2", isSpent = true))

        val exported = manager.exportKeyImages()
        assertEquals(1, exported.keyImages.size)
        assertEquals("tx1", exported.keyImages[0].txHash)
    }

    @Test
    fun `importKeyImages updates key images`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L))

        val keyImages = ExportedKeyImages(
            keyImages = listOf(
                KeyImageEntry("tx1", 0, "newki1", null),
                KeyImageEntry("tx2", 0, "newki2", null)
            )
        )

        val result = manager.importKeyImages(keyImages)
        assertEquals(2, result.imported)

        val outputs = manager.getOutputs()
        assertEquals("newki1", outputs.find { it.txHash == "tx1" }?.keyImage)
        assertEquals("newki2", outputs.find { it.txHash == "tx2" }?.keyImage)
    }

    @Test
    fun `importKeyImages returns spent and unspent amounts`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))
        manager.addOutput(createOutput("tx2", 0, 2000000L, isSpent = true))

        val keyImages = ExportedKeyImages(
            keyImages = listOf(
                KeyImageEntry("tx1", 0, "ki1", null),
                KeyImageEntry("tx2", 0, "ki2", null)
            )
        )

        val result = manager.importKeyImages(keyImages)
        assertEquals(1000000L, result.unspent)
        assertEquals(2000000L, result.spent)
    }

    // ======== Mark Spent and Set Key Image ========

    @Test
    fun `markSpent updates output`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))

        manager.markSpent("tx1", 0, 100L)

        val output = manager.getOutputs()[0]
        assertTrue(output.isSpent)
        assertEquals(100L, output.spentHeight)
    }

    @Test
    fun `setKeyImage updates output`() = runTest {
        manager.addOutput(createOutput("tx1", 0, 1000000L))

        manager.setKeyImage("tx1", 0, "newkeyimage")

        val output = manager.getOutputs()[0]
        assertEquals("newkeyimage", output.keyImage)
    }

    // ======== Serialization ========

    @Test
    fun `ExportedOutputs serialization roundtrip`() {
        val original = ExportedOutputs(
            outputs = listOf(
                ExportedOutput("tx1", 0, 1000000L, 100L, 0, 0, "pubkey", 0L)
            )
        )

        val json = OutputExportFormat.serializeOutputs(original)
        val deserialized = OutputExportFormat.deserializeOutputs(json)

        assertEquals(original, deserialized)
    }

    @Test
    fun `ExportedKeyImages serialization roundtrip`() {
        val original = ExportedKeyImages(
            keyImages = listOf(
                KeyImageEntry("tx1", 0, "ki1", "sig1")
            )
        )

        val json = OutputExportFormat.serializeKeyImages(original)
        val deserialized = OutputExportFormat.deserializeKeyImages(json)

        assertEquals(original, deserialized)
    }

    // ======== Load/Store Records ========

    @Test
    fun `loadFromRecords and toRecords`() = runTest {
        val records = listOf(
            createOutput("tx1", 0, 1000000L),
            createOutput("tx2", 0, 2000000L)
        )

        manager.loadFromRecords(records)
        assertEquals(2, manager.getOutputs().size)

        val exported = manager.toRecords()
        assertEquals(records, exported)
    }

    // ======== Helper ========

    private fun createOutput(
        txHash: String,
        outputIndex: Int,
        amount: Long,
        keyImage: String? = null,
        accountIndex: Int = 0,
        subaddressIndex: Int = 0,
        isSpent: Boolean = false,
        isFrozen: Boolean = false,
        unlockTime: Long = 0L
    ) = OutputRecord(
        txHash = txHash,
        outputIndex = outputIndex,
        amount = amount,
        keyImage = keyImage,
        globalIndex = null,
        accountIndex = accountIndex,
        subaddressIndex = subaddressIndex,
        isSpent = isSpent,
        isFrozen = isFrozen,
        spentHeight = null,
        unlockTime = unlockTime
    )
}
