package com.monito.expense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monito.expense.data.BackupCodec
import com.monito.expense.data.BackupManager
import com.monito.expense.data.MonitoDatabase
import com.monito.expense.data.Repository
import com.monito.expense.drive.SyncCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Restores real backup files when they are present in the (never committed) `samples` folder of the
 * project; skipped otherwise. Prints only counts, never the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RealBackupSampleTest {

    private lateinit var context: Context
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Repository.resetForTests()
        context.deleteDatabase(MonitoDatabase.DB_NAME)
        repo = Repository.get(context)
    }

    private fun samples(extension: String): List<File> =
        listOf(File("samples"), File("../samples")).flatMap { it.listFiles()?.toList() ?: emptyList() }
            .filter { it.name.endsWith(extension) }

    @Test
    fun originalAppBackupsRestore() {
        val files = samples(".monitobk")
        assumeTrue("no .monitobk sample", files.isNotEmpty())
        for (f in files) {
            val bytes = f.readBytes()
            assertNotNull("${f.name} does not decrypt to SQLite", BackupCodec.toSqlite(bytes))
            assertTrue(BackupManager(context, repo).restoreBytes(bytes))
            println("SAMPLE ${f.name}: version=${repo.databaseVersion()} transactions=${repo.transactionCount()} " +
                "categories=${repo.categories(includeSpecial = true).size} recurrences=${repo.recurrenceCount()} " +
                "from=${repo.minDate()} to=${repo.maxDate()}")
            assertEquals("7.0", repo.databaseVersion())
            assertTrue(repo.transactionCount() > 0)
        }
    }

    @Test
    fun driveCopiesAreRecognisedAndNeedAPassphrase() {
        val files = samples(".monitoenc")
        assumeTrue("no .monitoenc sample", files.isNotEmpty())
        val manager = BackupManager(context, repo)
        for (f in files) {
            val bytes = f.readBytes()
            assertTrue(manager.needsPassphrase(bytes))
            assertFalse("must not restore without the passphrase", manager.restoreBytes(bytes))
            var wrong = false
            try {
                manager.restoreEncrypted(bytes, "certainly-not-the-passphrase".toCharArray())
            } catch (e: SyncCrypto.WrongPassphraseException) {
                wrong = true
            }
            assertTrue("a wrong passphrase must be rejected", wrong)
            println("SAMPLE ${f.name}: encrypted copy recognised, wrong passphrase rejected")
        }
    }
}
