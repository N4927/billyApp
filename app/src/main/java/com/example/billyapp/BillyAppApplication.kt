package com.example.billyapp

import android.app.Application
import com.billyapp.shared.BillySDK  // ← Dal KMM!
import com.example.billyapp.kmm.AndroidTokenStorage  // ← Tuoi

class BillyAppApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Crea storage (TUO)
        val tokenStorage = AndroidTokenStorage(this)
        
        // 2. Inizializza SDK (KMM fa tutto!)
        BillySDK.initialize(
            context = this,
            storage = tokenStorage
        )
    }
}