package io.monero.core

import io.monero.crypto.Keccak
import io.monero.crypto.Base58
import kotlinx.serialization.Serializable

/**
 * Monero address representation and utilities.
 *
 * Address Types (Mainnet):
 * - Standard: prefix 18 (starts with '4')
 * - Subaddress: prefix 42 (starts with '8')
 * - Integrated: prefix 19 (starts with '4', includes payment ID)
 *
 * Address Structure:
 * [1 byte prefix][32 bytes public spend key][32 bytes public view key][optional 8 bytes payment ID][4 bytes checksum]
 */
@Serializable
data class MoneroAddress(
    val rawAddress: String,
    val network: Network,
    val type: AddressType,
    val publicSpendKey: ByteArray,
    val publicViewKey: ByteArray,
    val paymentId: ByteArray? = null
) {
    /**
     * Network types
     */
    enum class Network(val standardPrefix: Byte, val subaddressPrefix: Byte, val integratedPrefix: Byte) {
        MAINNET(18, 42, 19),
        STAGENET(24, 36, 25),
        TESTNET(53, 63, 54)
    }

    /**
     * Address types
     */
    enum class AddressType {
        STANDARD,
        SUBADDRESS,
        INTEGRATED
    }

    companion object {
        private const val STANDARD_ADDRESS_LENGTH = 95
        private const val INTEGRATED_ADDRESS_LENGTH = 106

        /**
         * Parse a Monero address string
         *
         * @param address Base58 encoded address
         * @return Parsed MoneroAddress
         * @throws IllegalArgumentException if address is invalid
         */
        fun parse(address: String): MoneroAddress {
            require(address.isNotBlank()) { "Address cannot be empty" }

            val decoded = try {
                Base58.decode(address)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Base58 encoding: ${e.message}")
            }

            require(decoded.isNotEmpty()) { "Decoded address is empty" }

            val prefix = decoded[0]
            val (network, type) = determineNetworkAndType(prefix)

            return when (type) {
                AddressType.STANDARD, AddressType.SUBADDRESS -> {
                    require(decoded.size == 65) { "Invalid standard address size: ${decoded.size}" }
                    MoneroAddress(
                        rawAddress = address,
                        network = network,
                        type = type,
                        publicSpendKey = decoded.copyOfRange(1, 33),
                        publicViewKey = decoded.copyOfRange(33, 65)
                    )
                }
                AddressType.INTEGRATED -> {
                    require(decoded.size == 73) { "Invalid integrated address size: ${decoded.size}" }
                    MoneroAddress(
                        rawAddress = address,
                        network = network,
                        type = type,
                        publicSpendKey = decoded.copyOfRange(1, 33),
                        publicViewKey = decoded.copyOfRange(33, 65),
                        paymentId = decoded.copyOfRange(65, 73)
                    )
                }
            }
        }

        /**
         * Create a standard address from keys
         */
        fun fromKeys(
            publicSpendKey: ByteArray,
            publicViewKey: ByteArray,
            network: Network = Network.MAINNET
        ): MoneroAddress {
            require(publicSpendKey.size == 32) { "Public spend key must be 32 bytes" }
            require(publicViewKey.size == 32) { "Public view key must be 32 bytes" }

            val data = byteArrayOf(network.standardPrefix) + publicSpendKey + publicViewKey
            val encoded = Base58.encode(data)

            return MoneroAddress(
                rawAddress = encoded,
                network = network,
                type = AddressType.STANDARD,
                publicSpendKey = publicSpendKey,
                publicViewKey = publicViewKey
            )
        }

        /**
         * Create an integrated address with payment ID
         */
        fun createIntegrated(
            standardAddress: MoneroAddress,
            paymentId: ByteArray
        ): MoneroAddress {
            require(standardAddress.type == AddressType.STANDARD) { "Source must be standard address" }
            require(paymentId.size == 8) { "Payment ID must be 8 bytes" }

            val prefix = when (standardAddress.network) {
                Network.MAINNET -> Network.MAINNET.integratedPrefix
                Network.STAGENET -> Network.STAGENET.integratedPrefix
                Network.TESTNET -> Network.TESTNET.integratedPrefix
            }

            val data = byteArrayOf(prefix) +
                    standardAddress.publicSpendKey +
                    standardAddress.publicViewKey +
                    paymentId

            val encoded = Base58.encode(data)

            return MoneroAddress(
                rawAddress = encoded,
                network = standardAddress.network,
                type = AddressType.INTEGRATED,
                publicSpendKey = standardAddress.publicSpendKey,
                publicViewKey = standardAddress.publicViewKey,
                paymentId = paymentId
            )
        }

        private fun determineNetworkAndType(prefix: Byte): Pair<Network, AddressType> {
            return when (prefix.toInt()) {
                18 -> Network.MAINNET to AddressType.STANDARD
                42 -> Network.MAINNET to AddressType.SUBADDRESS
                19 -> Network.MAINNET to AddressType.INTEGRATED
                24 -> Network.STAGENET to AddressType.STANDARD
                36 -> Network.STAGENET to AddressType.SUBADDRESS
                25 -> Network.STAGENET to AddressType.INTEGRATED
                53 -> Network.TESTNET to AddressType.STANDARD
                63 -> Network.TESTNET to AddressType.SUBADDRESS
                54 -> Network.TESTNET to AddressType.INTEGRATED
                else -> throw IllegalArgumentException("Unknown address prefix: $prefix")
            }
        }
    }

    /**
     * Check if this is a subaddress
     */
    val isSubaddress: Boolean get() = type == AddressType.SUBADDRESS

    /**
     * Check if this has an integrated payment ID
     */
    val isIntegrated: Boolean get() = type == AddressType.INTEGRATED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoneroAddress) return false
        return rawAddress == other.rawAddress
    }

    override fun hashCode(): Int = rawAddress.hashCode()

    override fun toString(): String = rawAddress
}
