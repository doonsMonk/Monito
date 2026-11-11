package com.monito.expense

import com.monito.expense.data.BackupCodec
import com.monito.expense.data.InsecureSHA1PRNGKeyDerivator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun fakeSqlite(size: Int = 4096): ByteArray =
        ByteArray(size) { (it % 251).toByte() }.also { BackupCodec.SQLITE_HEADER.copyInto(it) }

    @Test
    fun keyDerivationIsDeterministicAnd128Bit() {
        val a = BackupCodec.key().encoded
        val b = BackupCodec.key().encoded
        assertEquals(16, a.size)
        assertArrayEquals(a, b)
        // A different seed must not produce the same key.
        val other = InsecureSHA1PRNGKeyDerivator.deriveInsecureKey("another seed".toByteArray(), 16)
        assertFalse(a.contentEquals(other))
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val plain = fakeSqlite()
        val enc = BackupCodec.encrypt(plain)
        assertNotEquals(plain.toList(), enc.toList())
        assertEquals(0, enc.size % 16)
        assertArrayEquals(plain, BackupCodec.decrypt(enc))
    }

    @Test
    fun toSqliteAcceptsRawAndEncryptedFilesOnly() {
        val plain = fakeSqlite()
        assertTrue(BackupCodec.isSqlite(plain))
        assertArrayEquals(plain, BackupCodec.toSqlite(plain))
        assertArrayEquals(plain, BackupCodec.toSqlite(BackupCodec.encrypt(plain)))
        assertNull(BackupCodec.toSqlite("definitely not a database".toByteArray()))
        assertNull(BackupCodec.toSqlite(ByteArray(0)))
    }
}
