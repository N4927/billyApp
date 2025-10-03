package com.example.billyapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billyapp.core.ChatViewModel

@Composable
fun ChatScreen(userName: String, chatViewModel: ChatViewModel) {
    val chat = chatViewModel.getChat(userName) ?: return
    var message by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chat with $userName", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(8.dp))

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            items(chat.messages) { msg ->
                Text(msg, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Input + Send button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") }
            )
            Button(onClick = {
                if (message.isNotBlank()) {
                    chatViewModel.sendMessage(userName, message)
                    message = "" // reset input
                }
            }) {
                Text("Send")
            }
        }
    }
}
