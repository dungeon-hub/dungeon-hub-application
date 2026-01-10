package net.dungeonhub.application.misc

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.exceptions.NotLinkedException
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection

// TODO implement more placeholders
class TicketPlaceholders(
    val ticketPanel: TicketPanelModel,
    val ticket: TicketModel,
    val interactionUser: Member,
    val ticketChannel: TextChannel?,
    val transcriptUrl: String? = null,
    val cacheExpiration: Int = 60 * 3
) {
    val ticketUserId = ticket.user.id
    val ticketUserIgn by lazy {
        ticketUserModel?.minecraftId?.let { MojangConnection.getNameByUUID(it) }
    }

    val ticketUser by lazy {
        runBlocking {
            DiscordConnection.bot.kordRef
                .getUser(Snowflake(ticketUserId))
                ?.asMemberOrNull(Snowflake(ticketPanel.discordServer.id))
        }
    }

    val ticketUserModel by lazy {
        DiscordUserConnection.authenticated().getByIdOrCreate(ticketUserId)
    }

    val replacements: Map<String, () -> String>
        get() {
            val apiConnection = HypixelApiConnection().withCacheExpiration(cacheExpiration)

            val replacements: MutableMap<String, () -> String> = HashMap()

            replacements["user.mention"] = { "<@${ticketUserId}>" }
            replacements["user.displayName"] = { ticketUser?.effectiveName ?: "unknown" }
            replacements["interactionUser.displayName"] = { interactionUser.effectiveName }
            replacements["user.minecraft.name"] = { ticketUserIgn ?: "unlinked" }
            replacements["user.skyblock.level"] = {
                apiConnection.getSkyblockProfiles(
                    ticketUserModel?.minecraftId ?: throw NotLinkedException()
                )?.profiles?.maxOfOrNull {
                    it.getCurrentMember(ticketUserModel?.minecraftId ?: throw NotLinkedException())?.leveling?.level
                        ?: 0
                }?.toString() ?: "?"
            }
            replacements["panel.name"] = { ticketPanel.displayName ?: ticketPanel.name }
            replacements["ticket.id"] = { ticket.id.toString() }
            replacements["ticket.name"] = { ticketChannel?.name ?: "not-set" }
            replacements["transcript.url"] = { transcriptUrl ?: "unknown" }

            return replacements
        }
}