package net.dungeonhub.application.misc

import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel
import net.dungeonhub.mojang.connection.MojangConnection

// TODO implement more placeholders
class TicketPlaceholders(
    val ticketPanel: TicketPanelModel,
    val ticket: TicketModel
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
            val replacements: MutableMap<String, () -> String> = HashMap()

            replacements["user.mention"] = { "<@${ticketUserId}>" }
            replacements["user.ign"] = { ticketUserIgn ?: "Not linked" } // TODO handle errors, maybe catch it early in the ticket creation process if a user is required to be linked
            replacements["panel.name"] = { ticketPanel.displayName ?: ticketPanel.name }

            return replacements
        }
}