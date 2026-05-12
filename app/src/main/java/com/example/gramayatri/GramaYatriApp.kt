package com.example.gramayatri

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class GramaYatriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Infrastructure Reliability: Enable offline persistence for 2G/3G compatibility
        // This ensures the app remains functional in rural areas with spotty connectivity.
        FirebaseDatabase.getInstance("https://gramayatri-d641b-default-rtdb.europe-west1.firebasedatabase.app/").setPersistenceEnabled(true)
    }
}
