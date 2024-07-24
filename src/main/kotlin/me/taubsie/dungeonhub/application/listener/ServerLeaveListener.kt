package me.taubsie.dungeonhub.application.listener

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.core.event.guild.GuildDeleteEvent
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.ServerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@LoadExtension
class ServerLeaveListener : Extension() {
    val logger: Logger = LoggerFactory.getLogger(ServerLeaveListener::class.java)

    override val name = "server-leave-listener"
    override suspend fun setup() {
        event<GuildDeleteEvent> {
            action {
                ServerJoinListener.GUILD_ON_JOIN.remove(event.guildId.value)

                val ownerName = event.guild?.getOwnerOrNull()?.effectiveName ?: "no-name"

                logger.info(
                    "I just left server '{}' by '{}' ({}).",
                    event.guild?.name ?: "unknown",
                    ownerName,
                    event.guild?.ownerId ?: "unknown"
                )


                ApplicationService.getBotOwner(kord)?.dm(
                    "I just left server `${event.guild?.name}` by $ownerName (<@${event.guild?.ownerId}>)."
                )

                ServerService.unloadServerData(event.guildId.value.toLong())
            }
        }
    }


}