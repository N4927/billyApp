package com.example.shared

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,           // 8-byte hex string
    val name: String,         // Display name - ✅ Fixed: was displayName in some places
    val age: Int?,            // Optional - ✅ Made nullable
    val bio: String?          // Optional - ✅ Made nullable
)