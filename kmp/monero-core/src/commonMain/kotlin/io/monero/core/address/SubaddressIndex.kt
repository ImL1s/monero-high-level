package io.monero.core.address

import kotlinx.serialization.Serializable

/**
 * Subaddress index identifying a specific subaddress within a wallet.
 * 
 * @property major Account index (0 = main account)
 * @property minor Address index within the account (0 = main address)
 */
@Serializable
data class SubaddressIndex(
    val major: Int,
    val minor: Int
) {
    init {
        require(major >= 0) { "Major index must be non-negative" }
        require(minor >= 0) { "Minor index must be non-negative" }
    }
    
    /** True if this is the main address (0,0) */
    val isMainAddress: Boolean get() = major == 0 && minor == 0
    
    /** True if this is a subaddress (not 0,0) */
    val isSubaddress: Boolean get() = !isMainAddress
    
    override fun toString(): String = "($major,$minor)"
    
    companion object {
        /** Main address index */
        val MAIN = SubaddressIndex(0, 0)
        
        /**
         * Parse from string format "(major,minor)"
         */
        fun parse(s: String): SubaddressIndex {
            val match = Regex("\\((\\d+),(\\d+)\\)").matchEntire(s)
                ?: throw IllegalArgumentException("Invalid subaddress index format: $s")
            return SubaddressIndex(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt()
            )
        }
    }
}
