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
import com.example.shared.User
import java.util.UUID
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.TextFieldDefaults


@Composable
fun ProfileSetupScreen(
    user: User?,
    onSave: (User) -> Unit
) {
    var name by remember { mutableStateOf(user?.name ?: "") } // ✅ Fixed: was displayName
    var age by remember { mutableStateOf(user?.age?.toString() ?: "") }
    var bio by remember { mutableStateOf(user?.bio ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9)) // bianco caldo
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // 🔹 Titolo minimal
        Text(
            text = "Create your profile",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF000000)
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        // 🔹 Campo nome
        MinimalInputField(
            value = name,
            onValueChange = { name = it },
            label = "Name"
        )

        Spacer(Modifier.height(20.dp))

        // 🔹 Campo età
        MinimalInputField(
            value = age,
            onValueChange = { age = it },
            label = "Age"
        )

        Spacer(Modifier.height(20.dp))

        // 🔹 Campo bio
        MinimalInputField(
            value = bio,
            onValueChange = { bio = it },
            label = "Bio",
            singleLine = false,
            height = 100.dp
        )

        Spacer(Modifier.height(40.dp))

        // 🔹 Pulsante Salva minimal
        Button(
            onClick = {
                val userIds = mapOf(
                    "alice" to "ecb73c72d94f1a23",
                    "bob" to "33f24d0c2ab9e78f",
                    "charlie" to "bcb375489ee42a6d",
                    "luca" to "90895e9d21cb8fa6",
                    "marco" to "8b44a291c7e3d0af",
                    "giulia" to "f8507096b1c3e79d"
                )

                val cleanName = name.trim().lowercase()
                val assignedId = userIds[cleanName] ?: UUID.randomUUID().toString().take(16) // ✅ Limit ID length

                val newUser = User(
                    id = assignedId,
                    name = name.trim(), // ✅ Fixed: was displayName
                    age = age.toIntOrNull(),
                    bio = bio.trim().ifEmpty { null } // ✅ Make bio optional
                )
                onSave(newUser)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF000000),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "Save Profile",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 🔹 Info about the new architecture
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your profile will use server-side encrypted identifiers",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.Gray
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

/**
 * Campo di input minimalista in bianco e nero
 */
@Composable
fun MinimalInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    height: Dp = 56.dp
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF5C5C5C),
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = LocalTextStyle.current.copy(
                    color = Color(0xFF1A1A1A),
                    fontSize = 16.sp
                ),
                singleLine = singleLine,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.Black
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}