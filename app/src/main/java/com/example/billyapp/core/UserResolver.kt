package com.example.billyapp.core

object UserResolver {

    // dizionario statico: UUID/hash → Nome
    private val knownUsers = mapOf(
        "secret-alice" to "Alice",
        "secret-bob" to "Bob",
        "secret-charlie" to "Charlie"
    )

    // Risolve un ID in un nome
    fun resolveName(idHex: String): String? {
        return knownUsers[idHex]
    }
}
