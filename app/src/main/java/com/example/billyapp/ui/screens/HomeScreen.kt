package com.example.billyapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Person
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
import com.example.billyapp.core.ResolvedEncounter

@Composable
fun HomeScreen(
    encounters: List<ResolvedEncounter>,
    onSelectProfile: (String) -> Unit,
    onSimulateEncounter: (() -> Unit)? = null,
    onStartBle: (() -> Unit)? = null,
    onStopBle: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        // 🔹 BLE buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStartBle?.invoke() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6EBFF))
            ) {
                Text("Start BLE", color = Color(0xFF4A6FFF))
            }

            Button(
                onClick = { onStopBle?.invoke() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6FFF))
            ) {
                Text("Stop BLE", color = Color.White)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Nearby Encounters",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Spacer(Modifier.height(12.dp))

        // 🔹 List of encounters
        if (encounters.isEmpty()) {
            Spacer(Modifier.height(60.dp))
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFFB0B4C0),
                modifier = Modifier.size(60.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No one nearby yet",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF555555)
            )
            Text(
                text = "Keep the app open to discover people around you.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(encounters) { encounter ->
                    EncounterCard(encounter, onSelectProfile)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 🔹 Simulate button
        Button(
            onClick = { onSimulateEncounter?.invoke() },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6FFF))
        ) {
            Text("+ Simulate Encounter", color = Color.White)
        }
    }
}

@Composable
fun EncounterCard(encounter: ResolvedEncounter, onSelectProfile: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectProfile(encounter.name) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 🔹 Avatar circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDADDE6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = encounter.name.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(encounter.name, fontWeight = FontWeight.Bold)
                    Text("Seen ${encounter.count} ${if (encounter.count == 1) "time" else "times"}", color = Color.Gray, fontSize = 13.sp)
                }
            }

            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = Color(0xFF4A6FFF)
            )
        }
    }
}
