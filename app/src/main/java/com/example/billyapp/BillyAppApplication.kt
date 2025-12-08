package com.example.billyapp

import android.app.Application
import com.example.billyapp.kmm.KmmEnvironment

class BillyAppApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inizializza il core KMM una volta sola all’avvio dell’app.
        // Se preferisci, puoi rimuoverla e lasciare init lazy sul primo accesso.
        KmmEnvironment.init(this)
    }
}
