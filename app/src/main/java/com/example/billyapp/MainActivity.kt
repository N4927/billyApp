package com.example.billyapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.billyapp.ble.BleService

class MainActivity : ComponentActivity() {

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.entries.all { it.value }
        if (allGranted) {
            startBleService()
        } else {
            // Qui puoi gestire il caso in cui l'utente rifiuti i permessi
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(
                onStartService = { requestAllPermissions() },
                onStopService = { stopService(Intent(this, BleService::class.java)) }
            )
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // anche sugli SDK >29 serve la location per BLE scan
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (Build.VERSION.SDK_INT >= 34) {
            // Obbligatorio per i ForegroundService di tipo location
            permissions += Manifest.permission.FOREGROUND_SERVICE_LOCATION
        }

        reqPerms.launch(permissions.toTypedArray())
    }


    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun MainScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    Column {
        Button(onClick = onStartService) {
            Text("Avvia BLE Service")
        }
        Button(onClick = onStopService) {
            Text("Ferma BLE Service")
        }
    }
}
