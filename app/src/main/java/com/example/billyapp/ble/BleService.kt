package com.example.billyapp.ble

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.core.Constants
import com.example.shared.Encounter
import com.example.shared.EncounterBus
import com.example.shared.EncounterRepository
import com.example.billyapp.core.UserManager
import kotlinx.coroutines.*

class BleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val encounterRepo = EncounterRepository()
    private lateinit var userManager: UserManager

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    private var isActive = true

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        userManager = UserManager(this)

        Log.i("BleService", "📱 Device name: ${userManager.getUserName()}")
        Log.i("BleService", "🔐 Using server-side encrypted BIDs with batch system")

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner

        // ✅ FIX: Start everything in a single coroutine to ensure proper timing
        scope.launch {
            initializeBleService()
        }
    }

    override fun onDestroy() {
        isActive = false
        stopAdvertising()
        stopScan()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ✅ FIX: New method to handle initialization in correct order
    private suspend fun initializeBleService() {
        Log.d("BleService", "🔄 Starting BLE service initialization...")

        // Step 1: Generate batch FIRST
        Log.d("BleService", "🔧 Step 1: Generating batch...")
        val batchSuccess = userManager.refreshBatch()

        if (batchSuccess) {
            Log.i("BleService", "✅ Batch generated successfully")

            // Step 2: Start advertising with the batch
            Log.d("BleService", "🔧 Step 2: Starting advertising loop...")
            startScan() // Start scanning in background
            loopRotateAndAdvertise() // This will block until service stops
        } else {
            Log.e("BleService", "❌ Failed to generate batch - cannot start BLE")
            updateNotification("BLE failed - No batch")
            stopSelf() // Stop service if batch generation fails
        }
    }

    // 🔹 Foreground notification to keep BLE alive
    private fun startForegroundNoti(contentText: String = "Advertising & scanning") {
        val channelId = "ble_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "BLE activity",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val noti: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BillyApp")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, noti)
    }

    // 🔹 Periodically advertise rotating BIDs from batch (FIXED timing)
    private suspend fun loopRotateAndAdvertise() {
        Log.d("BleService", "🎯 Starting advertising loop...")

        while (isActive) {
            stopAdvertising()

            // ✅ Use UserManager to get current BID
            val bidBytes = userManager.getCurrentBidBytes()

            if (bidBytes != null) {
                Log.d("BleService", "📡 Advertising BID from batch: ${bidBytes.size} bytes")
                Log.d("BleService", "🧩 BID Hex: ${bidBytes.joinToString("") { "%02x".format(it) }}")

                startAdvertising(bidBytes)
                updateNotification("BLE active - ${userManager.getBatchStatus()}")

                // ✅ ADDED: Debug log to confirm advertising started
                Log.d("BleService", "✅ SUCCESS: BLE Advertising ACTIVE")
            } else {
                Log.w("BleService", "⚠️ No valid BID available")

                // ✅ FIX: Use available methods from UserManager instead of getCurrentBatch()
                val user = userManager.getUser()
                val hasBatch = userManager.getCurrentBidBytes() != null
                Log.d("BleService", "🔍 DEBUG - User: ${user?.name}, Has Batch: $hasBatch")

                updateNotification("BLE inactive - No valid batch")

                // ✅ FIX: Only refresh batch if we don't have one
                if (!hasBatch) {
                    Log.d("BleService", "🔄 Attempting to refresh batch...")
                    if (userManager.refreshBatch()) {
                        Log.i("BleService", "✅ Batch refreshed successfully")
                    } else {
                        Log.e("BleService", "❌ Failed to refresh batch")
                    }
                }
            }

            delay(Constants.ROTATION_SECONDS * 1000L) // Wait for next rotation
        }

        Log.d("BleService", "🛑 Stopped advertising due to service inactive")
        updateNotification("BLE advertising stopped")
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 🔹 Advertising logic
    private fun startAdvertising(payload: ByteArray) {
        if (!hasBluetoothPermissions()) {
            Log.w("BleService", "⚠️ Bluetooth permissions not granted, skip startAdvertising")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleService", "⚠️ Bluetooth not enabled, skip startAdvertising")
                return
            }
            val adv = advertiser ?: return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(Constants.SERVICE_UUID)
                .addServiceData(Constants.SERVICE_UUID, payload)
                .build()

            // ✅ ADDED: More detailed advertising logs
            Log.d("BleService", "🚀 STARTING ADVERTISING:")
            Log.d("BleService", "   UUID: ${Constants.SERVICE_UUID}")
            Log.d("BleService", "   Payload: ${payload.size} bytes")
            Log.d("BleService", "   Payload Hex: ${payload.joinToString("") { "%02x".format(it) }}")

            adv.startAdvertising(settings, data, advCb)
            Log.d("BleService", "🎯 ADVERTISING ACTIVE - BLE is now broadcasting!")
            updateNotification("BLE active")

        } catch (e: SecurityException) {
            Log.e("BleService", "❌ BLE permission error: ${e.message}")
        } catch (e: Exception) {
            Log.e("BleService", "❌ Advertising error: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advCb)
            Log.d("BleService", "🛑 Stop advertising")
        } catch (e: SecurityException) {
            Log.e("BleService", "❌ Stop advertising error: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        val channelId = "ble_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BillyApp")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, notification)
    }

    // 🔹 Scanning logic
    private fun startScan() {
        if (!hasBluetoothPermissions()) {
            Log.w("BleService", "⚠️ Bluetooth permissions not granted, skip startScan")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleService", "⚠️ Bluetooth not enabled, skip startScan")
                return
            }
            val sc = scanner ?: return

            val filter = ScanFilter.Builder()
                .setServiceUuid(Constants.SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    val payload = record.getServiceData(Constants.SERVICE_UUID) ?: return
                    if (payload.size != 16) return  // Only accept full 16-byte BIDs

                    val payloadHex = payload.joinToString("") { "%02x".format(it) }
                    val currentTime = System.currentTimeMillis() / 1000

                    Log.d("BleService", "📡 Received BID: ${payloadHex.take(16)}..., RSSI: ${result.rssi}")

                    // ✅ Process encounter using new architecture
                    scope.launch {
                        val encounter = encounterRepo.processEncounter(payloadHex, result.rssi)
                        if (encounter != null) {
                            EncounterBus.emit(encounter)
                            Log.d("BleService", "🟢 Emitted encounter for ${encounter.resolvedName ?: "Unknown user"}")
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BleService", "❌ Scan failed with error: $errorCode")
                }
            }

            sc.startScan(listOf(filter), settings, scanCb)
            Log.d("BleService", "🔍 Started BLE scanning")

        } catch (e: SecurityException) {
            Log.e("BleService", "❌ Start scan error: ${e.message}")
        }
    }

    private fun stopScan() {
        try {
            scanner?.stopScan(scanCb)
            scanCb = null
            Log.d("BleService", "🛑 Stop scanning")
        } catch (e: SecurityException) {
            Log.e("BleService", "❌ Stop scan error: ${e.message}")
        }
    }
}