package com.example.shared

import kotlinx.serialization.Serializable

@Serializable
data class Encounter(
    val idHex: String,
    val rssi: Int,
    val timestampSec: Long,
    val resolvedName: String? = null
)

data class ResolvedEncounter(
    val name: String,
    val count: Int = 1,
    val idHex: String
)