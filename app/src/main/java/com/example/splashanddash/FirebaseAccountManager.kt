package com.example.splashanddash

object FirebaseAccountManager {
    fun signOut() {
        AppData.currentUser = "Guest"
        AppData.role = "Customer"
    }
}
