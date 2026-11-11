package com.monito.expense.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the Drive passphrase on the device encrypted with a key that lives in the Android Keystore,
 * so it is never written to disk in clear text.
 */
class SecretStore(context: Context) {

    private val sp = context.getSharedPreferences("monito_secrets", Context.MODE_PRIVATE)

    fun passphrase(): CharArray? {
        val blob = sp.getString(KEY_PASSPHRASE, null) ?: return null
        return runCatching {
            val data = Base64.decode(blob, Base64.NO_WRAP)
            val nonce = data.copyOfRange(0, 12)
            val body = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
            String(cipher.doFinal(body), Charsets.UTF_8).toCharArray()
        }.getOrNull()
    }

    fun savePassphrase(passphrase: CharArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(String(passphrase).toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
        sp.edit().putString(KEY_PASSPHRASE, blob).apply()
    }

    fun clearPassphrase() {
        sp.edit().remove(KEY_PASSPHRASE).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "monito_secret_store"
        private const val KEY_PASSPHRASE = "drivePassphrase"
    }
}
