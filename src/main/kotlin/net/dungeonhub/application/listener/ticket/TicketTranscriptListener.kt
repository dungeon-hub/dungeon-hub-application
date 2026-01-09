package net.dungeonhub.application.listener.ticket

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import kotlinx.coroutines.launch
import net.dungeonhub.application.commands.TicketSystem
import net.dungeonhub.application.commands.TicketSystem.Companion.isAllowedToChangeState
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.event.TicketTranscriptCreatedEvent
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.wrapper.kord.createTranscript
import java.nio.charset.StandardCharsets

@LoadExtension
class TicketTranscriptListener : Extension() {
    override val name = "ticket-transcript-listener"
    override suspend fun setup() {
        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.componentId == "transcript-ticket")
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

                if(!event.interaction.user.isAllowedToChangeState(ticket)) {
                    response.respond {
                        addEmbed {
                            description = "You're not allowed to generate a transcript here!"
                            color(EmbedColor.Negative)
                        }
                    }
                    return@action
                }

                val textChannel = event.interaction.channel.asChannelOf<TextChannel>()

                val transcriptInfoMessage = textChannel.createMessage {
                    addEmbed {
                        description = "Saving transcript requested by ${event.interaction.user.mention}..."
                        color(EmbedColor.Information)
                    }
                }

                TicketSystem.scheduler.launch {
                    val transcript = textChannel.createTranscript()

                    val url = ContentConnection.authenticated().uploadFile(transcript.toByteArray(StandardCharsets.UTF_8))?.let {
                        ContentConnection.authenticated().getCdnUrl(it).toString()
                    }

                    if(url != null) {
                        TicketSystem.scheduler.launch {
                            bot.send(TicketTranscriptCreatedEvent(ticket, url))
                        }

                        ticket.ticketPanel.transcriptChannel?.let { transcriptChannel ->
                            event.interaction.guild.getChannelOf<GuildMessageChannel>(Snowflake(transcriptChannel.id))
                        }?.let { transcriptChannel ->
                            transcriptChannel.createMessage {
                                // TODO improve embed
                                addEmbed {
                                    field("Panel", true) { ticket.ticketPanel.displayName ?: ticket.ticketPanel.name }
                                    field("Ticket Name", true) { textChannel.name }
                                    field("Transcript", true) { "[Click here]($url)" }
                                }
                            }
                        }

                        transcriptInfoMessage.edit {
                            if(ticket.ticketPanel.transcriptChannel != null) {
                                embeds = mutableListOf(buildEmbed {
                                    description = "[Transcript]($url) sent to <#${ticket.ticketPanel.transcriptChannel?.id}>, requested by ${event.interaction.user.mention}"
                                    color(EmbedColor.Positive)
                                })
                            } else {
                                embeds = mutableListOf(buildEmbed {
                                    description = "[Transcript]($url) generated; requested by ${event.interaction.user.mention}"
                                    color(EmbedColor.Positive)
                                })
                            }
                        }
                    } else {
                        transcriptInfoMessage.edit {
                            embeds = mutableListOf(buildEmbed {
                                description = "Couldn't generate the transcript requested by ${event.interaction.user.mention}!"
                                color(EmbedColor.Negative)
                            })
                        }
                    }
                }

                response.respond {
                    addEmbed {
                        description = "Generating transcript..."
                        color(EmbedColor.Positive)
                    }
                }
            }
        }
    }
}