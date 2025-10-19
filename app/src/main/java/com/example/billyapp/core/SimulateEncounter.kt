package com.example.billyapp.core

import android.content.Context
import android.util.Log
import com.example.billyapp.ble.BleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Simula un encounter BLE con cifratura e (opzionale) lookup al server.
 */
fun simulateEncounter(bleViewModel: BleViewModel, context: Context) {
    val userManager = UserManager(context)
    val cryptoManager = userManager.getCryptographyManager()

    // 🔹 Definizione utenti noti (16-byte ID)
    val users = mapOf(
        "Alice" to "ecb73c72d94f1a23",
        "Bob" to "33f24d0c2ab9e78f",
        "Charlie" to "bcb375489ee42a6d",
        "Luca" to "90895e9d21cb8fa6",
        "Marco" to "8b44a291c7e3d0af",
        "Giulia" to "f8507096b1c3e79d"
    )

    // 🔹 Seleziona utente casuale
    val selected = users.entries.random()
    val displayName = selected.key
    val personalId = selected.value
    val timestamp = System.currentTimeMillis() / 1000

    // 🔐 Crittografia simulata con CryptographyManager
    val encryptedPayload = cryptoManager.encryptRotatingIdentifier(personalId, timestamp)
    val cipher8Hex = encryptedPayload.take(8).joinToString("") { "%02x".format(it) }

    Log.i("SimulateEncounter", "🎲 Selected user: $displayName")
    Log.i("SimulateEncounter", "🧩 Personal ID: $personalId")
    Log.i("SimulateEncounter", "🔐 Cipher8Hex: $cipher8Hex")
    Log.i("SimulateEncounter", "⏱ Timestamp: $timestamp")

    // 🔹 Invia la richiesta simulata al server (non bloccante)
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("cipher8_hex", cipher8Hex)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://19.hackathon.ethz.ch/api/match/") // ✅ endpoint server reale
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    val obj = JSONObject(bodyStr ?: "{}")
                    val resolvedName = obj.optString("display_name", displayName)

                    Log.d("SimulateEncounter", "🌐 Server match: $resolvedName")
                    bleViewModel.addEncounter(resolvedName)
                } else {
                    Log.w("SimulateEncounter", "⚠️ Server returned ${response.code}")
                    bleViewModel.addEncounter(displayName) // fallback locale
                }
            }
        } catch (e: Exception) {
            Log.e("SimulateEncounter", "❌ Error: ${e.message}")
            // fallback locale se server non disponibile
            bleViewModel.addEncounter(displayName)
        }
    }
}
