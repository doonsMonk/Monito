package com.monito.expense.data

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Encodes/decodes `.monitobk` files exactly like the original Monito Expense Manager:
 * the whole SQLite file is AES/ECB/PKCS5 encrypted with a 128 bit key derived from a fixed seed
 * through the (deterministic) SHA1PRNG key derivation that old Android versions used.
 */
object BackupCodec {

    private const val SEED = "gZ8C6ffhA8WG150bMuqV3nH5o0ngj5xpTkYGRvBM"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
    val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun key(): SecretKeySpec {
        val seed = SEED.reversed().toByteArray(Charsets.UTF_8)
        val raw = InsecureSHA1PRNGKeyDerivator.deriveInsecureKey(seed, 16)
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: ByteArray): ByteArray =
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, key())
            doFinal(plain)
        }

    fun decrypt(data: ByteArray): ByteArray =
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key())
            doFinal(data)
        }

    fun isSqlite(bytes: ByteArray): Boolean =
        bytes.size >= SQLITE_HEADER.size && SQLITE_HEADER.indices.all { bytes[it] == SQLITE_HEADER[it] }

    /**
     * Accepts either a `.monitobk` (encrypted) or a raw `MonitoDB.sqlite` file and returns the
     * plain SQLite bytes, or null when the content is neither.
     */
    fun toSqlite(bytes: ByteArray): ByteArray? {
        if (isSqlite(bytes)) return bytes
        val decrypted = runCatching { decrypt(bytes) }.getOrNull() ?: return null
        return if (isSqlite(decrypted)) decrypted else null
    }
}
