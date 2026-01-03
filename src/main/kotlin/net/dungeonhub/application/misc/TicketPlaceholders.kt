package net.dungeonhub.application.misc

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
    val cacheExpiration: Int = 60 * 3
) {
    val ticketUserId = ticket.user.id
    val ticketUserIgn by lazy {
        ticketUserModel?.minecraftId?.let { MojangConnection.getNameByUUID(it) }
    }

    val ticketUserModel by lazy {
        DiscordUserConnection.authenticated().getByIdOrCreate(ticketUserId)
    }

    val replacements: Map<String, () -> String>
        get() {
            val apiConnection = HypixelApiConnection().withCacheExpiration(cacheExpiration)

            val replacements: MutableMap<String, () -> String> = HashMap()

            replacements["user.mention"] = { "<@${ticketUserId}>" }
            replacements["user.minecraft.name"] = { ticketUserIgn ?: "Not linked" }
            replacements["user.skyblock.level"] = {
                apiConnection.getSkyblockProfiles(
                    ticketUserModel?.minecraftId ?: throw NotLinkedException()
                )?.profiles?.maxOfOrNull {
                    it.getCurrentMember(ticketUserModel?.minecraftId ?: throw NotLinkedException())?.leveling?.level
                        ?: 0
                }?.toString() ?: "?"
            }
            replacements["panel.name"] = { ticketPanel.displayName ?: ticketPanel.name }

            return replacements
        }
}