package net.dungeonhub.application.listener.ticket

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MemberBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.dm
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.dungeonhub.application.commands.TicketSystem
import net.dungeonhub.application.commands.TicketSystem.Companion.isAllowedToChangeState
import net.dungeonhub.application.commands.TicketSystem.Companion.replacePlaceholders
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.connection.applyJson
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.event.TicketTranscriptCreatedEvent
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.TicketPlaceholders
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.buildEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.enums.TranscriptTarget
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.service.GsonService
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

                generateTranscript(
                    event.interaction.channel.asChannelOf<TextChannel>(),
                    event.interaction.user,
                    ticket,
                    TranscriptTarget.TranscriptChannel
                )

                response.respond {
                    addEmbed {
                        description = "Generating transcript..."
                        color(EmbedColor.Positive)
                    }
                }
            }
        }
    }

    companion object {
        suspend fun generateTranscript(textChannel: TextChannel, requester: MemberBehavior?, ticket: TicketModel, target: TranscriptTarget, postTranscriptAction: suspend () -> Unit = {}) {
            val url = TicketSystem.scheduler.async {
                val transcript = textChannel.createTranscript()

                val result = ContentConnection.authenticated().uploadFile(transcript.toByteArray(StandardCharsets.UTF_8))?.let {
                    ContentConnection.authenticated().getCdnUrl(it).toString()
                }

                if(result != null) {
                    TicketSystem.scheduler.launch {
                        DiscordConnection.bot.send(TicketTranscriptCreatedEvent(ticket, result))
                    }
                }

                return@async result
            }

            val userReply = if(target.sendToUser) {
                val transcriptInfoMessage = createPlaceholderMessage(textChannel, requester)

                TicketSystem.scheduler.launch {
                    sendUserTranscript(
                        url.await(),
                        transcriptInfoMessage,
                        ticket,
                        textChannel,
                        requester?.asMemberOrNull()
                            ?: DiscordConnection.bot.kordRef.getSelf().asMember(textChannel.guildId)
                    )
                }
            } else null

            val channelReply = if(target.sendToTranscriptChannel) {
                val transcriptInfoMessage = createPlaceholderMessage(textChannel, requester)

                TicketSystem.scheduler.launch {
                    sendChannelTranscript(
                        url.await(),
                        transcriptInfoMessage,
                        ticket,
                        textChannel,
                        requester?.asMemberOrNull()
                            ?: DiscordConnection.bot.kordRef.getSelf().asMember(textChannel.guildId)
                    )
                }
            } else null

            TicketSystem.scheduler.launch {
                userReply?.join()
                channelReply?.join()

                postTranscriptAction()
            }
        }

        private suspend fun createPlaceholderMessage(textChannel: TextChannel, requester: MemberBehavior?): Message =
            textChannel.createMessage {
                addEmbed {
                    description = if(requester != null) {
                        "Saving transcript requested by ${requester.mention}..."
                    } else {
                        "Saving transcript..."
                    }
                    color(EmbedColor.Information)
                }
            }

        // TODO make it possible to add a custom message to this
        private suspend fun sendUserTranscript(
            url: String?,
            transcriptInfoMessage: Message,
            ticket: TicketModel,
            textChannel: TextChannel,
            requester: Member
        ) {
            handleTranscript(
                url,
                transcriptInfoMessage,
                requester,
                "[Transcript]($url) sent to <@${ticket.user.id}>"
            ) { url ->
                val placeholders = TicketPlaceholders(
                    ticket.ticketPanel,
                    ticket,
                    requester,
                    textChannel,
                    url
                )

                val fallbackMessage = JsonArray()
                fallbackMessage.add("transcript")

                @Suppress("DEPRECATION")
                val messageJson = try {
                    ticket.ticketPanel.userTranscriptDm?.let {
                        GsonService.gson.fromJson(it, JsonObject::class.java)
                    }
                } catch (_: JsonSyntaxException) {
                    null
                } ?: fallbackMessage

                DiscordConnection.bot.kordRef
                    .getUser(Snowflake(ticket.user.id))
                    ?.let { user ->
                        user.dm {
                            embeds = parseTranscriptDmEmbeds(messageJson, placeholders)
                        }
                    }
            }
        }

        // TODO maybe merge with the method in the TicketSystem?
        private fun parseTranscriptDmEmbeds(embedData: JsonElement, placeholders: TicketPlaceholders): MutableList<EmbedBuilder> {
            val embedBuilders: MutableList<EmbedBuilder> = mutableListOf()

            try {
                if (embedData.isJsonObject) {
                    val embedBuilder = EmbedBuilder()

                    embedData.asJsonObject
                        .entrySet()
                        .forEach {
                            embedBuilder.applyJson(
                                it.key,
                                replacePlaceholders(it.value, placeholders)
                            )
                        }

                    embedBuilders.add(embedBuilder)
                } else if (embedData.isJsonArray) {
                    embedData.asJsonArray
                        .forEach { jsonElement: JsonElement ->
                            if (jsonElement.isJsonObject) {
                                val embedBuilder = EmbedBuilder()

                                jsonElement.asJsonObject
                                    .entrySet()
                                    .forEach { entry: Map.Entry<String, JsonElement> ->
                                        embedBuilder.applyJson(
                                            entry.key,
                                            replacePlaceholders(entry.value, placeholders)
                                        )
                                    }

                                embedBuilders.add(embedBuilder)
                            } else if (jsonElement.isJsonPrimitive) {
                                buildCustomEmbed(jsonElement.asString, placeholders)?.let { embedBuilders.add(it) }
                            }
                        }
                } else if (embedData.isJsonPrimitive) {
                    buildCustomEmbed(embedData.asString, placeholders)?.let { embedBuilders.add(it) }
                }
            } catch (_: JsonSyntaxException) {

            }

            return embedBuilders
        }

        private fun buildCustomEmbed(type: String, placeholders: TicketPlaceholders): EmbedBuilder? {
            return when (type) {
                "transcript" -> generateTranscriptEmbed(placeholders)

                else -> null
            }
        }

        // TODO improve message content
        private fun generateTranscriptEmbed(placeholders: TicketPlaceholders): EmbedBuilder = buildEmbed {
            field("Panel", true) { replacePlaceholders("{panel.name}", placeholders) }
            field("Ticket Name", true) { replacePlaceholders("{ticket.name}", placeholders) }
            field("Transcript", true) {
                replacePlaceholders("{transcript.url}", placeholders).takeIf {
                    it != "unknown"
                }?.let {
                    "[Click here]($it)"
                } ?: "Not available"
            }
        }

        private suspend fun sendChannelTranscript(
            url: String?,
            transcriptInfoMessage: Message,
            ticket: TicketModel,
            textChannel: TextChannel,
            requester: Member
        ) {
            handleTranscript(
                url,
                transcriptInfoMessage,
                requester,
                if (ticket.ticketPanel.transcriptChannel != null) {
                    "[Transcript]($url) sent to <#${ticket.ticketPanel.transcriptChannel?.id}>"
                } else {
                    "[Transcript]($url) generated"
                }
            ) { url ->
                val placeholders = TicketPlaceholders(
                    ticket.ticketPanel,
                    ticket,
                    requester,
                    textChannel,
                    url
                )

                ticket.ticketPanel.transcriptChannel?.let { transcriptChannel ->
                    textChannel.guild.getChannelOf<GuildMessageChannel>(Snowflake(transcriptChannel.id))
                }?.let { transcriptChannel ->
                    transcriptChannel.createMessage {
                        embeds = mutableListOf(generateTranscriptEmbed(placeholders))
                    }
                }
            }
        }

        private suspend fun handleTranscript(
            url: String?,
            transcriptInfoMessage: Message,
            requester: MemberBehavior?,
            target: String,
            handler: suspend (url: String) -> Unit
        ) {
            if (url != null) {
                handler(url)

                transcriptInfoMessage.edit {
                    embeds = mutableListOf(buildEmbed {
                        description = if (requester != null) {
                            "$target, requested by ${requester.mention}"
                        } else {
                            target
                        }
                        color(EmbedColor.Positive)
                    })
                }
            } else {
                transcriptInfoMessage.edit {
                    embeds = mutableListOf(buildEmbed {
                        description = if (requester != null) {
                            "Couldn't generate the transcript requested by ${requester.mention}!"
                        } else {
                            "Couldn't generate the transcript!"
                        }
                        color(EmbedColor.Negative)
                    })
                }
            }
        }
    }
}