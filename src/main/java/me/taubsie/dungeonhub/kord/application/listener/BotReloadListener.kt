package me.taubsie.dungeonhub.kord.application.listener

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.event.gateway.ResumedEvent
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension

@LoadExtension
class BotReloadListener : Extension() {
    override val name = "bot-reload-listener"

    override suspend fun setup() {
        event<ResumedEvent> {
            action {
                DiscordConnection.resetBotAppearance()
            }
        }
    }
}