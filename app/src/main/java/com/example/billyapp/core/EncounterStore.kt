package com.example.billyapp.core

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object EncounterStore {
    private const val FILE_NAME = "encounters.json"

    fun loadAll(context: Context): MutableList<Encounter> {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return mutableListOf()
        return try {
            val txt = f.readText()
            Json.Default.decodeFromString<List<Encounter>>(txt).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(context: Context, list: List<Encounter>) {
        val f = File(context.filesDir, FILE_NAME)
        try {
            f.writeText(Json.Default.encodeToString(list))
        } catch (_: Exception) {}
    }

    fun append(context: Context, item: Encounter) {
        val list = loadAll(context)
        list.add(item)
        saveAll(context, list)
    }
}