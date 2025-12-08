package com.example.billyapp.core

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel semplice / fake per la chat.
 *
 * - Tiene le chat solo in memoria (niente salvataggio, niente server).
 * - Serve solo per far funzionare ChatScreen, ChatsScreen e ProfileScreen
 *   senza errori di compilazione.
 */
class ChatViewModel : ViewModel() {

    // Tutte le chat aperte nella sessione
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    /**
     * Trova una chat con un certo utente, se esiste.
     * Usato da ProfileScreen e ChatScreen.
     */
    fun getChat(withUser: String): Chat? =
        _chats.value.find { it.withUser == withUser }

    /**
     * Crea una chat con l’utente se non esiste già.
     * Non fa altro (nessun networking).
     */
    fun startChat(withUser: String) {
        val existing = getChat(withUser)
        if (existing != null) return

        val newChat = Chat(withUser = withUser)
        _chats.update { it + newChat }
    }

    /**
     * Aggiunge un messaggio alla chat con [withUser].
     * Se la chat non esiste ancora, la crea.
     *
     * Per ora segniamo tutti i messaggi come "da me" (from = "me"),
     * giusto per avere qualcosa da mostrare nella UI.
     */
    fun sendMessage(withUser: String, text: String) {
        if (text.isBlank()) return

        val message = Message(from = "me", text = text.trim())

        val currentChats = _chats.value.toMutableList()
        val index = currentChats.indexOfFirst { it.withUser == withUser }

        if (index >= 0) {
            val chat = currentChats[index]
            val updatedMessages = chat.messages.toMutableList()
            updatedMessages.add(message)
            currentChats[index] = chat.copy(messages = updatedMessages)
        } else {
            val newChat = Chat(withUser = withUser, messages = mutableListOf(message))
            currentChats.add(newChat)
        }

        _chats.value = currentChats
    }
}
