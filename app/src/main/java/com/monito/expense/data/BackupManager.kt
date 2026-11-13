package com.monito.expense.data

import android.content.Context
import android.net.Uri
import com.monito.expense.drive.SyncCrypto
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Local backups in the app's own external files folder
 * (`Android/data/<package>/files/backups`, visible in any file manager, no permission needed).
 * Files use the original `.monitobk` format so they can be exchanged with the old app and vice versa.
 */
class BackupManager(private val context: Context, private val repo: Repository) {

    val backupsDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }

    fun list(): List<BackupFile> =
        backupsDir.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupFile(it.name, it.absolutePath, it.lastModified(), it.length()) }
            ?: emptyList()

    /** Snapshot of the current database, encrypted like the original app. */
    fun createBackup(name: String? = null, auto: Boolean = false): File {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
        val base = name?.trim()?.takeIf { it.isNotEmpty() }?.sanitize() ?: if (auto) AUTO_PREFIX else "Monito"
        val target = File(backupsDir, "${base}_$stamp$EXTENSION")
        val plain = repo.withDatabaseClosed { it.readBytes() }
        target.writeBytes(BackupCodec.encrypt(plain))
        return target
    }

    fun restore(file: File): Boolean = restoreBytes(file.readBytes())

    fun restoreFromUri(uri: Uri): Boolean {
        val bytes = readUri(uri) ?: return false
        return restoreBytes(bytes)
    }

    /** Content of a picked file, or null when it cannot be opened. */
    fun readUri(uri: Uri): ByteArray? = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    /** True for a `.monitoenc` file: a Google Drive copy that needs the passphrase before it can be restored. */
    fun needsPassphrase(bytes: ByteArray): Boolean = SyncCrypto.isEncrypted(bytes)

    /**
     * Restores a passphrase-encrypted Drive copy that was downloaded as a file.
     * @throws SyncCrypto.WrongPassphraseException when the passphrase does not open it
     */
    fun restoreEncrypted(bytes: ByteArray, passphrase: CharArray): Boolean =
        restoreBytes(SyncCrypto.decrypt(bytes, passphrase))

    /** Accepts `.monitobk` or a raw `MonitoDB.sqlite`; replaces the live database and upgrades it if needed. */
    fun restoreBytes(bytes: ByteArray): Boolean {
        val sqlite = BackupCodec.toSqlite(bytes) ?: return false
        repo.withDatabaseClosed { dbFile ->
            File(dbFile.path + "-journal").delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbFile.parentFile?.mkdirs()
            dbFile.writeBytes(sqlite)
        }
        return true
    }

    fun exportToUri(file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    fun delete(file: BackupFile): Boolean = File(file.path).delete()

    fun shareUri(file: BackupFile): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))

    /** Keeps only the newest [keep] automatic backups so the daily job does not grow without bound. */
    fun pruneAutoBackups(keep: Int = 7) {
        list().filter { it.name.startsWith(AUTO_PREFIX) }.drop(keep).forEach { File(it.path).delete() }
    }

    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(40)

    companion object {
        const val EXTENSION = ".monitobk"
        const val AUTO_PREFIX = "Backup"
    }
}
