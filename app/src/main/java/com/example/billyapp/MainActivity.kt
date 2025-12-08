package com.example.billyapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.billyapp.bluetooth.BluetoothCentralService
import com.example.billyapp.bluetooth.BluetoothPeripheralService
import com.example.billyapp.core.ChatViewModel
import com.example.billyapp.core.UserManager
import com.example.billyapp.core.testBatchSystem
import com.example.billyapp.proximity.ProximityViewModel
import com.example.billyapp.ui.screens.*
import com.example.billyapp.ui.theme.BillyAppTheme

class MainActivity : ComponentActivity() {

    private val proximityViewModel: ProximityViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private lateinit var userManager: UserManager

    private var appInitialized = false

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.entries.all { it.value }
        if (allGranted) {
            // ✅ Permissions granted - now initialize the app
            initializeApp()
        } else {
            // ❌ Permissions denied - show error and set basic content
            Toast.makeText(
                this,
                "BLE permissions required for app functionality!",
                Toast.LENGTH_LONG
            ).show()
            setBasicContent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)

        // ✅ Request permissions FIRST, before any app initialization
        requestAllPermissions()
    }

    private fun initializeApp() {
        if (appInitialized) return // Prevent multiple initializations

        appInitialized = true

        // ✅ Initialize batch system (resta com’era – se UserManager lo gestisce ancora)
        initializeBatchSystem()

        // ✅ Start chat
        chatViewModel.startChat("Helena Hills")

        // ✅ Set the main app content
        setMainContent()

        // ✅ Auto-start BLE services (central + peripheral)
        startBleServices()

        Log.d("MainActivity", "✅ App fully initialized with permissions")
    }

    private fun setMainContent() {
        setContent {
            BillyAppTheme {
                val navController = rememberNavController()
                val context = LocalContext.current

                BillyApp(
                    navController = navController,
                    proximityViewModel = proximityViewModel,
                    chatViewModel = chatViewModel,
                    onStartService = {
                        // Just start services - we already have permissions
                        startBleServices()
                    },
                    onStopService = { stopBleServices() },
                    onTestBatchSystem = { testBatchSystem(context) },
                    onClearAllData = {
                        stopBleServices()
                        UserManager(context).clearAllUserData()
                        // se vuoi pulire anche il KMM, puoi aggiungere qui
                        Log.i("MainActivity", "🧹 Cleared all data")
                        // Restart the app to refresh completely
                        restartActivity()
                    }
                )
            }
        }
    }

    private fun setBasicContent() {
        // ✅ Fallback content when permissions are denied
        setContent {
            BillyAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Permissions Required",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.Red
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This app requires Bluetooth permissions to function properly.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { requestAllPermissions() }
                        ) {
                            Text("Grant Permissions")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) {
                            Text("Exit App")
                        }
                    }
                }
            }
        }
    }

    private fun initializeBatchSystem() {
        val user = userManager.getUser()
        if (user != null) {
            // Se UserManager ha ancora refreshBatch(), lo puoi mantenere,
            // altrimenti puoi togliere questa riga.
            userManager.refreshBatch()
            Log.d("MainActivity", "🔄 Initialized batch system for user: ${user.name}")
        } else {
            Log.d("MainActivity", "ℹ️ No user profile set up yet")
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        // ✅ Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += android.Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += android.Manifest.permission.BLUETOOTH_SCAN
            permissions += android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // ✅ Location permissions for Android < 12
            permissions += android.Manifest.permission.ACCESS_FINE_LOCATION
            permissions += android.Manifest.permission.ACCESS_COARSE_LOCATION
        }

        // ✅ Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
        }

        // ✅ Check if we already have all permissions
        val hasAllPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }

        if (hasAllPermissions) {
            Log.d("MainActivity", "✅ All permissions already granted")
            initializeApp()
        } else {
            Log.d("MainActivity", "🔵 Requesting permissions: ${permissions.size} permissions")
            reqPerms.launch(permissions.toTypedArray())
        }
    }

    // ---------------- BLE SERVICES (NUOVI) ----------------

    private fun startBleServices() {
        if (hasBluetoothPermissions()) {
            val centralIntent = Intent(this, BluetoothCentralService::class.java)
            val peripheralIntent = Intent(this, BluetoothPeripheralService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, centralIntent)
                ContextCompat.startForegroundService(this, peripheralIntent)
            } else {
                startService(centralIntent)
                startService(peripheralIntent)
            }

            userManager.setBleOnline(true)
            Log.d("MainActivity", "🚀 Started BLE Central & Peripheral Services")
        } else {
            Log.w("MainActivity", "⚠️ Cannot start BLE services - permissions missing")
            requestAllPermissions()
        }
    }

    private fun stopBleServices() {
        stopService(Intent(this, BluetoothCentralService::class.java))
        stopService(Intent(this, BluetoothPeripheralService::class.java))

        userManager.setBleOnline(false)
        Log.d("MainActivity", "🛑 Stopped BLE Services")
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun restartActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillyApp(
    navController: NavHostController,
    proximityViewModel: ProximityViewModel,
    chatViewModel: ChatViewModel,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onTestBatchSystem: () -> Unit,
    onClearAllData: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // prendiamo lo stato dal nuovo ViewModel di prossimità
    val proximityUiState by proximityViewModel.uiState.collectAsState()

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
            composable("home") {
                val context = LocalContext.current
                val userManager = remember { UserManager(context) }

                HomeScreen(
                    // ⬇⬇ NUOVO: passiamo gli incontri dal ProximityViewModel
                    encounters = proximityUiState.encounters,
                    onSelectProfile = { user -> navController.navigate("profile/$user") },
                    onOpenChat = { userName ->
                        chatViewModel.startChat(userName)
                        navController.navigate("chat/$userName")
                    },
                    onStartBle = onStartService,
                    onStopBle = onStopService,
                    onTestBatchSystem = onTestBatchSystem,
                    userStatus = userManager.getUserStatus(),
                )
            }

            composable("chats") {
                ChatsScreen(
                    chatViewModel = chatViewModel,
                    onSelectChat = { userName ->
                        chatViewModel.startChat(userName)
                        navController.navigate("chat/$userName")
                    }
                )
            }


            composable("chat/{userName}") { backStackEntry ->
                val userName = backStackEntry.arguments?.getString("userName") ?: return@composable
                ChatScreen(
                    userName = userName,
                    chatViewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("profile/{userName}") { backStackEntry ->
                val userName = backStackEntry.arguments?.getString("userName") ?: return@composable
                ProfileScreen(
                    userName = userName,
                    chatViewModel = chatViewModel,
                    // ⬇ se ProfileScreen usava BleViewModel, aggiorna la firma e passa ProximityViewModel
                    proximityViewModel = proximityViewModel,
                    onGoToChat = { navController.navigate("chat/$it") }
                )
            }

            composable("profile/setup") {
                val context = LocalContext.current
                val userManager = remember { UserManager(context) }

                ProfileSetupScreen(
                    user = userManager.getUser(),
                    onSave = { newUser ->
                        userManager.saveUser(newUser)
                        userManager.refreshBatch()
                        navController.popBackStack("myprofile", inclusive = false)
                    }
                )
            }

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
                            userManager.refreshBatch()
                        }
                    )
                } else {
                    MyProfileScreen(
                        userName = currentUser!!.name,
                        age = currentUser!!.age ?: 0,
                        bio = currentUser!!.bio ?: "No bio yet",
                        onEditProfile = { navController.navigate("profile/setup") },
                        onNavigateHome = { navController.navigate("home") },
                        onNavigateChats = { navController.navigate("chats") },
                        onNavigateProfile = { navController.navigate("myprofile") },
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
                    Icons.Filled.Home,
                    contentDescription = "Home",
                    tint = if (currentRoute == "home") Color.Black else Color.Gray
                )
            }
            IconButton(onClick = onNavigateChats) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chats",
                    tint = if (currentRoute == "chats") Color.Black else Color.Gray
                )
            }
            IconButton(onClick = onNavigateProfile) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = if (currentRoute == "myprofile") Color.Black else Color.Gray
                )
            }
        }
    }
}
