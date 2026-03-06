package com.example.billyapp.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.billyapp.shared.BillySDK  // ← NUOVO: dal KMM!
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BluetoothPeripheralService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ← NUOVO: Usa BillySDK invece di KmmEnvironment
    private val core = BillySDK.core

    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var currentAdvertiseCallback: AdvertiseCallback? = null

    @Volatile
    private var isActive = false

    // --- Permission Check ---

    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Hex helper ---

    private fun String.hexToByteArray(): ByteArray {
        val clean = trim().removePrefix("0x").replace(" ", "")
        require(clean.length % 2 == 0) { "Invalid hex string length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification("Starting BLE advertising...")

        if (!hasAdvertisePermission()) {
            Log.e("BluetoothPeripheral", "Missing permissions. Stopping.")
            stopSelf()
            return
        }

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e("BluetoothPeripheral", "❌ Bluetooth disabled")
            stopSelf()
            return
        }

        bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e("BluetoothPeripheral", "❌ Advertiser unavailable")
            stopSelf()
            return
        }

        bluetoothGattServer = openGattServerSafely(manager)
        if (bluetoothGattServer == null) {
            Log.e("BluetoothPeripheral", "❌ GATT server failed")
            stopSelf()
            return
        }

        isActive = true
        scope.launch { advertisingLoop() }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        isActive = false
        if (hasAdvertisePermission()) {
            stopAdvertising()
        }
        try { bluetoothGattServer?.close() } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Main Loop ---

    private suspend fun advertisingLoop() {
        // ← NUOVO: Usa BillySDK.core
        try {
            core.ensureAdvertisingBatch()
        } catch (e: Exception) {
            Log.e("BluetoothPeripheral", "❌ Batch error: ${e.message}")
            updateNotification("Batch error")
            return
        }

        while (isActive) {
            if (!hasAdvertisePermission()) {
                stopSelf()
                return
            }

            // ← NUOVO: Usa BillySDK.core.getCurrentBid()
            val bid = try {
                core.getCurrentBid()
            } catch (e: Exception) {
                Log.e("BluetoothPeripheral", "❌ getCurrentBid: ${e.message}")
                null
            }

            val payload: ByteArray? = when (bid) {
                null -> null
                is com.billyapp.shared.domain.model.Bid -> bid.hex.hexToByteArray()
                is ByteArray -> bid
                is String -> bid.hexToByteArray()
                else -> {
                    Log.e("BluetoothPeripheral", "Unsupported BID type: ${bid::class}")
                    null
                }
            }

            if (payload != null) {
                if (hasAdvertisePermission()) {
                    updateGattService(payload)
                    startAdvertising(payload)
                    updateNotification("Advertising: ${payload.size} bytes")
                    Log.i("BluetoothPeripheral", "📢 BID: ${payload.joinToString("") { "%02x".format(it) }}")
                }
            } else {
                updateNotification("Waiting for BID...")
                core.ensureAdvertisingBatch()
            }

            delay(BleConstants.ROTATION_SECONDS * 1000L)
            if (hasAdvertisePermission()) {
                stopAdvertising()
            }
        }
    }

    // --- BLE Advertising ---

    @SuppressLint("MissingPermission")
    private fun startAdvertising(payload: ByteArray) {
        if (!hasAdvertisePermission() || bluetoothLeAdvertiser == null) return

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(BleConstants.SERVICE_UUID)
            .addServiceData(BleConstants.SERVICE_UUID, payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i("BluetoothPeripheral", "✅ Advertising started")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BluetoothPeripheral", "❌ Advertising failed: $errorCode")
            }
        }

        currentAdvertiseCallback = callback

        try {
            bluetoothLeAdvertiser?.startAdvertising(settings, advertiseData, callback)
        } catch (e: Exception) {
            Log.e("BluetoothPeripheral", "❌ startAdvertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        currentAdvertiseCallback?.let { callback ->
            try {
                bluetoothLeAdvertiser?.stopAdvertising(callback)
            } catch (_: Exception) {}
            currentAdvertiseCallback = null
        }
    }

    // --- GATT Server ---

    @SuppressLint("MissingPermission")
    private fun updateGattService(payload: ByteArray) {
        if (!hasAdvertisePermission()) return

        try {
            bluetoothGattServer?.getService(BleConstants.SERVICE_UUID.uuid)?.let {
                bluetoothGattServer?.removeService(it)
            }
        } catch (_: Exception) {}

        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID.uuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val characteristic = BluetoothGattCharacteristic(
            BleConstants.BID_CHARACTERISTIC_UUID.uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            value = payload
        }

        service.addCharacteristic(characteristic)

        try {
            bluetoothGattServer?.addService(service)
        } catch (e: Exception) {
            Log.e("BluetoothPeripheral", "❌ addService: ${e.message}")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.i("BluetoothPeripheral", "🔗 Connected: ${device?.address}")
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    Log.i("BluetoothPeripheral", "🔌 Disconnected: ${device?.address}")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (!hasAdvertisePermission()) return

            if (characteristic?.uuid == BleConstants.BID_CHARACTERISTIC_UUID.uuid) {
                Log.d("BluetoothPeripheral", "📖 Read by ${device?.address}")
                try {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        characteristic.value
                    )
                } catch (e: Exception) {
                    Log.e("BluetoothPeripheral", "❌ sendResponse: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGattServerSafely(manager: BluetoothManager): BluetoothGattServer? {
        if (!hasAdvertisePermission()) return null
        return try {
            manager.openGattServer(this, gattServerCallback)
        } catch (e: Exception) {
            Log.e("BluetoothPeripheral", "❌ openGattServer: ${e.message}")
            null
        }
    }

    // --- Notification ---

    private fun startForegroundNotification(msg: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(BleConstants.NOTI_CHANNEL_PERIPHERAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(BleConstants.NOTI_CHANNEL_PERIPHERAL, "BLE Peripheral", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, BleConstants.NOTI_CHANNEL_PERIPHERAL)
            .setContentTitle("Billy – Advertising")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1002, notification)
    }

    private fun updateNotification(text: String) {
        startForegroundNotification(text)
    }
}