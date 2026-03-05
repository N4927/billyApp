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
import com.example.billyapp.core.AuthViewModel
import com.example.billyapp.kmm.AndroidTokenStorage

class MainActivity : ComponentActivity() {

    private val proximityViewModel: ProximityViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var userManager: UserManager

    private var appInitialized = false

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.entries.all { it.value }
        if (allGranted) {
            initializeApp()
        } else {
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

        // ✅ NUOVO: Controlla login prima dei permessi
        if (isLoggedIn()) {
            // Già loggato, procedi con permessi
            requestAllPermissions()
        } else {
            // Non loggato, mostra login
            setLoginContent()
        }
    }

    // ✅ NUOVO: Funzione helper
    private fun isLoggedIn(): Boolean {
        return AndroidTokenStorage(this).getAccessToken() != null
    }

    // ✅ NUOVO: Mostra login screen
    private fun setLoginContent() {
        setContent {
            BillyAppTheme {
                LoginScreen(
                    viewModel = authViewModel,
                    onAuthSuccess = {
                        // Dopo login OK, richiedi permessi
                        requestAllPermissions()
                    }
                )
            }
        }
    }

    // Modifica initializeApp() - rimuovi doppia inizializzazione
    private fun initializeApp() {
        if (appInitialized) return
        appInitialized = true

        // ✅ Rimuovi initializeBatchSystem() - lo fa KMM

        chatViewModel.startChat("Helena Hills")
        setMainContent()
        startBleServices()

        Log.d("MainActivity", "✅ App initialized")
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
                        startBleServices()
                    },
                    onStopService = { stopBleServices() },
                    onTestBatchSystem = { testBatchSystem(context) },
                    onClearAllData = {
                        stopBleServices()
                        UserManager(context).clearAllUserData()
                        Log.i("MainActivity", "🧹 Cleared all data")
                        restartActivity()
                    }
                )
            }
        }
    }

    private fun setBasicContent() {
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
            userManager.refreshBatch()
            Log.d("MainActivity", "🔄 Initialized batch system for user: ${user.name}")
        } else {
            Log.d("MainActivity", "ℹ️ No user profile set up yet")
        }
    }

    // ============================================================
    // PERMESSI - VERSIONE SENZA LOCATION (NO GPS!)
    // ============================================================

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+): SOLO permessi BLE specifici
            // NEVER location grazie al flag neverForLocation nel manifest
            permissions += android.Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += android.Manifest.permission.BLUETOOTH_SCAN
            permissions += android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // Android < 12: permessi legacy BLE
            // NO BLUETOOTH_PRIVILEGED, NO LOCATION!
            permissions += android.Manifest.permission.BLUETOOTH
            permissions += android.Manifest.permission.BLUETOOTH_ADMIN
        }

        // Notifiche per foreground service (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
        }

        // Verifica se abbiamo già tutti i permessi
        val hasAllPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (hasAllPermissions) {
            Log.d("MainActivity", "✅ All permissions already granted")
            initializeApp()
        } else {
            Log.d("MainActivity", "🔵 Requesting permissions: $permissions")
            reqPerms.launch(permissions.toTypedArray())
        }
    }

    /**
     * NUOVA VERSIONE: Senza nessun riferimento a location!
     * Solo permessi BLE puri.
     */
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: BLUETOOTH_SCAN con neverForLocation nel manifest
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android < 12: SOLO BLUETOOTH classico
            // MAI ACCESS_FINE_LOCATION o ACCESS_COARSE_LOCATION!
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ============================================================
    // BLE SERVICES
    // ============================================================

    private fun startBleServices() {
        if (hasBluetoothPermissions()) {
            val peripheralIntent = Intent(this, BluetoothPeripheralService::class.java)
            val centralIntent = Intent(this, BluetoothCentralService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, peripheralIntent)
                ContextCompat.startForegroundService(this, centralIntent)
            } else {
                startService(peripheralIntent)
                startService(centralIntent)
            }

            userManager.setBleOnline(true)
            Log.d("MainActivity", "🚀 Started BLE Peripheral & Central Services")
        } else {
            Log.w("MainActivity", "⚠️ Cannot start BLE services - permissions missing")
            requestAllPermissions()
        }
    }

    private fun stopBleServices() {
        stopService(Intent(this, BluetoothPeripheralService::class.java))
        stopService(Intent(this, BluetoothCentralService::class.java))

        userManager.setBleOnline(false)
        Log.d("MainActivity", "🛑 Stopped BLE Services")
    }

    private fun restartActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}

// ============================================================
// UI COMPOSE (invariata)
// ============================================================

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