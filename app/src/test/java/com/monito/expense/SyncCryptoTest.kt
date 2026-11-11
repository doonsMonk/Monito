package com.monito.expense

import com.monito.expense.drive.SyncCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCryptoTest {

    @Test
    fun roundTripWithPassphrase() {
        val plain = ByteArray(5000) { (it * 7).toByte() }
        val enc = SyncCrypto.encrypt(plain, "correct horse battery".toCharArray())
        assertTrue(SyncCrypto.isEncrypted(enc))
        assertFalse(SyncCrypto.isEncrypted(plain))
        assertArrayEquals(plain, SyncCrypto.decrypt(enc, "correct horse battery".toCharArray()))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val enc = SyncCrypto.encrypt("secret data".toByteArray(), "passphrase-1".toCharArray())
        assertThrows(SyncCrypto.WrongPassphraseException::class.java) {
            SyncCrypto.decrypt(enc, "passphrase-2".toCharArray())
        }
    }

    @Test
    fun eachEncryptionUsesFreshSaltAndNonce() {
        val plain = "same input".toByteArray()
        val a = SyncCrypto.encrypt(plain, "pw".toCharArray())
        val b = SyncCrypto.encrypt(plain, "pw".toCharArray())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun tamperedFileIsRejected() {
        val enc = SyncCrypto.encrypt("payload".toByteArray(), "pw".toCharArray())
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 0x01).toByte()
        assertThrows(SyncCrypto.WrongPassphraseException::class.java) { SyncCrypto.decrypt(enc, "pw".toCharArray()) }
    }
}
