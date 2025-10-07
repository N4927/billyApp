package com.example.billyapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.billyapp.core.User
import java.util.UUID

@Composable
fun ProfileSetupScreen(
    user: User?,
    onSave: (User) -> Unit
) {
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var age by remember { mutableStateOf(user?.age?.toString() ?: "") }
    var bio by remember { mutableStateOf(user?.bio ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 🔹 Title
        Text(
            text = "Set up your profile",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // 🔹 Name field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // 🔹 Age field
        OutlinedTextField(
            value = age,
            onValueChange = { age = it },
            label = { Text("Age") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // 🔹 Bio field
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Bio") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(32.dp))

        // 🔹 Save button
        // 🔹 Save button
        Button(
            onClick = {
                // Mappa fissa di ID personali coerente con la parte BLE/Python
                // ID coerenti con FakeServer (solo 8 caratteri)
                val userIds = mapOf(
                    "alice" to "ecb73c72",
                    "bob" to "33f24d0c",
                    "charlie" to "bcb37548",
                    "luca" to "90895e9d",
                    "marco" to "8b44a291",
                    "giulia" to "f8507096"
                )


                val cleanName = name.trim().lowercase()
                val assignedId = userIds[cleanName] ?: UUID.randomUUID().toString()

                val newUser = User(
                    id = assignedId,
                    displayName = name.trim(),
                    age = age.toIntOrNull(),
                    bio = bio.trim()
                )

                onSave(newUser)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6FFF))
        ) {
            Text("Save Profile", color = Color.White, fontSize = 16.sp)
        }

    }
}
