package net.dungeonhub.application.listener

import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.gateway.Intent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.dm
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ServerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@LoadExtension
class ServerJoinListener : Extension() {
    val logger: Logger = LoggerFactory.getLogger(ServerJoinListener::class.java)

    override val name = "server-join-listener"
    override val intents = mutableSetOf<Intent>(Intent.Guilds)
    override suspend fun setup() {
        event<GuildCreateEvent> {
            action {
                if (GUILD_ON_JOIN.contains(event.guild.id.value)) {
                    return@action
                }

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

                ServerService.loadServerData(event.guild.id.value.toLong())
            }
        }
    }

    companion object {
        @JvmStatic
        val GUILD_ON_JOIN: MutableList<ULong> = mutableListOf()
    }
}
