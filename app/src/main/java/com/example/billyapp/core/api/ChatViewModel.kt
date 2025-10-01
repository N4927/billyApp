package com.example.billyapp.core.api

import androidx.lifecycle.ViewModel
import com.example.billyapp.core.Chat

class ChatViewModel : ViewModel() {
    private val chats = mutableMapOf<String, Chat>()

    fun startChat(userName: String) {
        if (!chats.containsKey(userName)) {
            chats[userName] = Chat(userName)
        }
    }

    fun getChat(userName: String): Chat? = chats[userName]

    fun getActiveChats(): List<Chat> = chats.values.toList()

    fun sendMessage(userName: String, message: String) {
        chats[userName]?.messages?.add(message)
    }
}
