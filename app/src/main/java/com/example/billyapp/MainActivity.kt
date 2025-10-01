package com.example.billyapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.billyapp.ble.BleService
import com.example.billyapp.core.BleViewModel
import com.example.billyapp.core.api.ChatViewModel
import com.example.billyapp.ui.screens.*

class MainActivity : ComponentActivity() {
    private val bleViewModel: BleViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.entries.all { it.value }
        if (allGranted) {
            startBleService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            MainApp(
                navController = navController,
                bleViewModel = bleViewModel,
                chatViewModel = chatViewModel,
                onStartService = { requestAllPermissions() },
                onStopService = { stopService(Intent(this, BleService::class.java)) }
            )
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += android.Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += android.Manifest.permission.BLUETOOTH_SCAN
            permissions += android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += android.Manifest.permission.ACCESS_FINE_LOCATION
            permissions += android.Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
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
fun MainApp(
    navController: NavHostController,
    bleViewModel: BleViewModel,
    chatViewModel: ChatViewModel,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("home") },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("chats") },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Chats") },
                    label = { Text("Chats") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsanti Start/Stop BLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onStartService,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start BLE")
                        }
                        Button(
                            onClick = onStopService,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop BLE")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // HomeScreen con encounter reali
                    Box(modifier = Modifier.weight(1f)) {
                        HomeScreen(
                            encounters = bleViewModel.encounters,
                            onSelectProfile = { user ->
                                navController.navigate("profile/$user")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pulsante per simulare encounter
                    Button(
                        onClick = { simulateEncounter(bleViewModel) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simula encounter 🚀")
                    }
                }
            }

            composable("profile/{userName}") { backStackEntry ->
                val user = backStackEntry.arguments?.getString("userName") ?: return@composable
                ProfileScreen(userName = user, onStartChat = {
                    chatViewModel.startChat(it)
                    navController.navigate("chat/$it")
                })
            }
            composable("chats") {
                ChatsScreen(
                    chats = chatViewModel.getActiveChats(),
                    onSelectChat = { user ->
                        navController.navigate("chat/$user")
                    }
                )
            }
            composable("chat/{userName}") { backStackEntry ->
                val user = backStackEntry.arguments?.getString("userName") ?: return@composable
                ChatScreen(userName = user, chatViewModel = chatViewModel)
            }
        }
    }
}

// 🔹 Simulazione encounter
fun simulateEncounter(bleViewModel: BleViewModel) {
    val fakeUsers = listOf("Alice", "Bob", "Charlie")
    fakeUsers.shuffled().take(1).forEach { name ->
        bleViewModel.addEncounter(name)
    }
}
