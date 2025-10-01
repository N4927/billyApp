package com.example.billyapp.core


import androidx.compose.runtime.mutableStateListOf


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


data class Chat(
    val userName: String,
    val messages: MutableList<String> = mutableStateListOf()
)
