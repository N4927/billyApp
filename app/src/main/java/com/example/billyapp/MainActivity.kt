package com.example.billyapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.billyapp.ble.BleService
import com.example.billyapp.ble.BleViewModel
import com.example.billyapp.core.ChatViewModel
import com.example.billyapp.core.UserManager
import com.example.billyapp.ui.screens.*
import com.example.billyapp.ui.theme.BillyAppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
        chatViewModel.startChat("Helena Hills")

        setContent {
            BillyAppTheme {
                val navController = rememberNavController()
                BillyApp(
                    navController = navController,
                    bleViewModel = bleViewModel,
                    chatViewModel = chatViewModel,
                    onStartService = { requestAllPermissions() },
                    onStopService = { stopService(Intent(this, BleService::class.java)) }
                )
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissions += android.Manifest.permission.POST_NOTIFICATIONS

        reqPerms.launch(permissions.toTypedArray())
    }

    private fun startBleService() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(this, BleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ContextCompat.startForegroundService(this, intent)
            else
                startService(intent)
        } else {
            requestAllPermissions()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillyApp(
    navController: NavHostController,
    bleViewModel: BleViewModel,
    chatViewModel: ChatViewModel,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != null && !currentRoute.startsWith("chat/")) {
                BottomBarMinimal(
                    currentRoute = currentRoute,
                    onNavigateHome = { navController.navigate("home") },
                    onNavigateChats = { navController.navigate("chats") },
                    onNavigateProfile = { navController.navigate("myprofile") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            // 🏠 Home (reattiva)
            composable("home") {
                HomeScreen(
                    encounters = bleViewModel.resolvedEncounters, // ✅ lista aggiornata in tempo reale
                    onSelectProfile = { user -> navController.navigate("profile/$user") },
                    onOpenChat = { userName ->
                        chatViewModel.startChat(userName)
                        navController.navigate("chat/$userName")
                    },
                    onSimulateEncounter = { simulateEncounter(bleViewModel) },
                    onStartBle = onStartService,
                    onStopBle = onStopService
                )
            }

            // 💬 Lista chat
            composable("chats") {
                ChatsScreen(
                    chats = chatViewModel.getActiveChats(),
                    onSelectChat = { userName ->
                        chatViewModel.startChat(userName)
                        navController.navigate("chat/$userName")
                    }
                )
            }

            // 🗨️ Chat singola
            composable("chat/{userName}") { backStackEntry ->
                val userName = backStackEntry.arguments?.getString("userName") ?: return@composable
                ChatScreen(
                    userName = userName,
                    chatViewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // 👤 Profilo utente
            composable("profile/{userName}") { backStackEntry ->
                val userName = backStackEntry.arguments?.getString("userName") ?: return@composable
                ProfileScreen(
                    userName = userName,
                    chatViewModel = chatViewModel,
                    bleViewModel = bleViewModel,
                    onGoToChat = { navController.navigate("chat/$it") }
                )
            }


            // ⚙️ Setup profilo
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

            // 👤 Mio profilo
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
                        onEditProfile = { navController.navigate("profile/setup") },
                        onNavigateHome = { navController.navigate("home") },
                        onNavigateChats = { navController.navigate("chats") },
                        onNavigateProfile = { navController.navigate("myprofile") }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomBarMinimal(
    currentRoute: String?,
    onNavigateHome: () -> Unit,
    onNavigateChats: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateHome) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    tint = if (currentRoute == "home") Color.Black else Color.Gray
                )
            }
            IconButton(onClick = onNavigateChats) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Chats",
                    tint = if (currentRoute == "chats") Color.Black else Color.Gray
                )
            }
            IconButton(onClick = onNavigateProfile) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = if (currentRoute == "myprofile") Color.Black else Color.Gray
                )
            }
        }
    }
}

// 🧩 Simulazione BLE
fun simulateEncounter(bleViewModel: BleViewModel) {
    val fakeUsers = listOf("Alice", "Bob", "Charlie", "Diana", "Luca")
    fakeUsers.shuffled().take(1).forEach { name -> bleViewModel.addEncounter(name) }
}



