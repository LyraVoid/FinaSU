package me.weishu.kernelsu.data.backup.device

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun cleanup() {
        context.deleteSharedPreferences(PREFS_NAME)
        keyStore().apply {
            deleteEntry(CURRENT_ALIAS)
            deleteEntry(LEGACY_ALIAS)
        }
    }

    @Test
    fun legacyCiphertextIsMigratedToTheNewKey() {
        val legacyStore = KeystoreSecretStore(context, PREFS_NAME, LEGACY_ALIAS, LEGACY_ALIAS)
        legacyStore.put(ENTRY, SECRET)
        val legacyCiphertext = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENTRY, null)
        assertNotNull(keyStore().getEntry(LEGACY_ALIAS, null))

        val currentStore = KeystoreSecretStore(context, PREFS_NAME, CURRENT_ALIAS, LEGACY_ALIAS)
        assertEquals(SECRET, currentStore.get(ENTRY))
        val migratedCiphertext = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENTRY, null)
        assertNotEquals(legacyCiphertext, migratedCiphertext)
        assertNotNull(keyStore().getEntry(CURRENT_ALIAS, null))

        keyStore().deleteEntry(LEGACY_ALIAS)
        assertEquals(SECRET, currentStore.get(ENTRY))
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val PREFS_NAME = "keystore_migration_test"
        private const val ENTRY = "migration-probe"
        private const val SECRET = "finasu-keystore-migration"
        private const val CURRENT_ALIAS = "finasu_keystore_migration_test_v1"
        private const val LEGACY_ALIAS = "folksu_keystore_migration_test_v1"
    }
}
