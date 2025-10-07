package com.example.billyapp.core

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class ChatViewModel : ViewModel() {

    // Stato osservabile di tutte le chat
    private val chats = mutableStateListOf<Chat>()

    fun startChat(userName: String) {
        if (chats.none { it.userName == userName }) {
            chats.add(Chat(userName))
        }
    }

    fun getActiveChats(): List<Chat> = chats

    fun sendMessage(userName: String, message: String) {
        chats.find { it.userName == userName }?.messages?.add(ChatMessage(message, isIncoming = false))
    }

    fun receiveMessage(userName: String, message: String) {
        chats.find { it.userName == userName }?.messages?.add(ChatMessage(message, isIncoming = true))
    }

    fun getChat(userName: String): Chat? = chats.find { it.userName == userName }
}

data class Chat(
    val userName: String,
    val messages: MutableList<ChatMessage> = mutableStateListOf()
)
