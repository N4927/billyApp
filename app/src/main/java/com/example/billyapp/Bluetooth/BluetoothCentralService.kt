package com.example.billyapp.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import com.billyapp.shared.BillySDK  // ← NUOVO: dal KMM!
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class BluetoothCentralService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ← NUOVO: Usa BillySDK invece di KmmEnvironment
    private val core = BillySDK.core

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val processedDevices = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var isActive = false

    // --- Permission Check ---

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification("Scanning for nearby Billy users...")

        if (!hasScanPermission()) {
            Log.e("BluetoothCentral", "Missing permissions. Stopping.")
            stopSelf()
            return
        }

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e("BluetoothCentral", "❌ Bluetooth disabled")
            stopSelf()
            return
        }

        bluetoothLeScanner = adapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e("BluetoothCentral", "❌ Scanner unavailable")
            stopSelf()
            return
        }

        isActive = true
        scope.launch { startScanning() }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        isActive = false
        try { bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        activeConnections.values.forEach { gatt ->
            try { gatt.disconnect(); gatt.close() } catch (_: Exception) {}
        }
        activeConnections.clear()
        processedDevices.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Scanning ---

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (!hasScanPermission()) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
            Log.i("BluetoothCentral", "🔍 Scanning for Billy devices")
        } catch (e: Exception) {
            Log.e("BluetoothCentral", "❌ Scan failed: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address ?: return

            if (activeConnections.containsKey(address)) return
            if (processedDevices.contains(address)) return

            val uuids = result.scanRecord?.serviceUuids
            if (uuids?.contains(BleConstants.SERVICE_UUID) != true) return

            Log.i("BluetoothCentral", "📱 Found: $address (RSSI: ${result.rssi}dBm)")
            scope.launch { connectToDevice(device) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothCentral", "❌ Scan error: $errorCode")
        }
    }

    // --- GATT Connection ---

    @SuppressLint("MissingPermission")
    private suspend fun connectToDevice(device: BluetoothDevice) {
        val address = device.address ?: return
        if (!hasScanPermission()) return

        processedDevices.add(address)
        Log.d("BluetoothCentral", "🔌 Connecting to $address")

        try {
            val gatt = device.connectGatt(this, false, gattCallback)
            if (gatt != null) activeConnections[address] = gatt
        } catch (e: Exception) {
            Log.e("BluetoothCentral", "❌ Connect failed: ${e.message}")
            activeConnections.remove(address)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device?.address ?: "unknown"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BluetoothCentral", "✅ Connected to $address")
                    try { gatt.discoverServices() } catch (e: Exception) { cleanupConnection(gatt) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> cleanupConnection(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                cleanupConnection(gatt)
                return
            }

            val service = gatt.getService(BleConstants.SERVICE_UUID.uuid)
            val characteristic = service?.getCharacteristic(BleConstants.BID_CHARACTERISTIC_UUID.uuid)

            if (characteristic == null) {
                cleanupConnection(gatt)
                return
            }

            try {
                if (!gatt.readCharacteristic(characteristic)) {
                    cleanupConnection(gatt)
                }
            } catch (e: Exception) {
                cleanupConnection(gatt)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val bid = characteristic.value ?: byteArrayOf()
                val bidHex = bid.joinToString("") { "%02x".format(it) }
                val address = gatt.device?.address ?: "unknown"

                Log.d("BluetoothCentral", "📦 BID from $address: $bidHex")

                // ← NUOVO: Usa BillySDK.core
                scope.launch {
                    try {
                        core.ingestPacket(bidHex)
                        core.syncQueue()
                        Log.i("BluetoothCentral", "✅ Encounter: $address")
                    } catch (e: Exception) {
                        Log.e("BluetoothCentral", "❌ ingestPacket: ${e.message}")
                    }
                }
            }
            cleanupConnection(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun cleanupConnection(gatt: BluetoothGatt?) {
        val address = gatt?.device?.address ?: return
        activeConnections.remove(address)
        try { gatt.disconnect() } catch (_: Exception) {}
        try { gatt.close() } catch (_: Exception) {}
    }

    // --- Notification ---

    private fun startForegroundNotification(msg: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(BleConstants.NOTI_CHANNEL_CENTRAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(BleConstants.NOTI_CHANNEL_CENTRAL, "BLE Central", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, BleConstants.NOTI_CHANNEL_CENTRAL)
            .setContentTitle("Billy – Scanning")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1001, notification)
    }
}