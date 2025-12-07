package com.example.billyapp.core

import android.content.Context
import com.example.shared.Encounter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object EncounterStore {
    private const val FILE_NAME = "encounters.json"
    private const val MAX_STORED_ENCOUNTERS = 1000 // Prevent storage bloat

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
            // Limit storage size
            val limitedList = if (list.size > MAX_STORED_ENCOUNTERS) {
                list.sortedByDescending { it.timestampSec }.take(MAX_STORED_ENCOUNTERS)
            } else {
                list
            }
            f.writeText(Json.Default.encodeToString(limitedList))
        } catch (_: Exception) {}
    }

    fun append(context: Context, item: Encounter) {
        val list = loadAll(context)
        list.add(item)
        saveAll(context, list)
    }

    // Clear all stored encounters
    fun clearAll(context: Context) {
        saveAll(context, emptyList())
    }
}