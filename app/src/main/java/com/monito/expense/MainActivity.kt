package com.monito.expense

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.monito.expense.data.Prefs
import com.monito.expense.ui.MonitoNavHost
import com.monito.expense.ui.theme.MonitoTheme

class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_ADD_TRANSACTION = "com.monito.expense.ADD_TRANSACTION"
    }

    /** Set when launched from the widget or the reminder; consumed by the nav host. */
    val pendingAddTransaction = mutableStateOf(false)

    private val unlocked = mutableStateOf(false)
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val prefs = Prefs.get(this)
        setContent {
            val settings by prefs.settings.collectAsState()
            LaunchedEffect(settings.hideContent) { applySecureFlag(settings.hideContent) }
            MonitoTheme(settings.theme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val isUnlocked by unlocked
                    if (!settings.screenLock || isUnlocked) {
                        MonitoNavHost(pendingAddTransaction)
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ADD_TRANSACTION) pendingAddTransaction.value = true
    }

    override fun onResume() {
        super.onResume()
        val settings = Prefs.get(this).current
        if (settings.screenLock && !unlocked.value && !promptShowing) showUnlockPrompt()
    }

    override fun onStop() {
        super.onStop()
        // Require unlocking again next time the app comes to the foreground.
        if (Prefs.get(this).current.screenLock) unlocked.value = false
    }

    private fun applySecureFlag(hide: Boolean) {
        if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun showUnlockPrompt() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(authenticators)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No lock configured on the device: do not lock the user out of their data.
            unlocked.value = true
            return
        }
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    unlocked.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish()
                    } else {
                        unlocked.value = false
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_monito))
                .setSubtitle(getString(R.string.confirm_your_identity))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
