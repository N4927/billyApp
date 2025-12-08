package com.example.billyapp.core

/**
 * Modello locale per una chat.
 * Per ora è solo in memoria, senza backend.
 */
data class Chat(
    val withUser: String,               // username della persona con cui stai chattando
    val messages: MutableList<Message> = mutableListOf()
)

/**
 * Singolo messaggio.
 * Tutto resta solo in RAM finché l’app è aperta.
 */
data class Message(
    val from: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
