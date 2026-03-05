package com.example.billyapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.billyapp.core.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }

    // Osserva successo
    LaunchedEffect(uiState) {
        if (uiState is AuthViewModel.AuthUiState.Success) {
            onAuthSuccess()
            viewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Billy",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isRegister) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        // Errore
        (uiState as? AuthViewModel.AuthUiState.Error)?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isRegister) {
                    viewModel.register(username, email, password)
                } else {
                    viewModel.login(email, password)
                }
            },
            enabled = uiState !is AuthViewModel.AuthUiState.Loading &&
                    email.isNotBlank() && password.isNotBlank() &&
                    (!isRegister || username.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState is AuthViewModel.AuthUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (isRegister) "Registrati" else "Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isRegister = !isRegister }) {
            Text(
                if (isRegister) "Hai già un account? Login"
                else "Non hai un account? Registrati"
            )
        }
    }
}