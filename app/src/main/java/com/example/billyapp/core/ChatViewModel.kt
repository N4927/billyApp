package com.example.billyapp.core

import androidx.lifecycle.ViewModel

class ChatViewModel : ViewModel() {

    // Mappa che contiene tutte le chat attive (userName -> Chat)
    private val chats = mutableMapOf<String, Chat>()

    // Avvia una nuova chat se non esiste già
    fun startChat(userName: String) {
        if (!chats.containsKey(userName)) {
            chats[userName] = Chat(userName)
        }
    }

    // Restituisce tutte le chat attive come lista
    fun getActiveChats(): List<Chat> = chats.values.toList()

    // Invia un messaggio a un utente specifico
    fun sendMessage(userName: String, message: String) {
        chats[userName]?.messages?.add(message)
    }

    // Restituisce la chat di un utente specifico (o null se non esiste)
    fun getChat(userName: String): Chat? {
        return chats[userName]
    }
}