package com.example.data

import android.content.Context
import java.security.SecureRandom
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object DatabaseProvider {
    @Volatile
    private var cachedDbPassword: ByteArray? = null

    @Synchronized
    fun getDbPassword(context: Context): ByteArray {
        cachedDbPassword?.let { return it.clone() }
        val appContext = context.applicationContext ?: context
        val fallbackPrefs = appContext.getSharedPreferences("weekly_finance_fallback_secure_prefs", Context.MODE_PRIVATE)

        val keyBytes = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                appContext,
                "weekly_finance_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var storedKey = prefs.getString("db_key", null) ?: fallbackPrefs.getString("db_key", null)
            if (storedKey == null) {
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                storedKey = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
            }
            prefs.edit().putString("db_key", storedKey).apply()
            fallbackPrefs.edit().putString("db_key", storedKey).apply()
            Base64.decode(storedKey, Base64.DEFAULT)
        } catch (e: Throwable) {
            android.util.Log.e("DatabaseProvider", "Failed to use EncryptedSharedPreferences, falling back", e)
            var storedKey = fallbackPrefs.getString("db_key", null)
            if (storedKey == null) {
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                storedKey = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
                fallbackPrefs.edit().putString("db_key", storedKey).apply()
            }
            Base64.decode(storedKey, Base64.DEFAULT)
        }
        cachedDbPassword = keyBytes
        return keyBytes.clone()
    }

    fun getDatabase(context: Context): AppDatabase {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences("weekly_finance_prefs", Context.MODE_PRIVATE)
        val isDemoMode = prefs.getBoolean("is_demo_mode", false)
        return AppDatabase.getDatabase(appContext, isDemoMode, false, getDbPassword(appContext))
    }
}

