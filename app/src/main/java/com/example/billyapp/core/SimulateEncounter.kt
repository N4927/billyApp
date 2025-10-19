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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/**
 * Simula un encounter BLE con cifratura e lookup al server (TEST ONLY: bypass SSL).
 *
 * ATTENZIONE: questa versione disabilita la verifica SSL e l'hostname verification.
 * Usare solo in ambiente di sviluppo / test.
 */
fun simulateEncounter(bleViewModel: BleViewModel, context: Context) {
    val userManager = UserManager(context)

    // 🔹 Utenti reali del server ETHZ (usati per la simulazione)
    val users = mapOf(
        "Alice" to "ecb73c72d94f1a23",
        "Bob" to "33f24d0c2ab9e78f",
        "Charlie" to "bcb375489ee42a6d",
        "Luca" to "90895e9d21cb8fa6"
    )

    // 🔹 Seleziona utente casuale
    val selected = users.entries.random()
    val displayName = selected.key
    val personalId = selected.value

    // ✅ Allinea il timestamp alla finestra di rotazione
    val currentSec = System.currentTimeMillis() / 1000
    val window = currentSec / Constants.ROTATION_SECONDS * Constants.ROTATION_SECONDS
    val timestamp = window

    // ✅ Chiave coerente con lo username (16 bytes)
    val cryptoManager = CryptographyManager(displayName.lowercase().toByteArray().copyOf(16))

    // 🔐 Crittografia simulata
    val encryptedPayload = cryptoManager.encryptRotatingIdentifier(personalId, timestamp)
    val cipher8Hex = encryptedPayload.copyOfRange(8, 16)
        .joinToString("") { "%02x".format(it) }

    Log.i("SimulateEncounter", "🎲 Simulating encounter with $displayName")
    Log.i("SimulateEncounter", "🧩 Personal ID: $personalId")
    Log.i("SimulateEncounter", "🔐 Cipher8Hex: $cipher8Hex")
    Log.i("SimulateEncounter", "⏱ Timestamp: $timestamp")

    CoroutineScope(Dispatchers.IO).launch {
        try {
            // TEST ONLY: usa sempre il client che accetta tutti i certificati
            val httpClient = getUnsafeOkHttpClient()

            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("cipher8_hex", cipher8Hex)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://19.hackathon.ethz.ch/api/match/") // endpoint reale ETHZ
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    val obj = JSONObject(bodyStr ?: "{}")
                    val resolvedName = obj.optString("display_name", null)

                    if (!resolvedName.isNullOrEmpty()) {
                        Log.d("SimulateEncounter", "🌐 Server match: $resolvedName")
                        bleViewModel.addEncounter(resolvedName)
                    } else {
                        Log.w("SimulateEncounter", "⚠️ Server did not resolve any user")
                    }
                } else {
                    Log.w("SimulateEncounter", "⚠️ Server returned ${response.code}")
                    // Fallback locale compatibile (timestamp + cipher8_hex)
                    val localUser = FakeServer.resolveRotatingId(cipher8Hex, timestamp)
                    if (localUser != null) {
                        Log.d("SimulateEncounter", "🖥️ Local fallback match: ${localUser.displayName}")
                        bleViewModel.addEncounter(localUser.displayName)
                    } else {
                        Log.w("SimulateEncounter", "❌ No local match (FakeServer)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SimulateEncounter", "❌ Network error: ${e.message}")
            // Fallback locale
            val localUser = FakeServer.resolveRotatingId(cipher8Hex, timestamp)
            if (localUser != null) {
                Log.d("SimulateEncounter", "🖥️ Local fallback on exception: ${localUser.displayName}")
                bleViewModel.addEncounter(localUser.displayName)
            } else {
                Log.w("SimulateEncounter", "❌ No local match on exception (FakeServer)")
            }
        }
    }
}

/**
 * 🔧 OkHttpClient che accetta tutti i certificati SSL (solo per TEST).
 *
 * NON usare questa funzione in produzione.
 */
fun getUnsafeOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, SecureRandom())
    val sslSocketFactory = sslContext.socketFactory

    return OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true } // Ignora hostname verification
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}
