package com.example.billyapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.billyapp.ble.BleService
import com.example.billyapp.ble.BleViewModel
import com.example.billyapp.core.ChatViewModel
import com.example.billyapp.core.ResolvedEncounter
import com.example.billyapp.core.UserManager
import com.example.billyapp.ui.screens.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Chat




class MainActivity : ComponentActivity() {
    private val bleViewModel: BleViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.entries.all { it.value }
        if (allGranted) startBleService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        testCryptoServerMatch()

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions += android.Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
        }

        reqPerms.launch(permissions.toTypedArray())
    }

    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ContextCompat.startForegroundService(this, intent)
        else
            startService(intent)
    }
}
//ciao
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
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("myprofile") },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            /* -------------------- 🏠 HOME -------------------- */
            composable("home") {
                HomeScreen(
                    encounters = bleViewModel.encounters.map {
                        ResolvedEncounter(
                            name = it.encounter.resolvedName ?: "Unknown",

                            count = it.count
                        )
                    },
                    onSelectProfile = { user ->
                        navController.navigate("profile/$user")
                    },
                    onSimulateEncounter = { simulateEncounter(bleViewModel) },
                    onStartBle = onStartService,
                    onStopBle = onStopService
                )
            }

            /* -------------------- 💬 CHATS -------------------- */
            composable("chats") {
                ChatsScreen(
                    chats = chatViewModel.getActiveChats(),
                    onSelectChat = { user ->
                        navController.navigate("chat/$user")
                    }
                )
            }

            /* -------------------- 🗣️ CHAT DETAIL -------------------- */
            composable("chat/{userName}") { backStackEntry ->
                val user = backStackEntry.arguments?.getString("userName") ?: return@composable
                ChatScreen(userName = user, chatViewModel = chatViewModel)
            }

            /* -------------------- 👥 OTHER PROFILE -------------------- */
            composable("profile/{userName}") { backStackEntry ->
                val user = backStackEntry.arguments?.getString("userName") ?: return@composable
                ProfileScreen(
                    userName = user,
                    chatViewModel = chatViewModel,
                    onGoToChat = { navController.navigate("chat/$it") }
                )
            }

            /* -------------------- 👤 MY PROFILE -------------------- */
            composable("myprofile") {
                val context = LocalContext.current
                val userManager = remember { UserManager(context) }
                var currentUser by remember { mutableStateOf(userManager.getUser()) }

                if (currentUser == null) {
                    ProfileSetupScreen(
                        user = null,
                        onSave = { newUser ->
                            userManager.saveUser(newUser)
                            currentUser = newUser
                        }
                    )
                } else {
                    MyProfileScreen(
                        userName = currentUser!!.displayName,
                        age = currentUser!!.age ?: 0,
                        bio = currentUser!!.bio ?: "No bio yet",
                        onEditProfile = {
                            navController.navigate("profile/setup")
                        }
                    )
                }
            }

            /* -------------------- ⚙️ EDIT PROFILE -------------------- */
            composable("profile/setup") {
                val context = LocalContext.current
                val userManager = remember { UserManager(context) }

                ProfileSetupScreen(
                    user = userManager.getUser(),
                    onSave = { newUser ->
                        userManager.saveUser(newUser)
                        navController.popBackStack("myprofile", inclusive = false)
                    }
                )
            }
        }
    }
}

/* -------------------- ⚙️ SIMULATED BLE -------------------- */
fun simulateEncounter(bleViewModel: BleViewModel) {
    val fakeUsers = listOf("Alice", "Bob", "Charlie", "Diana")
    fakeUsers.shuffled().take(1).forEach { name ->
        bleViewModel.addEncounter(name)
    }
}

private fun testCryptoServerMatch() {
    val timestamp = System.currentTimeMillis() / 1000
    val secret = "alice".toByteArray().copyOf(16)
    val crypto = com.example.billyapp.core.CryptographyManager(secret)

    val payload = crypto.encryptRotatingIdentifier("ecb73c72", timestamp)
    android.util.Log.d("CryptoTest", "Client payload: ${payload.joinToString("") { "%02x".format(it) }}")

    val resolved = com.example.billyapp.core.FakeServer.resolveRotatingId(payload, timestamp)
    android.util.Log.d("CryptoTest", "Resolved user: ${resolved?.displayName ?: "Unknown"}")
}

