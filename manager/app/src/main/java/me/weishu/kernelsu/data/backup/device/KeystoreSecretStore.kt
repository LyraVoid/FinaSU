package me.weishu.kernelsu.data.backup.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import me.weishu.kernelsu.data.backup.SecretCodec
import me.weishu.kernelsu.data.backup.SecretStore

/**
 * [SecretStore] backed by an Android Keystore AES key that never leaves the keystore. Ciphertexts
 * live in a private SharedPreferences file; only the encrypted envelope is persisted.
 */
class KeystoreSecretStore(
    context: Context,
    private val prefsName: String = PREFS_NAME,
    private val alias: String = KEY_ALIAS,
) : SecretStore {

    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun put(key: String, value: String) {
        // A keystore key owns its IV: encryptWithGeneratedIv lets the cipher pick one, because the
        // keystore refuses a caller-provided IV under the default randomized-encryption requirement.
        // Failures are swallowed so a keystore hiccup never takes the UI down mid-keystroke.
        val record = runCatching { SecretCodec.encryptWithGeneratedIv(secretKey(), value) }
            .onFailure { Log.w(TAG, "Unable to encrypt secret", it) }
            .getOrNull() ?: return
        prefs.edit { putString(key, record) }
    }

    override fun get(key: String): String? {
        val record = prefs.getString(key, null) ?: return null
        decryptRecord(alias, record)?.let { return it }
        if (alias != KEY_ALIAS) return null
        val value = decryptRecord(LEGACY_KEY_ALIAS, record) ?: return null
        runCatching {
            SecretCodec.encryptWithGeneratedIv(secretKey(), value)
        }.onSuccess { migrated ->
            prefs.edit { putString(key, migrated) }
        }
        return value
    }

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    private fun secretKey(): SecretKey {
        existingKey(alias)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun decryptRecord(alias: String, record: String): String? =
        runCatching {
            existingKey(alias)?.let { SecretCodec.decrypt(it, record) }
        }.getOrNull()

    private fun existingKey(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    companion object {
        const val PREFS_NAME = "backup_secrets"
        private const val TAG = "KeystoreSecretStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "finasu_backup_secret_v1"
        private const val LEGACY_KEY_ALIAS = "folksu_backup_secret_v1"
    }
}
