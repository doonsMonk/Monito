package com.monito.expense.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Obtains an OAuth access token for Google Drive through Google Play services' AuthorizationClient.
 * The first time, the user has to pick an account and consent (a [AuthResult.NeedsConsent] result
 * carries the intent to launch); afterwards tokens are issued silently.
 *
 * Requires an Android OAuth client (package name + signing certificate SHA-1) registered in a
 * Google Cloud project with the Drive API enabled.
 */
object DriveAuth {

    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_EMAIL = "email"

    sealed class AuthResult {
        data class Token(val accessToken: String) : AuthResult()
        data class NeedsConsent(val pendingIntent: PendingIntent) : AuthResult()
        data class Failed(val message: String) : AuthResult()
    }

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE), Scope(SCOPE_EMAIL)))
            .build()

    /** Silent token when possible; otherwise an intent the UI must launch. */
    suspend fun authorize(context: Context): AuthResult = try {
        val result = Identity.getAuthorizationClient(context).authorize(request()).await()
        when {
            result.hasResolution() -> AuthResult.NeedsConsent(result.pendingIntent!!)
            result.accessToken != null -> AuthResult.Token(result.accessToken!!)
            else -> AuthResult.Failed("no token")
        }
    } catch (e: Exception) {
        AuthResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** Turns the activity result of the consent screen into a token. */
    fun fromConsentResult(context: Context, data: Intent?): AuthResult = try {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        result.accessToken?.let { AuthResult.Token(it) } ?: AuthResult.Failed("no token")
    } catch (e: Exception) {
        AuthResult.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** E-mail of the account behind [accessToken], for display in settings. */
    fun fetchEmail(accessToken: String): String? = runCatching {
        val conn = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().readText()).optString("email").takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
