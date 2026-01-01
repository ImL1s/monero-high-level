package io.monero.wallet.storage

import io.monero.crypto.ChaCha20Poly1305
import io.monero.crypto.WalletEncryption
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * File-based wallet storage implementation for JVM.
 * 
 * Wallet files are encrypted using ChaCha20-Poly1305 with a key derived
 * from the user's password using PBKDF2-Keccak256.
 */
class FileWalletStorage : WalletStorage {

    companion object {
        private const val WALLET_EXTENSION = ".wallet"
        private const val KEYS_EXTENSION = ".keys"
    }

    override suspend fun exists(path: String): Boolean {
        return File(getWalletFilePath(path)).exists()
    }

    override suspend fun save(path: String, data: WalletData, password: String) {
        val file = File(getWalletFilePath(path))
        
        try {
            // Ensure parent directory exists
            file.parentFile?.mkdirs()

            // Serialize to JSON
            val jsonString = walletJson.encodeToString(data)

            // Encrypt with ChaCha20-Poly1305
            val encrypted = WalletEncryption.encrypt(password, jsonString.encodeToByteArray())
            file.writeBytes(encrypted)

            // Also save a separate keys file for quick access (unencrypted metadata)
            saveKeysFile(path, data)
        } catch (e: IOException) {
            throw WalletStorageException.WriteError(path, e)
        }
    }

    override suspend fun load(path: String, password: String): WalletData {
        val file = File(getWalletFilePath(path))

        if (!file.exists()) {
            throw WalletStorageException.FileNotFound(path)
        }

        try {
            val encryptedData = file.readBytes()

            // Decrypt with ChaCha20-Poly1305
            val decrypted = try {
                WalletEncryption.decrypt(password, encryptedData)
            } catch (e: ChaCha20Poly1305.AuthenticationException) {
                throw WalletStorageException.InvalidPassword(path)
            }

            val jsonString = decrypted.decodeToString()
            return walletJson.decodeFromString(jsonString)
        } catch (e: WalletStorageException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw WalletStorageException.FileNotFound(path)
        } catch (e: IOException) {
            throw WalletStorageException.ReadError(path, e)
        } catch (e: Exception) {
            throw WalletStorageException.CorruptedData(path, e)
        }
    }

    override suspend fun delete(path: String): Boolean {
        val walletFile = File(getWalletFilePath(path))
        val keysFile = File(getKeysFilePath(path))

        val walletDeleted = walletFile.delete()
        val keysDeleted = keysFile.delete()

        return walletDeleted || keysDeleted
    }

    override suspend fun listWallets(directory: String): List<String> {
        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(WALLET_EXTENSION) }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * Save a minimal keys file for quick wallet identification.
     */
    private fun saveKeysFile(path: String, data: WalletData) {
        val keysFile = File(getKeysFilePath(path))
        val keysData = KeysFileData(
            network = data.network,
            primaryAddress = data.primaryAddress,
            isViewOnly = data.privateSpendKey == null
        )
        keysFile.writeText(walletJson.encodeToString(keysData))
    }

    /**
     * Quick check of wallet properties without loading full data.
     */
    suspend fun getWalletInfo(path: String): KeysFileData? {
        val keysFile = File(getKeysFilePath(path))
        if (!keysFile.exists()) {
            return null
        }

        return try {
            walletJson.decodeFromString(keysFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun getWalletFilePath(path: String): String {
        return if (path.endsWith(WALLET_EXTENSION)) path else "$path$WALLET_EXTENSION"
    }

    private fun getKeysFilePath(path: String): String {
        val basePath = if (path.endsWith(WALLET_EXTENSION)) {
            path.removeSuffix(WALLET_EXTENSION)
        } else {
            path
        }
        return "$basePath$KEYS_EXTENSION"
    }
}

/**
 * Minimal keys file data for quick wallet identification.
 */
@kotlinx.serialization.Serializable
data class KeysFileData(
    val network: NetworkType,
    val primaryAddress: String,
    val isViewOnly: Boolean
)
