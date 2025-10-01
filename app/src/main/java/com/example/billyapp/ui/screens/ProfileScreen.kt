package com.example.billyapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    userName: String,
    onStartChat: (String) -> Unit          // callback per creare la chat
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Profile of $userName", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = { onStartChat(userName) }) {   // 👈 qui parte la chat
            Text("Start Chat with $userName")
        }
    }
}
