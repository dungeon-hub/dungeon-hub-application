package me.taubsie.dungeonhub.application.commands

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.optionalStringChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getJumpUrl
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.*
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.common.DungeonHubService
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

/**
 * This extension provides the ability to manage embeds.
 * It allows you to get the embed data of a message, send a custom embed, add an embed to a message sent by this bot, and edit an embed sent by this bot.
 * The embed data can be provided in a JSON format.
 * The embed data can be displayed as a beautiful embed (all fields of the embed class are mapped to embed fields), as source code, or as a CDN link (which contains the source code).
 * The embed data can be sent to a specific channel and defaults to the channel where the command was executed.
 */
@LoadExtension
class EmbedCommand : Extension() {
    override val name = "embed-command"

    //Suppress "method too complex" because it's pretty understandable and the size is required
    @Suppress("kotlin:S3776")
    override suspend fun setup() {
        publicSlashCommand {
            name = "embed"
            description = "Makes it possible to manage embeds."
            allowInDms = false
            defaultMemberPermissions = Permissions(Permission.ManageMessages)

            publicSubCommand(::GetArguments) {
                name = "get"
                description = "Gets the embed data of a message."

                action {
                    respond {
                        val beautiful = arguments.type.equals("beautiful", ignoreCase = true)

                        val message = arguments.getMessage() ?: throw InvalidOptionException(
                            "link"
                        )

                        if (message.embeds.isEmpty()) {
                            throw InvalidOptionException(
                                "link",
                                "The given message doesn't have an embed."
                            )
                        }

                        val count = arguments.count ?: -1

                        if (count != -1 && count >= message.embeds.size) {
                            throw InvalidOptionException(
                                "link",
                                "The given message doesn't have that many embeds."
                            )
                        }

                        val embeds = if (count == -1) message.embeds else mutableListOf(message.embeds[count])

                        val embedBuilder = ApplicationService.embed
                        embedBuilder.color = EmbedColor.Default.color

                        if (beautiful && embeds.size == 1) {
                            DungeonHubService.getInstance().gson.toJsonTree(embeds[0])
                                .asJsonObject
                                .entrySet()
                                .forEach(Consumer { entry: Map.Entry<String, JsonElement> ->
                                    embedBuilder.field(
                                        entry.key,
                                        false
                                    )
                                    {
                                        entry.value.toString()
                                    }
                                })
                        } else {
                            val embedSource = DungeonHubService.getInstance().gson.toJson(
                                if (embeds.size == 1) embeds[0].toModel()
                                else embeds.map { it.toModel() }
                            )

                            val description =
                                if (embedSource.length >= 4000 || arguments.type.equals("cdn", ignoreCase = true)) {
                                    ContentConnection.getInstance()
                                        .uploadFile(embedSource.toByteArray(StandardCharsets.UTF_8)).map { s: String? ->
                                            ContentConnection.getInstance()
                                                .getCdnUrl(s).toString()
                                        }.orElse(embedSource)
                                } else {
                                    "```\n$embedSource\n```"
                                }

                            embedBuilder.description = description
                        }

                        this.embeds = mutableListOf(embedBuilder)
                    }
                }
            }

            publicSubCommand(::SendArguments) {
                name = "send"
                description = "Sends a custom embed."

                action {
                    respond {
                        val channel = (arguments.channel ?: channel).asChannelOf<MessageChannel>()

                        var source = arguments.embed

                        val cdnPrefix = ConfigProperty.CDN_URL.value
                            ?: throw CommandExecutionException("The CDN isn't set up correctly in the bot's settings, please tell an administrator to correct that.")

                        if (source.startsWith(cdnPrefix)) {
                            source = source.substring(cdnPrefix.length)

                            source =
                                ContentConnection.getInstance()
                                    .downloadFile(source).orElseThrow {
                                        CommandExecutionException(
                                            "Couldn't download the file correctly."
                                        )
                                    }
                        }

                        val embedBuilders: MutableList<EmbedBuilder> = ArrayList()

                        try {
                            val embedSource = DungeonHubService.getInstance().gson.fromJson(
                                source,
                                JsonElement::class.java
                            )

                            if (embedSource.isJsonObject) {
                                val embedBuilder = EmbedBuilder()

                                embedSource.asJsonObject
                                    .entrySet()
                                    .forEach { entry: Map.Entry<String, JsonElement> ->
                                        embedBuilder.applyJson(
                                            entry.key,
                                            entry.value
                                        )
                                    }

                                embedBuilders.add(embedBuilder)
                            } else if (embedSource.isJsonArray) {
                                embedSource.asJsonArray
                                    .forEach { jsonElement: JsonElement ->
                                        if (jsonElement.isJsonObject) {
                                            val embedBuilder = EmbedBuilder()

                                            jsonElement.asJsonObject
                                                .entrySet()
                                                .forEach { entry: Map.Entry<String, JsonElement> ->
                                                    embedBuilder.applyJson(
                                                        entry.key,
                                                        entry.value
                                                    )
                                                }

                                            embedBuilders.add(embedBuilder)
                                        }
                                    }
                            }
                        } catch (jsonSyntaxException: JsonSyntaxException) {
                            throw CommandExecutionException("The embed JSON you entered is invalid formatted.")
                        } catch (exception: Exception) {
                            throw CommandExecutionException(exception)
                        }

                        if (embedBuilders.isEmpty()) {
                            throw CommandExecutionException("Please provide any embeds to send.")
                        }

                        channel.createMessage {
                            embeds = embedBuilders
                        }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Positive.color
                        embed.description = "Embed sent into <#${channel.id}>!"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::MessageLinkEmbedArguments) {
                name = "add"
                description = "Add an embed to a message sent by this bot."

                action {
                    respond {
                        val embedBuilders: MutableList<EmbedBuilder> = mutableListOf()

                        try {
                            val embedSource = DungeonHubService.getInstance().gson.fromJson(
                                arguments.embed,
                                JsonElement::class.java
                            )

                            if (embedSource.isJsonObject) {
                                val embedBuilder = EmbedBuilder()

                                embedSource.asJsonObject
                                    .entrySet()
                                    .forEach {
                                        embedBuilder.applyJson(
                                            it.key,
                                            it.value
                                        )
                                    }

                                embedBuilders.add(embedBuilder)
                            } else if (embedSource.isJsonArray) {
                                embedSource.asJsonArray
                                    .forEach { jsonElement: JsonElement ->
                                        if (jsonElement.isJsonObject) {
                                            val embedBuilder = EmbedBuilder()

                                            jsonElement.asJsonObject
                                                .entrySet()
                                                .forEach { entry: Map.Entry<String, JsonElement> ->
                                                    embedBuilder.applyJson(
                                                        entry.key,
                                                        entry.value
                                                    )
                                                }

                                            embedBuilders.add(embedBuilder)
                                        }
                                    }
                            }
                        } catch (jsonSyntaxException: JsonSyntaxException) {
                            throw CommandExecutionException("The embed JSON you entered is invalid formatted.")
                        } catch (exception: java.lang.Exception) {
                            throw CommandExecutionException(exception)
                        }

                        if (embedBuilders.isEmpty()) {
                            throw CommandExecutionException("Please provide any embeds to send.")
                        }

                        val message = arguments.getMessage() ?: throw InvalidOptionException(
                            "link"
                        )

                        //TODO how to handle interaction responses?
                        if (message.author?.isSelf() != true) {
                            throw InvalidOptionException(
                                "link",
                                "How should I edit a message that wasn't sent by myself?"
                            )
                        }

                        if (message.embeds.isEmpty()) {
                            throw InvalidOptionException(
                                "link",
                                "The given message doesn't have any embeds to edit."
                            )
                        }

                        message.edit {
                            this@edit.embeds =
                                (message.embeds.toMutableList().map { it.toBuilder() } + embedBuilders).toMutableList()
                        }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Positive.color
                        embed.description = "Embed(s) added to message " + message.getJumpUrl() + "!"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::EditArguments) {
                name = "edit"
                description = "Edit an embed sent by this bot."

                action {
                    respond {
                        val embed = EmbedBuilder()
                        try {
                            val embedSource: JsonObject = DungeonHubService.getInstance().gson.fromJson(
                                arguments.embed,
                                JsonObject::class.java
                            )

                            embedSource.entrySet().forEach { entry: Map.Entry<String, JsonElement> ->
                                embed.applyJson(
                                    entry.key,
                                    entry.value
                                )
                            }
                        } catch (jsonSyntaxException: JsonSyntaxException) {
                            throw CommandExecutionException("The embed JSON you entered is invalid formatted.")
                        } catch (exception: Exception) {
                            throw CommandExecutionException(exception)
                        }

                        val count = arguments.count ?: 0

                        val message = arguments.getMessage()

                        //TODO how to handle interaction responses?


                        if (message?.author?.isSelf() != true) {
                            throw InvalidOptionException(
                                "link",
                                "How should I edit a message that wasn't sent by myself?"
                            )
                        }

                        if (message.embeds.isEmpty()) {
                            throw InvalidOptionException(
                                "link",
                                "The given message doesn't have any embeds to edit."
                            )
                        }

                        message.edit {
                            val embedBuilders = message.embeds.map { it.toBuilder() }.toMutableList()
                            if (count >= embedBuilders.size) {
                                throw InvalidOptionException(
                                    "link",
                                    "The given message doesn't have that many embeds."
                                )
                            }

                            embedBuilders[count] = embed
                            embeds = embedBuilders
                        }

                        val response = ApplicationService.embed
                        response.color = EmbedColor.Positive.color
                        response.description = "Embed in message ${message.getJumpUrl()} edited!"

                        embeds = mutableListOf(response)
                    }
                }
            }
        }
    }

    open inner class MessageLinkArguments : Arguments() {
        private val messageLink by string {
            name = "link"
            description = "Please paste the link to the message here."
        }

        suspend fun getMessage(): Message? {
            return kord.loadMessageByLink(messageLink)
        }
    }

    inner class GetArguments : MessageLinkArguments() {
        val type by optionalStringChoice {
            name = "type"
            description = "Select how you want to get the embed data."
            choices = mutableMapOf("beautiful" to "beautiful", "source" to "source", "cdn" to "cdn")
        }

        val count by optionalInt {
            name = "count"
            description = "Select which embed you want to get (0-based counting)."
            minValue = 0
            maxValue = 25
        }
    }

    open inner class MessageLinkEmbedArguments : MessageLinkArguments() {
        val embed by string {
            name = "embed"
            description = "The embed data to send."
        }
    }

    inner class SendArguments : Arguments() {
        val embed by string {
            name = "embed"
            description = "The embed data to send."
        }

        val channel by optionalChannel {
            name = "channel"
            description = "The channel to send the embed into."

            requiredChannelTypes = mutableSetOf(
                ChannelType.GuildText,
                ChannelType.GuildVoice,
                ChannelType.GuildNews,
                ChannelType.PublicGuildThread
            )
        }
    }

    inner class EditArguments : MessageLinkEmbedArguments() {
        val count by optionalInt {
            name = "count"
            description = "Select which embed you want to edit (0-based counting)."
            minValue = 0
            maxValue = 25
        }
    }
}