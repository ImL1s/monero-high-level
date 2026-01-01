package io.monero.wallet.addressbook

import io.monero.wallet.storage.AddressBookEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AddressBookTest {

    private lateinit var addressBook: InMemoryAddressBook

    @BeforeTest
    fun setup() {
        addressBook = InMemoryAddressBook()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Add/Get Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testAddEntry() = runTest {
        val entry = addressBook.add(
            address = "888tNkZrPN6JsEgekjMnABU4TBzc2Dt29EPAvkRxbANsAnjyPbb3iQ1YBRk1UXcdRsiKc9dhwMVgN5S9cQUiyoogDavup3H",
            label = "Alice",
            description = "My friend",
            paymentId = "abc123"
        )

        assertNotNull(entry.id)
        assertEquals("Alice", entry.label)
        assertEquals("My friend", entry.description)
        assertEquals("abc123", entry.paymentId)
    }

    @Test
    fun testGetById() = runTest {
        val added = addressBook.add(
            address = "address1",
            label = "Test"
        )

        val retrieved = addressBook.get(added.id)

        assertNotNull(retrieved)
        assertEquals(added.id, retrieved.id)
        assertEquals("Test", retrieved.label)
    }

    @Test
    fun testGetByIdNotFound() = runTest {
        val result = addressBook.get("nonexistent")
        assertNull(result)
    }

    @Test
    fun testGetByAddress() = runTest {
        val address = "unique_address_123"
        addressBook.add(address = address, label = "Bob")

        val result = addressBook.getByAddress(address)

        assertNotNull(result)
        assertEquals("Bob", result.label)
    }

    @Test
    fun testGetByAddressNotFound() = runTest {
        val result = addressBook.getByAddress("nonexistent_address")
        assertNull(result)
    }

    @Test
    fun testGetAll() = runTest {
        addressBook.add(address = "addr1", label = "Charlie")
        addressBook.add(address = "addr2", label = "Alice")
        addressBook.add(address = "addr3", label = "Bob")

        val all = addressBook.getAll()

        assertEquals(3, all.size)
        // Should be sorted by label
        assertEquals("Alice", all[0].label)
        assertEquals("Bob", all[1].label)
        assertEquals("Charlie", all[2].label)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testUpdate() = runTest {
        val entry = addressBook.add(address = "addr1", label = "OldLabel")

        val updated = addressBook.update(
            id = entry.id,
            label = "NewLabel",
            description = "New description"
        )

        assertNotNull(updated)
        assertEquals("NewLabel", updated.label)
        assertEquals("New description", updated.description)
    }

    @Test
    fun testUpdatePartial() = runTest {
        val entry = addressBook.add(
            address = "addr1",
            label = "Label",
            description = "Original"
        )

        // Only update label, description should remain
        val updated = addressBook.update(id = entry.id, label = "NewLabel")

        assertNotNull(updated)
        assertEquals("NewLabel", updated.label)
        assertEquals("Original", updated.description)
    }

    @Test
    fun testUpdateNotFound() = runTest {
        val result = addressBook.update(id = "nonexistent", label = "Whatever")
        assertNull(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remove Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testRemove() = runTest {
        val entry = addressBook.add(address = "addr1", label = "ToRemove")

        val removed = addressBook.remove(entry.id)

        assertTrue(removed)
        assertNull(addressBook.get(entry.id))
    }

    @Test
    fun testRemoveNotFound() = runTest {
        val removed = addressBook.remove("nonexistent")
        assertFalse(removed)
    }

    @Test
    fun testRemoveByAddress() = runTest {
        val address = "address_to_remove"
        addressBook.add(address = address, label = "ToRemove")

        val removed = addressBook.removeByAddress(address)

        assertTrue(removed)
        assertNull(addressBook.getByAddress(address))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testSearch() = runTest {
        addressBook.add(address = "addr1", label = "Alice Smith")
        addressBook.add(address = "addr2", label = "Bob Johnson")
        addressBook.add(address = "addr3", label = "Charlie", description = "Works at Smith Corp")

        val results = addressBook.search("smith")

        assertEquals(2, results.size)
    }

    @Test
    fun testSearchByAddress() = runTest {
        addressBook.add(address = "unique123abc", label = "Test")

        val results = addressBook.search("123abc")

        assertEquals(1, results.size)
    }

    @Test
    fun testSearchNoResults() = runTest {
        addressBook.add(address = "addr1", label = "Test")

        val results = addressBook.search("xyz")

        assertTrue(results.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testCount() = runTest {
        assertEquals(0, addressBook.count())

        addressBook.add(address = "addr1", label = "One")
        addressBook.add(address = "addr2", label = "Two")

        assertEquals(2, addressBook.count())
    }

    @Test
    fun testContains() = runTest {
        val address = "known_address"
        addressBook.add(address = address, label = "Known")

        assertTrue(addressBook.contains(address))
        assertFalse(addressBook.contains("unknown_address"))
    }

    @Test
    fun testLoadFromEntries() = runTest {
        val entries = listOf(
            AddressBookEntry("entry_1", "addr1", "Alice", null, null, 0),
            AddressBookEntry("entry_2", "addr2", "Bob", null, null, 0)
        )

        addressBook.loadFromEntries(entries)

        assertEquals(2, addressBook.count())
        assertNotNull(addressBook.get("entry_1"))
        assertNotNull(addressBook.get("entry_2"))
    }
}

class TransactionNotesTest {

    private lateinit var notes: InMemoryTransactionNotes

    @BeforeTest
    fun setup() {
        notes = InMemoryTransactionNotes()
    }

    @Test
    fun testSetAndGetNote() = runTest {
        notes.setNote("tx123", "Payment for groceries")

        val note = notes.getNote("tx123")

        assertEquals("Payment for groceries", note)
    }

    @Test
    fun testGetNoteNotFound() = runTest {
        val note = notes.getNote("nonexistent")
        assertNull(note)
    }

    @Test
    fun testUpdateNote() = runTest {
        notes.setNote("tx123", "Old note")
        notes.setNote("tx123", "New note")

        val note = notes.getNote("tx123")
        assertEquals("New note", note)
    }

    @Test
    fun testSetBlankNoteRemoves() = runTest {
        notes.setNote("tx123", "Some note")
        notes.setNote("tx123", "   ")

        assertNull(notes.getNote("tx123"))
    }

    @Test
    fun testRemoveNote() = runTest {
        notes.setNote("tx123", "Note")

        val removed = notes.removeNote("tx123")

        assertTrue(removed)
        assertNull(notes.getNote("tx123"))
    }

    @Test
    fun testRemoveNoteNotFound() = runTest {
        val removed = notes.removeNote("nonexistent")
        assertFalse(removed)
    }

    @Test
    fun testGetAllNotes() = runTest {
        notes.setNote("tx1", "Note 1")
        notes.setNote("tx2", "Note 2")
        notes.setNote("tx3", "Note 3")

        val all = notes.getAllNotes()

        assertEquals(3, all.size)
        assertEquals("Note 1", all["tx1"])
        assertEquals("Note 2", all["tx2"])
        assertEquals("Note 3", all["tx3"])
    }

    @Test
    fun testSearchNotes() = runTest {
        notes.setNote("tx1", "Payment for coffee")
        notes.setNote("tx2", "Rent payment")
        notes.setNote("tx3", "Donation")

        val results = notes.searchNotes("payment")

        assertEquals(2, results.size)
        assertTrue(results.containsKey("tx1"))
        assertTrue(results.containsKey("tx2"))
    }

    @Test
    fun testLoadFromMap() = runTest {
        val map = mapOf(
            "tx1" to "Note 1",
            "tx2" to "Note 2"
        )

        notes.loadFromMap(map)

        assertEquals("Note 1", notes.getNote("tx1"))
        assertEquals("Note 2", notes.getNote("tx2"))
    }
}
