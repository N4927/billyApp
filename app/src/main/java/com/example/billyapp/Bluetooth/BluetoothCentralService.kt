package com.example.billyapp.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.kmm.KmmEnvironment
import com.billyapp.shared.core.BillyCore
import kotlinx.coroutines.*

class BluetoothCentralService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val core: BillyCore
        get() = KmmEnvironment.core

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    @Volatile
    private var isActive = false

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification("Starting BLE scan...")

        // Inizializza il core KMM (se non già inizializzato)
        KmmEnvironment.init(applicationContext)

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e("BluetoothCentral", "❌ Bluetooth disabled or unavailable")
            stopSelf()
            return
        }

        scanner = adapter.bluetoothLeScanner
        isActive = true

        scope.launch {
            startScanning()
        }
    }

    override fun onDestroy() {
        isActive = false
        stopScanning()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------

    private fun startForegroundNotification(msg: String) {
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26 &&
            nm.getNotificationChannel(BleConstants.NOTI_CHANNEL_CENTRAL) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(
                    BleConstants.NOTI_CHANNEL_CENTRAL,
                    "BLE Central",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification: Notification =
            NotificationCompat.Builder(this, BleConstants.NOTI_CHANNEL_CENTRAL)
                .setContentTitle("Billy – Scanning")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()

        startForeground(1001, notification)
    }

    private fun updateNotification(text: String) {
        startForegroundNotification(text)
    }

    // ---------------------------------------------------------
    // Scanner
    // ---------------------------------------------------------

    @SuppressLint("MissingPermission") // le permission vengono controllate prima
    private fun startScanning() {
        if (!hasPermissions()) {
            Log.w("BluetoothCentral", "⚠️ Missing permissions")
            updateNotification("Missing permissions")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.w("BluetoothCentral", "⚠️ Bluetooth disabled")
            updateNotification("Bluetooth disabled")
            return
        }

        val sc = scanner ?: run {
            Log.e("BluetoothCentral", "❌ BluetoothLeScanner is null")
            updateNotification("Scanner unavailable")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val payload = record.getServiceData(BleConstants.SERVICE_UUID) ?: return
                if (payload.size != 16) return // accettiamo solo BIDs di 16 byte

                // 🔁 Convertiamo il ByteArray in esadecimale per BillyCore
                val bidHex = payload.joinToString("") { "%02x".format(it) }

                Log.d(
                    "BluetoothCentral",
                    "📡 BID received: ${bidHex.take(16)}... RSSI=${result.rssi}"
                )

                // Anche se ingestPacket non è suspend, lo lanciamo in background
                scope.launch {
                    try {
                        core.ingestPacket(bidHex)
                        core.syncQueue()
                    } catch (e: Exception) {
                        Log.e("BluetoothCentral", "❌ ingestPacket/syncQueue error: ${e.message}")
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BluetoothCentral", "❌ Scan failed: $errorCode")
                updateNotification("Scan error $errorCode")
            }
        }

        try {
            sc.startScan(listOf(filter), settings, scanCallback)
            Log.d("BluetoothCentral", "🚀 Started BLE scanning")
            updateNotification("Scanning for BID packets")
        } catch (e: SecurityException) {
            Log.e("BluetoothCentral", "❌ startScan SecurityException: ${e.message}")
            updateNotification("Scan security error")
        } catch (e: Exception) {
            Log.e("BluetoothCentral", "❌ startScan exception: ${e.message}")
            updateNotification("Scan failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
            scanCallback = null
            Log.d("BluetoothCentral", "🛑 Stopped BLE scanning")
        } catch (e: SecurityException) {
            Log.e("BluetoothCentral", "❌ stopScan SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e("BluetoothCentral", "❌ stopScan exception: ${e.message}")
        }
    }

    // ---------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------

    private fun hasPermissions(): Boolean {
        val pm = packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e("BluetoothCentral", "❌ Device does not support BLE")
            return false
        }

        fun check(p: String) =
            ContextCompat.checkSelfPermission(this, p) ==
                    PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(android.Manifest.permission.BLUETOOTH_SCAN) &&
                    check(android.Manifest.permission.BLUETOOTH_CONNECT) &&
                    check(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            check(android.Manifest.permission.BLUETOOTH) &&
                    check(android.Manifest.permission.BLUETOOTH_ADMIN) &&
                    check(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
