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

@Composable
fun ProfileScreen(
    userName: String,
    chatViewModel: ChatViewModel,
    onGoToChat: (String) -> Unit
) {
    val existingChat = chatViewModel.getChat(userName)

    // 🔹 Palette minimal: bianco / nero / grigio neutro
    val background = Color(0xFFF8F8F8)
    val textPrimary = Color(0xFF111111)
    val textSecondary = Color(0xFF6C6C6C)
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

            // 🔹 Info aggiuntive statiche o di esempio
            Text(
                text = "Recently met user",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = textSecondary
                ),
                textAlign = TextAlign.Center
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
                    text = if (existingChat == null) "Start Chat 💬" else "Open Chat 💭",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
