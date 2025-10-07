package com.example.billyapp.core

import kotlinx.serialization.Serializable
import androidx.compose.runtime.mutableStateListOf
@OptIn(kotlinx.serialization.InternalSerializationApi::class)


@Serializable
data class Encounter(
    val idHex: String,
    val rssi: Int,
    val timestampSec: Long,
    val resolvedName: String? = null
)

data class ResolvedEncounter(
    val name: String,
    val count: Int = 1
)


