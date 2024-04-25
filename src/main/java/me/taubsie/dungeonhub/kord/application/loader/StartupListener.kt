package me.taubsie.dungeonhub.kord.application.loader

interface StartupListener {
    suspend fun preStart() {
    }

    suspend fun onStart() {
    }

    suspend fun postStart() {
    }
}