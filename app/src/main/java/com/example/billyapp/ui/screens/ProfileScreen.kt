package com.example.billyapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billyapp.core.ChatViewModel

@Composable
fun ProfileScreen(
    userName: String,
    chatViewModel: ChatViewModel,
    onGoToChat: (String) -> Unit
) {
    val existingChat = chatViewModel.getChat(userName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "👤 $userName", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (existingChat == null) {
            // 🔹 Se non esiste la chat → mostra "Start Chat"
            Button(onClick = {
                chatViewModel.startChat(userName)
                onGoToChat(userName)
            }) {
                Text("Start New Chat 💬")
            }
        } else {
            // 🔹 Se la chat esiste → mostra "Go to Chat"
            Button(onClick = { onGoToChat(userName) }) {
                Text("Go to Chat 🚀")
            }
        }
    }
}
