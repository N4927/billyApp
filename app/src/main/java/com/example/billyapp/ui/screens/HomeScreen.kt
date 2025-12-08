package com.example.billyapp.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billyapp.core.UserManager
import com.example.billyapp.proximity.ProximityViewModel

@Composable
fun HomeScreen(
    encounters: List<ProximityViewModel.ProximityEncounterUiModel>,
    onSelectProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onSimulateEncounter: (() -> Unit)? = null,
    onStartBle: (() -> Unit)? = null,
    onStopBle: (() -> Unit)? = null,
    onTestBatchSystem: () -> Unit,
    userStatus: String,
) {
    val context = LocalContext.current
    val userManager = remember { UserManager(context) }

    // inizializza con lo stato salvato
    var isOnline by remember { mutableStateOf(userManager.isBleOnline()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        // Header
        Text(
            text = "People nearby",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 8.dp),
            color = Color.Black
        )

        // Online / Offline toggle
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            ToggleButton(
                text = "online",
                isSelected = isOnline,
                onClick = {
                    if (!isOnline) {
                        isOnline = true
                        userManager.setBleOnline(true)
                        onStartBle?.invoke()
                    }
                }
            )
            ToggleButton(
                text = "offline",
                isSelected = !isOnline,
                onClick = {
                    if (isOnline) {
                        isOnline = false
                        userManager.setBleOnline(false)
                        onStopBle?.invoke()
                    }
                }
            )
        }

        // People list
        if (encounters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No one nearby yet",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.Gray),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = encounters,
                    key = { it.id }   // 🔹 prima usavi idHex, ora usiamo id
                ) { encounter ->
                    PersonCard(
                        encounter = encounter,
                        onOpenProfile = onSelectProfile,
                        onOpenChat = onOpenChat
                    )
                }
            }
        }

        // Simulate Encounter button (se lasci la feature)
        if (onSimulateEncounter != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSimulateEncounter.invoke() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("+ Simulate Encounter", color = Color.White)
            }
        }
    }

    // LOG: controlla quando la UI si aggiorna
    LaunchedEffect(encounters.size, isOnline) {
        Log.d("HomeScreen", "🖥️ UI refreshed: ${encounters.size} encounters, online=$isOnline")
    }
}

@Composable
fun ToggleButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(40.dp),
        colors = if (isSelected)
            ButtonDefaults.buttonColors(containerColor = Color.Black)
        else
            ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F3F3)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PersonCard(
    encounter: ProximityViewModel.ProximityEncounterUiModel,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit
) {
    val displayName = encounter.title ?: encounter.id

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProfile(displayName) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAEAEA)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Text(
                    text = "near you",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        Button(
            onClick = { onOpenChat(displayName) },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("chat", color = Color.White, fontSize = 14.sp)
        }
    }
}
