package com.example.billyapp.core

data class Encounter(
    val idHex: String,
    val rssi: Int,
    val timestampSec: Long,
    val resolvedName: String? = null
)
