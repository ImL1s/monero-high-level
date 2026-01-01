package io.monero.wallet.addressbook

import io.monero.wallet.storage.AddressBookEntry
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Address book manager for storing and retrieving contact addresses.
 */
interface AddressBook {
    /**
     * Add a new address book entry.
     * @return The created entry with generated ID
     */
    suspend fun add(
        address: String,
        label: String,
        description: String? = null,
        paymentId: String? = null
    ): AddressBookEntry

    /**
     * Get an entry by ID.
     */
    suspend fun get(id: String): AddressBookEntry?

    /**
     * Get an entry by address.
     */
    suspend fun getByAddress(address: String): AddressBookEntry?

    /**
     * Get all entries.
     */
    suspend fun getAll(): List<AddressBookEntry>

    /**
     * Update an existing entry.
     */
    suspend fun update(
        id: String,
        label: String? = null,
        description: String? = null,
        paymentId: String? = null
    ): AddressBookEntry?

    /**
     * Remove an entry by ID.
     * @return true if entry was removed
     */
    suspend fun remove(id: String): Boolean

    /**
     * Remove an entry by address.
     * @return true if entry was removed
     */
    suspend fun removeByAddress(address: String): Boolean

    /**
     * Search entries by label or description.
     */
    suspend fun search(query: String): List<AddressBookEntry>

    /**
     * Get number of entries.
     */
    suspend fun count(): Int

    /**
     * Check if an address exists in the address book.
     */
    suspend fun contains(address: String): Boolean
}

/**
 * In-memory address book implementation.
 */
class InMemoryAddressBook : AddressBook {

    private val entries = mutableMapOf<String, AddressBookEntry>()
    private var nextId = 1

    override suspend fun add(
        address: String,
        label: String,
        description: String?,
        paymentId: String?
    ): AddressBookEntry {
        val id = generateId()
        val entry = AddressBookEntry(
            id = id,
            address = address,
            label = label,
            description = description,
            paymentId = paymentId,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        entries[id] = entry
        return entry
    }

    override suspend fun get(id: String): AddressBookEntry? = entries[id]

    override suspend fun getByAddress(address: String): AddressBookEntry? {
        return entries.values.find { it.address == address }
    }

    override suspend fun getAll(): List<AddressBookEntry> {
        return entries.values.sortedBy { it.label.lowercase() }
    }

    override suspend fun update(
        id: String,
        label: String?,
        description: String?,
        paymentId: String?
    ): AddressBookEntry? {
        val existing = entries[id] ?: return null
        val updated = existing.copy(
            label = label ?: existing.label,
            description = description ?: existing.description,
            paymentId = paymentId ?: existing.paymentId
        )
        entries[id] = updated
        return updated
    }

    override suspend fun remove(id: String): Boolean {
        return entries.remove(id) != null
    }

    override suspend fun removeByAddress(address: String): Boolean {
        val entry = entries.values.find { it.address == address }
        return if (entry != null) {
            entries.remove(entry.id) != null
        } else {
            false
        }
    }

    override suspend fun search(query: String): List<AddressBookEntry> {
        val queryLower = query.lowercase()
        return entries.values.filter {
            it.label.lowercase().contains(queryLower) ||
            it.description?.lowercase()?.contains(queryLower) == true ||
            it.address.lowercase().contains(queryLower)
        }.sortedBy { it.label.lowercase() }
    }

    override suspend fun count(): Int = entries.size

    override suspend fun contains(address: String): Boolean {
        return entries.values.any { it.address == address }
    }

    private fun generateId(): String {
        return "entry_${nextId++}"
    }

    fun loadFromEntries(list: List<AddressBookEntry>) {
        entries.clear()
        list.forEach { entries[it.id] = it }
        // Update nextId based on existing entries
        val maxId = entries.keys
            .mapNotNull { it.removePrefix("entry_").toIntOrNull() }
            .maxOrNull() ?: 0
        nextId = maxId + 1
    }

    fun toEntries(): List<AddressBookEntry> = entries.values.toList()
}

/**
 * Transaction notes manager for attaching notes to transactions.
 */
interface TransactionNotes {
    /**
     * Set a note for a transaction.
     */
    suspend fun setNote(txHash: String, note: String)

    /**
     * Get the note for a transaction.
     */
    suspend fun getNote(txHash: String): String?

    /**
     * Remove the note for a transaction.
     * @return true if note was removed
     */
    suspend fun removeNote(txHash: String): Boolean

    /**
     * Get all transactions with notes.
     */
    suspend fun getAllNotes(): Map<String, String>

    /**
     * Search notes containing the query string.
     */
    suspend fun searchNotes(query: String): Map<String, String>
}

/**
 * In-memory transaction notes implementation.
 */
class InMemoryTransactionNotes : TransactionNotes {

    private val notes = mutableMapOf<String, String>()

    override suspend fun setNote(txHash: String, note: String) {
        if (note.isBlank()) {
            notes.remove(txHash)
        } else {
            notes[txHash] = note
        }
    }

    override suspend fun getNote(txHash: String): String? = notes[txHash]

    override suspend fun removeNote(txHash: String): Boolean {
        return notes.remove(txHash) != null
    }

    override suspend fun getAllNotes(): Map<String, String> = notes.toMap()

    override suspend fun searchNotes(query: String): Map<String, String> {
        val queryLower = query.lowercase()
        return notes.filter { it.value.lowercase().contains(queryLower) }
    }

    fun loadFromMap(map: Map<String, String>) {
        notes.clear()
        notes.putAll(map)
    }

    fun toMap(): Map<String, String> = notes.toMap()
}
