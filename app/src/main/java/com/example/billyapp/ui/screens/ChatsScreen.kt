package com.example.billyapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billyapp.core.Chat
import com.example.billyapp.core.ChatViewModel

@Composable
fun ChatsScreen(
    chatViewModel: ChatViewModel,
    onSelectChat: (String) -> Unit
) {
    val chats by chatViewModel.chats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Chats",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (chats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chats yet",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chats, key = { it.withUser }) { chat ->
                    ChatRow(
                        chat = chat,
                        onClick = {
                            // 👇 QUI usiamo il nome dell’utente preso dalla chat
                            onSelectChat(chat.withUser)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    onClick: () -> Unit
) {
    val lastMessage = chat.messages.lastOrNull()?.text ?: ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Iniziale dell'utente
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.withUser.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chat.withUser,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                if (lastMessage.isNotBlank()) {
                    Text(
                        text = lastMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
