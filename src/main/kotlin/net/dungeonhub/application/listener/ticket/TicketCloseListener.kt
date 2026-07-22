package net.dungeonhub.application.listener.ticket

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.i18n.toKey
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
class TicketCloseListener : Extension() {
    override val name = "ticket-close-listener"

    override suspend fun setup() {
        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.componentId == "close-ticket")
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

                if(ticket.state == TicketState.Closed || ticket.state == TicketState.Deleted) {
                    response.respond {
                        addEmbed {
                            description = "This ticket is already closed!"
                            color(EmbedColor.Negative)
                        }

                        actionRow {
                            TicketSystem.getControlButtons().forEach {
                                it()
                            }
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

                if(!ticket.ticketPanel.closeable) {
                    response.respond {
                        embeds = mutableListOf(TicketDeleteListener.deleteTicket(ticket, event.interaction.user, event.interaction.channel.asChannelOf<TextChannel>()))
                    }
                    return@action
                }

                if(ticket.ticketPanel.closeConfirmation) {
                    response.respond {
                        addEmbed {
                            description = "Please confirm that you actually want to close this ticket."
                            color(EmbedColor.Information)
                        }

                        components {
                            ephemeralButton {
                                label = "Confirm".toKey()
                                style = ButtonStyle.Success

                                action {
                                    val updatedTicket = TicketConnection[ticket.ticketPanel.discordServer, ticket.ticketPanel].authenticated().getById(ticket.id)

                                    if(updatedTicket == null) {
                                        respond {
                                            embeds = mutableListOf(buildEmbed {
                                                description = "Couldn't load ticket info!"
                                                color(EmbedColor.Negative)
                                            })
                                        }
                                        return@action
                                    }

                                    if(updatedTicket.state == TicketState.Closed || updatedTicket.state == TicketState.Deleted) {
                                        respond {
                                            addEmbed {
                                                description = "This ticket is already closed!"
                                                color(EmbedColor.Negative)
                                            }
                                        }
                                        return@action
                                    }

                                    val embed = closeTicket(member!!, channel.asChannelOf(), updatedTicket)

                                    respond {
                                        embeds = mutableListOf(embed)
                                    }
                                }
                            }
                        }
                    }
                    return@action
                }

                val embed = closeTicket(event.interaction.user, event.interaction.channel.asChannelOf(), ticket)

                response.respond {
                    embeds = mutableListOf(embed)
                }
            }
        }
    }

    companion object {
        fun closeTicket(member: MemberBehavior, textChannel: TextChannel, ticket: TicketModel): EmbedBuilder {
            TicketSystem.scheduler.launch {
                val updateModel = ticket.getUpdateModel()
                updateModel.state = TicketState.Closed
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
                    permissionOverwrites?.clear()
                    updateTicketPermissions(updatedTicket.ticketPanel, updatedTicket)

                    val categories = if (updatedTicket.state in listOf(TicketState.Creating, TicketState.Open)) {
                        updatedTicket.ticketPanel.openCategories
                    } else {
                        updatedTicket.ticketPanel.closedCategories
                    }
                    TicketSystem.getCategory(categories)?.let { parentId = Snowflake(it) }
                }

                val updateTime = TicketSystem.updateTicketName(updatedTicket, member, textChannel)

                textChannel.createMessage {
                    addEmbed {
                        description = "Ticket closed by ${member.mention}.${
                            if (updateTime != null) {
                                "\n-# The ticket name will be updated in $updateTime due to ratelimits."
                            } else {
                                ""
                            }
                        }"
                        color(EmbedColor.Default)
                    }
                }

                TicketSystem.logTicketAction(member.guild, ticket) {
                    description = "Ticket #${ticket.id} closed by ${member.mention}."
                }

                TicketTranscriptListener.generateTranscript(
                    textChannel,
                    member,
                    updatedTicket,
                    updatedTicket.ticketPanel.closeTranscriptTarget
                )

                textChannel.createMessage {
                    addEmbed {
                        title = "Manage Ticket"
                    }

                    actionRow {
                        TicketSystem.getControlButtons().forEach {
                            it()
                        }
                    }
                }
            }

            return buildEmbed {
                description = "Closing ticket..."
                color(EmbedColor.Positive)
            }
        }
    }
}