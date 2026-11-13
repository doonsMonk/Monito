package com.monito.expense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monito.expense.data.BackupManager
import com.monito.expense.data.CatType
import com.monito.expense.data.MonitoDatabase
import com.monito.expense.data.Repository
import com.monito.expense.drive.SyncCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

/** A Google Drive copy (.monitoenc) downloaded as a file can be imported with its passphrase. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EncryptedImportTest {

    @Test
    fun encryptedCopyRestoresOnlyWithTheRightPassphrase() {
        val context: Context = ApplicationProvider.getApplicationContext()
        Repository.resetForTests()
        context.deleteDatabase(MonitoDatabase.DB_NAME)
        val repo = Repository.get(context)
        val manager = BackupManager(context, repo)
        val cat = repo.insertCategory("Encrypted test", CatType.EXPENSE, repo.colors().first().id, false).toInt()
        repo.insertTransaction("kept", BigDecimal("42"), cat, LocalDate.of(2026, 11, 13), null)
        val copy = SyncCrypto.encrypt(repo.withDatabaseClosed { it.readBytes() }, "correct horse".toCharArray())

        repo.moveToTrash(repo.transactions(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).first().id)
        assertEquals(0, repo.transactionCount())

        assertTrue(manager.needsPassphrase(copy))
        assertFalse(manager.restoreBytes(copy))
        var rejected = false
        try { manager.restoreEncrypted(copy, "wrong".toCharArray()) } catch (e: SyncCrypto.WrongPassphraseException) { rejected = true }
        assertTrue(rejected)
        assertEquals(0, repo.transactionCount())

        assertTrue(manager.restoreEncrypted(copy, "correct horse".toCharArray()))
        assertEquals(1, repo.transactionCount())
    }
}
