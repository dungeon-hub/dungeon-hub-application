package net.dungeonhub.application.loader

import dev.kord.core.event.gateway.ReadyEvent

interface StartupListener {
    /**
     * This method will be executed before all others and before the discord bot is launched.
     * The bot itself is loaded in this method.
     */
    suspend fun preStart() {
    }

    /**
     * This method will be executed after the bot has loaded all classes, but before it actually tries to connect to discord.
     */
    suspend fun onStart() {
    }

    /**
     * This method will be executed after the bot has connected successfully.
     *
     * @see ReadyEvent
     */
    suspend fun postStart() {
    }
}