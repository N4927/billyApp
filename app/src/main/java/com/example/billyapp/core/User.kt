package com.example.billyapp.core

import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.InternalSerializationApi::class)

@Serializable
data class User(
    val id: String,                // UUID univoco o assegnato dal server
    var displayName: String,       // Nome visibile
    var age: Int? = null,          // opzionale
    var gender: String? = null,    // opzionale
    var bio: String? = null,       // breve descrizione
    var avatarUrl: String? = null, // immagine profilo
    var interests: List<String>? = null, // tag
    var rotatingId: ByteArray? = null // collegato al BLE
)
