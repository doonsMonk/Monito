package com.monito.expense.drive

import android.content.Context
import android.os.Build
import com.monito.expense.data.BackupCodec
import com.monito.expense.data.BackupManager
import com.monito.expense.data.Prefs
import com.monito.expense.data.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Snapshot based Drive backup: every upload is the whole database, encrypted with the user's
 * passphrase, stored under `Monito/backups` in the linked Google Drive. Any device with the same
 * passphrase can restore a snapshot. Only the newest [KEEP] copies are kept.
 */
object DriveSync {

    const val EXTENSION = ".monitoenc"
    private const val KEEP = 10
    private val FOLDER = listOf("Monito", "backups")

    class NotLinkedException : Exception("Google Drive is not linked")
    class NoPassphraseException : Exception("No encryption passphrase set")

    /** Resolves a token silently; throws when the user has to interact (consent) and [interactive] is false. */
    private suspend fun token(context: Context, interactive: Boolean): String {
        val prefs = Prefs.get(context).current
        if (prefs.driveAccount == null) throw NotLinkedException()
        return when (val r = DriveAuth.authorize(context)) {
            is DriveAuth.AuthResult.Token -> r.accessToken
            is DriveAuth.AuthResult.NeedsConsent -> if (interactive) throw IllegalStateException("consent required") else throw NotLinkedException()
            is DriveAuth.AuthResult.Failed -> throw IllegalStateException(r.message)
        }
    }

    private fun passphrase(context: Context): CharArray =
        SecretStore(context).passphrase() ?: throw NoPassphraseException()

    fun snapshotName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
        val device = Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(24)
        return "Monito_${device}_$stamp$EXTENSION"
    }

    /** Encrypts the live database with the passphrase and uploads it. Returns the Drive file name. */
    suspend fun uploadSnapshot(context: Context, interactive: Boolean, accessToken: String? = null): String {
        val token = accessToken ?: token(context, interactive)
        val pass = passphrase(context)
        val repo = Repository.get(context)
        val plain = repo.withDatabaseClosed { it.readBytes() }
        val encrypted = SyncCrypto.encrypt(plain, pass)
        val client = DriveClient(token)
        val folder = client.ensureFolderPath(FOLDER)
        val name = snapshotName()
        client.upload(folder, name, encrypted)
        // keep only the newest copies
        client.list(folder).filter { it.name.endsWith(EXTENSION) }.drop(KEEP).forEach { runCatching { client.delete(it.id) } }
        return name
    }

    suspend fun listSnapshots(context: Context, accessToken: String? = null): List<DriveClient.DriveFile> {
        val client = DriveClient(accessToken ?: token(context, true))
        val folder = client.ensureFolderPath(FOLDER)
        return client.list(folder).filter { it.name.endsWith(EXTENSION) || it.name.endsWith(BackupManager.EXTENSION) }
    }

    /** Downloads, decrypts and restores one snapshot. Also accepts plain `.monitobk` files this app uploaded. */
    suspend fun restoreSnapshot(context: Context, file: DriveClient.DriveFile, accessToken: String? = null): Boolean {
        val client = DriveClient(accessToken ?: token(context, true))
        val data = client.download(file.id)
        val sqliteOrBackup = if (SyncCrypto.isEncrypted(data)) SyncCrypto.decrypt(data, passphrase(context)) else data
        val sqlite = BackupCodec.toSqlite(sqliteOrBackup) ?: return false
        val repo = Repository.get(context)
        return BackupManager(context, repo).restoreBytes(sqlite)
    }

    suspend fun deleteSnapshot(context: Context, file: DriveClient.DriveFile, accessToken: String? = null) {
        DriveClient(accessToken ?: token(context, true)).delete(file.id)
    }
}
