package com.example.util

import com.example.BuildConfig

object SecureConfig {
    val firebaseDatabaseUrl: String = "https://collection-app-2007-default-rtdb.asia-southeast1.firebasedatabase.app/"
    val firebaseStorageUrl: String = "gs://collection-app-2007.firebasestorage.app"
    val geminiApiKey: String = BuildConfig.GEMINI_API_KEY
}
