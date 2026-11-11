package com.monito.expense.drive

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase based encryption for the copies kept on Google Drive.
 *
 * File layout: `MNTE1` magic (5 bytes) | 16 byte salt | 12 byte nonce | AES-256-GCM ciphertext.
 * The key is PBKDF2-HMAC-SHA256(passphrase, salt, 120 000 rounds). Nothing but the passphrase can
 * decrypt a file, so the same passphrase must be entered on every device.
 */
object SyncCrypto {

    private val MAGIC = "MNTE1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class WrongPassphraseException : Exception("Could not decrypt: wrong passphrase or corrupt file")

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + SALT_LEN + NONCE_LEN && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(plain)
        return MAGIC + salt + nonce + body
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        if (!isEncrypted(data)) throw WrongPassphraseException()
        var p = MAGIC.size
        val salt = data.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val nonce = data.copyOfRange(p, p + NONCE_LEN); p += NONCE_LEN
        val body = data.copyOfRange(p, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(body)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }
}
