package net.dungeonhub.application.listener.ticket

import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dungeonhub.application.commands.TicketSystem
import net.dungeonhub.application.commands.TicketSystem.Companion.isAllowedToChangeState
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.TicketConnection
import net.dungeonhub.enums.TicketState
import net.dungeonhub.model.ticket.TicketModel
import kotlin.time.Duration.Companion.seconds

@LoadExtension
class TicketDeleteListener : Extension() {
    override val name = "ticket-delete-listener"

    override suspend fun setup() {
        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.componentId == "delete-ticket")
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

                if(ticket.state == TicketState.Deleted) {
                    response.respond {
                        addEmbed {
                            description = "This ticket is already deleted!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                if(!event.interaction.user.isAllowedToChangeState(ticket)) {
                    response.respond {
                        addEmbed {
                            description = "You're not allowed to delete this ticket!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                response.respond {
                    embeds = mutableListOf(deleteTicket(ticket, event.interaction.user, event.interaction.channel.asChannelOf<TextChannel>()))
                }
            }
        }
    }

    companion object {
        suspend fun deleteTicket(ticket: TicketModel, member: Member, textChannel: TextChannel): EmbedBuilder {
            val updatedTicket = updateTicketState(ticket) ?: return buildEmbed {
                description = "Couldn't set the ticket state to deleted!"
                color(EmbedColor.Negative)
            }

            TicketSystem.scheduler.launch {
                textChannel.createMessage {
                    addEmbed {
                        description = "Ticket deleted by ${member.mention} - this channel will be deleted in a few seconds."
                        color(EmbedColor.Negative)
                    }
                }

                TicketTranscriptListener.generateTranscript(
                    textChannel,
                    null,
                    updatedTicket,
                    updatedTicket.ticketPanel.deleteTranscriptTarget
                ) {
                    delay(3.seconds)
                    textChannel.delete()
                }
            }

            return buildEmbed {
                description = "Deleting the ticket..."
                color(EmbedColor.Positive)
            }
        }

        suspend fun updateTicketState(ticket: TicketModel): TicketModel? {
            val updateModel = ticket.getUpdateModel()
            updateModel.state = TicketState.Deleted
            return TicketConnection[ticket.ticketPanel.discordServer, ticket.ticketPanel].authenticated().updateTicket(ticket.id, updateModel)
        }
    }
}