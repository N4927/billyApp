package com.example.billyapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billyapp.core.ChatViewModel
import com.example.billyapp.ble.BleViewModel

@Composable
fun ProfileScreen(
    userName: String,
    chatViewModel: ChatViewModel,
    bleViewModel: BleViewModel,
    onGoToChat: (String) -> Unit
) {
    val existingChat = chatViewModel.getChat(userName)

    // ✅ Ottieni il numero di volte che hai incontrato la persona
    val encounter = bleViewModel.resolvedEncounters.find { it.name == userName }
    val encounterCount = encounter?.count ?: 0

    // 🔹 Palette minimal
    val background = Color(0xFFF8F8F8)
    val textPrimary = Color(0xFF111111)
    val accent = Color(0xFF000000)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 🔹 Avatar iniziale
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFE5E5E5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🔹 Nome
            Text(
                text = userName,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = textPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 🔹 Mostra quante volte lo hai incontrato
            Text(
                text = when {
                    encounterCount > 1 -> "👀 Hai incontrato $userName $encounterCount volte nelle vicinanze"
                    encounterCount == 1 -> "👀 Hai incontrato $userName una volta nelle vicinanze"
                    else -> "Nessun incontro recente"
                },
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.Gray),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 🔹 Pulsante chat
            Button(
                onClick = {
                    if (existingChat == null) {
                        chatViewModel.startChat(userName)
                    }
                    onGoToChat(userName)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = if (existingChat == null) "Avvia chat 💬" else "Apri chat 💭",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
