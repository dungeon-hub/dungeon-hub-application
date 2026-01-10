package net.dungeonhub.application.listener.ticket

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import kotlinx.coroutines.launch
import net.dungeonhub.application.commands.TicketSystem
import net.dungeonhub.application.commands.TicketSystem.Companion.isAllowedToChangeState
import net.dungeonhub.application.commands.TicketSystem.Companion.updateTicketPermissions
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.TicketConnection
import net.dungeonhub.enums.TicketState
import net.dungeonhub.model.ticket.TicketModel

@LoadExtension
class TicketOpenListener : Extension() {
    override val name = "ticket-open-listener"
    override suspend fun setup() {
        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.componentId == "open-ticket")
            }

            action {
                val response = event.interaction.deferEphemeralResponse()

                val ticket = DiscordServerConnection.authenticated().findTickets(event.interaction.guildId.value.toLong(), channelId = event.interaction.channelId.value.toLong())?.firstOrNull()

                if(ticket == null) {
                    response.respond {
                        addEmbed {
                            description = "This isn't a ticket channel!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                if(ticket.state != TicketState.Closed) {
                    response.respond {
                        addEmbed {
                            description = "This ticket can't be reopened!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                if(!event.interaction.user.isAllowedToChangeState(ticket)) {
                    response.respond {
                        addEmbed {
                            description = "You're not allowed to close this ticket!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                val embed = openTicket(event.interaction.user, event.interaction.channel.asChannelOf(), ticket)

                response.respond {
                    embeds = mutableListOf(embed)
                }
            }
        }
    }

    fun openTicket(member: MemberBehavior, textChannel: TextChannel, ticket: TicketModel): EmbedBuilder {
        TicketSystem.scheduler.launch {
            val updateModel = ticket.getUpdateModel()
            updateModel.state = TicketState.Open
            val updatedTicket = TicketConnection[member.guildId.value.toLong(), ticket.ticketPanel].authenticated().updateTicket(ticket.id, updateModel)

            if(updatedTicket == null) {
                textChannel.createMessage {
                    addEmbed {
                        description = "Couldn't update ticket state!"
                        color(EmbedColor.Negative)
                    }
                }
                return@launch
            }

            textChannel.edit {
                val newName = TicketSystem.buildTicketName(updatedTicket.ticketPanel, updatedTicket, member.asMember(), textChannel)

                if(newName != null) {
                    name = newName
                }

                permissionOverwrites?.clear()
                updateTicketPermissions(updatedTicket.ticketPanel, updatedTicket)

                val categories = if (ticket.state in listOf(TicketState.Creating, TicketState.Open)) {
                    updatedTicket.ticketPanel.openCategories
                } else {
                    updatedTicket.ticketPanel.closedCategories
                }
                TicketSystem.getCategory(categories)?.let { parentId = Snowflake(it) }
            }

            textChannel.createMessage {
                addEmbed {
                    description = "Ticket opened by ${member.mention}."
                    color(EmbedColor.Default)
                }
            }
        }

        return buildEmbed {
            description = "Opening ticket..."
            color(EmbedColor.Positive)
        }
    }
}