package com.example.billyapp.proximity

/**
 * Modello usato dalla UI per mostrare le persone vicine.
 * La logica di risoluzione vera (da B_id -> profilo) sta nel KMM,
 * ma qui abbiamo un "DTO" semplice per gli screen Android.
 */
data class ResolvedEncounter(
    val idHex: String,     // identificatore del BID / encounter
    val name: String,      // nome da mostrare nella UI
    val count: Int = 1     // quante volte li hai incontrati (usato in ProfileScreen)
)
