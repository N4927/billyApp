package com.example.billyapp.core

import android.content.Context
import android.util.Log
import com.example.billyapp.ble.BleViewModel
import com.example.shared.FakeServer
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

private fun ByteArray.toHex(): String {
    val hexChars = "0123456789ABCDEF"
    val sb = StringBuilder(size * 2)
    for (byte in this) {
        sb.append(hexChars[(byte.toInt() shr 4) and 0x0F])
        sb.append(hexChars[byte.toInt() and 0x0F])
    }
    return sb.toString()
}

private fun String.decodeHexToByteArray(): ByteArray {
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * Simula encounters con MULTIPLI utenti per testare sia il server remoto che la risoluzione locale
 * Tests multiple users from FakeServer registry
 */
fun simulateEncounter(bleViewModel: BleViewModel, context: Context) {
    val userManager = UserManager(context)

    Log.i("SimulateEncounter", "🎲 Testing MULTIPLE users from FakeServer registry")

    // ✅ Get ALL registered users from FakeServer
    val allUsers = FakeServer.getRegisteredUsers()

    if (allUsers.isEmpty()) {
        Log.e("SimulateEncounter", "❌ No users found in FakeServer registry")
        bleViewModel.addEncounter("Error: No users in FakeServer")
        return
    }

    Log.i("SimulateEncounter", "🔍 Found ${allUsers.size} users in registry: ${allUsers.joinToString { it.name }}")

    // ✅ Test each user in the registry
    allUsers.forEach { user ->
        Log.i("SimulateEncounter", "🧪 Testing user: ${user.name}")
        testUserEncryptionPipeline(user, bleViewModel, userManager)
    }

    // ✅ Also test remote server with current user's BID
    Log.i("SimulateEncounter", "🌐 Testing REMOTE server with current user...")
    testRemoteServerWithCurrentUser(bleViewModel, userManager)
}

/**
 * Test the encryption/decryption pipeline for a specific user
 */
private fun testUserEncryptionPipeline(user: com.example.shared.User, bleViewModel: BleViewModel, userManager: UserManager) {
    try {
        Log.d("SimulateEncounter", "🔄 Generating batch for user: ${user.name}")

        // ✅ Generate a batch for this user (simulates server-side encryption)
        val batch = FakeServer.generateBatch(user.name)

        if (batch.bids.isEmpty()) {
            Log.e("SimulateEncounter", "❌ No BIDs generated for ${user.name}")
            bleViewModel.addEncounter("${user.name} - No BIDs")
            return
        }

        Log.d("SimulateEncounter", "✅ Generated ${batch.bids.size} BIDs for ${user.name}")

        // ✅ Test the first BID in the batch
        val testBid = batch.bids[0]
        val timestamp = System.currentTimeMillis() / 1000

        Log.d("SimulateEncounter", "🔐 Testing BID: ${testBid.take(16)}... for ${user.name}")

        // ✅ Try to resolve the BID back to the user
        val resolvedUser = FakeServer.resolveBid(testBid, timestamp)

        if (resolvedUser != null) {
            if (resolvedUser.name == user.name) {
                Log.d("SimulateEncounter", "✅ PIPELINE WORKING: ${user.name} → BID → ${resolvedUser.name}")
                bleViewModel.addEncounter("${user.name}")
            } else {
                Log.w("SimulateEncounter", "❌ PIPELINE BROKEN: ${user.name} → BID → ${resolvedUser.name}")
            }
        } else {
            Log.w("SimulateEncounter", "❌ PIPELINE BROKEN: ${user.name} → BID → NULL")
        }

    } catch (e: Exception) {
        Log.e("SimulateEncounter", " ERROR testing ${user.name}: ${e.message}")
        bleViewModel.addEncounter("${user.name}  (Error: ${e.message})")
    }
}

/**
 * Test remote server with current user's BID
 */
private fun testRemoteServerWithCurrentUser(bleViewModel: BleViewModel, userManager: UserManager) {
    // ✅ Get current BID from batch for remote testing
    val currentBidHex = userManager.getCurrentBidHex()

    if (currentBidHex == null) {
        Log.e("SimulateEncounter", "❌ No current BID available for remote testing")
        return
    }

    val timestamp = System.currentTimeMillis() / 1000

    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d("SimulateEncounter", "🌐 Testing REMOTE server with current user's BID...")

            val httpClient = getUnsafeOkHttpClient()

            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("cipher8_hex", currentBidHex)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://19.hackathon.ethz.ch/api/match/")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    val obj = JSONObject(bodyStr ?: "{}")
                    val resolvedName = obj.optString("display_name", null)

                    if (!resolvedName.isNullOrEmpty()) {
                        Log.d("SimulateEncounter", "✅ REMOTE SUCCESS: Server resolved to: $resolvedName")
                        bleViewModel.addEncounter("$resolvedName ✅ (Remote)")
                    } else {
                        Log.w("SimulateEncounter", "⚠️ REMOTE: Server returned no user")
                        bleViewModel.addEncounter("Unknown ⚠️ (Remote No Match)")
                    }
                } else {
                    Log.w("SimulateEncounter", "⚠️ REMOTE: Server returned ${response.code}")
                    bleViewModel.addEncounter("Server Error ${response.code} ⚠️")
                }
            }
        } catch (e: Exception) {
            Log.e("SimulateEncounter", "❌ REMOTE ERROR: ${e.message}")
            bleViewModel.addEncounter("Network Error ❌")
        }
    }
}

/**
 * 🔧 OkHttpClient che accetta tutti i certificati SSL (solo per TEST).
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
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}

/**
 * Test del sistema di batch
 */
fun testBatchSystem(context: Context) {
    val userManager = UserManager(context)

    Log.i("BatchTest", "=== TESTING BATCH SYSTEM ===")
    Log.i("BatchTest", "User: ${userManager.getUserName()}")
    Log.i("BatchTest", "Batch valid: ${userManager.isBatchValid()}")
    Log.i("BatchTest", "Batch status: ${userManager.getBatchStatus()}")

    val currentBid = userManager.getCurrentBidHex()
    if (currentBid != null) {
        Log.i("BatchTest", "✅ Current BID: ${currentBid.take(16)}...")
        Log.i("BatchTest", "✅ BID length: ${currentBid.length} chars (${currentBid.length / 2} bytes)")

        val isValid = FakeServer.validateBid(currentBid)
        Log.i("BatchTest", "✅ BID locally valid: $isValid")
    } else {
        Log.w("BatchTest", "❌ No current BID available")
        val refreshed = userManager.refreshBatch()
        Log.i("BatchTest", "🔄 Batch refresh: $refreshed")
    }

    Log.i("BatchTest", "=== BATCH TEST COMPLETE ===")
}