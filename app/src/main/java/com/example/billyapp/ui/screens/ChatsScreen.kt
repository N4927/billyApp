package com.example.billyapp.ui.screens

import com.example.billyapp.core.Chat


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatsScreen(chats: List<Chat>, onSelectChat: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chats", style = MaterialTheme.typography.headlineSmall)

        LazyColumn {
            items(chats) { chat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onSelectChat(chat.userName) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(chat.userName, style = MaterialTheme.typography.bodyLarge)
                        if (chat.messages.isNotEmpty()) {
                            Text(
                                chat.messages.last(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
