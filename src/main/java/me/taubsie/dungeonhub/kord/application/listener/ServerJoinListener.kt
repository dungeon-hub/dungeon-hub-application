package me.taubsie.dungeonhub.kord.application.listener

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.core.event.guild.GuildCreateEvent
import me.taubsie.dungeonhub.application.service.ServerService
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@LoadExtension
class ServerJoinListener : Extension() {
    val logger: Logger = LoggerFactory.getLogger(ServerJoinListener::class.java)

    override val name = "server-join-listener"
    override suspend fun setup() {
        event<GuildCreateEvent> {
            action {
                if (GUILD_ON_JOIN.contains(event.guild.id.value)) {
                    return@action
                }

                DiscordConnection.resetBotAppearance()

                val ownerName = event.guild.getOwnerOrNull()?.effectiveName ?: "no-name"

                logger.info(
                    "I just joined server '{}' by '{}' ({}).",
                    event.guild.name,
                    ownerName,
                    event.guild.ownerId
                )

                ApplicationService.getBotOwner(kord)?.dm(
                    "I just joined server `${event.guild.name}` by $ownerName (<@${event.guild.ownerId}>)."
                )

                ServerService.getInstance().loadServerData(event.guild.id.value.toLong())
            }
        }
    }

    companion object {
        @JvmStatic
        val GUILD_ON_JOIN: MutableList<ULong> = mutableListOf()
    }
}
