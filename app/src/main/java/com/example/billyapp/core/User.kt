package com.example.billyapp.core

/**
 * Modello profilo locale usato dallo screen di setup.
 * In futuro potrai collegarlo al KMM / backend, ma per ora basta così.
 */
data class User(
    val id: String,
    val name: String,
    val age: Int? = null,
    val bio: String? = null
)
