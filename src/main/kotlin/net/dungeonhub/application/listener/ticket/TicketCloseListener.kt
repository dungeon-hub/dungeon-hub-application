package net.dungeonhub.application.listener.ticket

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasPermission
import dev.kordex.i18n.toKey
import kotlinx.coroutines.launch
import net.dungeonhub.application.commands.TicketSystem
import net.dungeonhub.application.commands.TicketSystem.Companion.updateTicketPermissions
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.TicketConnection
import net.dungeonhub.enums.TicketState
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.wrapper.kord.createTranscript
import java.nio.charset.StandardCharsets

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
                    }
                    return@action
                }

                if(!isAllowedToClose(event.interaction.user, ticket)) {
                    response.respond {
                        addEmbed {
                            description = "You're not allowed to close this ticket!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                if(!ticket.ticketPanel.closeable) {
                    // TODO actually delete ticket
                    // TODO also add a confirmation if closeConfirmation is true
                    response.respond {
                        addEmbed {
                            description = "Deleting ticket.. 3.. 2.. 1.. Haha, PRANKED! (I forgot to implement)"
                            color(EmbedColor.Negative)
                        }
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
                                        response.respond {
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

    // TODO is there any other situation in which some user might be allowed / disallowed to close a ticket?
    suspend fun isAllowedToClose(user: Member, ticket: TicketModel): Boolean {
        if (user.hasPermission(Permission.Administrator) || user.hasPermission(Permission.ManageChannels)) return true

        if (ticket.ticketPanel.supportRoles.any { user.roleIds.contains(Snowflake(it.id)) }
            || ticket.ticketPanel.additionalRoles.any { user.roleIds.contains(Snowflake(it.id)) }) {
            return true
        }

        // TODO here, we assume that the ticket creator is allowed to close the ticket - that should be a setting
        return ticket.user.id == user.id.value.toLong()
    }

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
                val newName = TicketSystem.buildTicketName(updatedTicket.ticketPanel, updatedTicket, member.asMember())

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
                    description = "Ticket closed by ${member.mention}."
                    color(EmbedColor.Default)
                }
            }

            val transcriptInfoMessage = textChannel.createMessage {
                addEmbed {
                    description = "Saving transcript..."
                    color(EmbedColor.Information)
                }
            }

            // TODO send transcript --> user, transcript channel or both, depending on settings
            TicketSystem.scheduler.launch {
                val transcript = textChannel.createTranscript()

                val url = ContentConnection.authenticated().uploadFile(transcript.toByteArray(StandardCharsets.UTF_8))?.let {
                    ContentConnection.authenticated().getCdnUrl(it).toString()
                }

                if(url != null) {
                    kord.getUser(Snowflake(updatedTicket.user.id))?.let { user ->
                        user.dm {
                            embeds = mutableListOf(buildEmbed {
                                field("Panel", true) { updatedTicket.ticketPanel.displayName ?: updatedTicket.ticketPanel.name }
                                field("Ticket Name", true) { textChannel.name }
                                field("Transcript", true) { "[Click here]($url)" }
                            })
                        }
                    }

                    transcriptInfoMessage.edit {
                        embeds = mutableListOf(buildEmbed {
                            description = "[Transcript]($url) sent to <@${ticket.user.id}>"
                            color(EmbedColor.Positive)
                        })
                    }
                } else {
                    transcriptInfoMessage.edit {
                        embeds = mutableListOf(buildEmbed {
                            description = "Couldn't generate the transcript!"
                            color(EmbedColor.Negative)
                        })
                    }
                }
            }

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