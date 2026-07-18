package net.dungeonhub.application.commands

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.squareup.moshi.adapter
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
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.getJumpUrl
import net.dungeonhub.application.config.ConfigProperty
import net.dungeonhub.application.connection.applyJson
import net.dungeonhub.application.connection.loadMessageByLink
import net.dungeonhub.application.connection.toBuilder
import net.dungeonhub.application.connection.toModel
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.InvalidEmbedJsonWarning
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.EmbedModel
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.i18n.Translations.Command.Embed
import net.dungeonhub.service.GsonService
import net.dungeonhub.service.MoshiService
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
    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("kotlin:S3776")
    override suspend fun setup() {
        publicSlashCommand {
            name = Embed.name
            description = Embed.description
            allowInDms = false
            defaultMemberPermissions = Permissions(Permission.ManageMessages)

            publicSubCommand(::GetArguments) {
                name = "get".toKey()
                description = "Gets the embed data of a message.".toKey()

                action {
                    respond {
                        val beautiful = arguments.type.equals("beautiful", ignoreCase = true)

                        val message = arguments.getMessage() ?: throw InvalidOptionException("link")

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
                            @Suppress("DEPRECATION")
                            GsonService.gson.toJsonTree(embeds[0].toModel())
                                .asJsonObject
                                .entrySet()
                                .forEach(Consumer { entry: Map.Entry<String, JsonElement> ->
                                    embedBuilder.field(
                                        entry.key,
                                        false
                                    )
                                    {
                                        if(entry.value.isJsonPrimitive) {
                                            entry.value.asString
                                        } else {
                                            entry.value.toString()
                                        }
                                    }
                                })
                        } else {
                            val embedSource = if (embeds.size == 1) {
                                MoshiService.moshi.adapter<EmbedModel>().toJson(embeds[0].toModel())
                            } else {
                                MoshiService.moshi.adapter<List<EmbedModel>>().toJson(embeds.map { it.toModel() })
                            }

                            val description =
                                if (embedSource.length >= 4000 || arguments.type.equals("cdn", ignoreCase = true)) {
                                    ContentConnection.authenticated()
                                        .uploadFile(embedSource.toByteArray(StandardCharsets.UTF_8))
                                        ?.let { ContentConnection.authenticated().getCdnUrl(it).toString() }
                                        ?: embedSource
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
                name = "send".toKey()
                description = "Sends a custom embed.".toKey()

                action {
                    respond {
                        val channel = (arguments.channel ?: channel).asChannelOf<MessageChannel>()

                        var source = arguments.embed

                        val cdnPrefix = ConfigProperty.CDN_URL.value
                            ?: throw CommandExecutionException("The CDN isn't set up correctly in the bot's settings, please tell an administrator to correct that.")

                        if (source.startsWith(cdnPrefix)) {
                            source = source.substring(cdnPrefix.length)

                            source = ContentConnection.authenticated().downloadFile(source)
                                ?: throw CommandExecutionException("Couldn't download the file correctly.")
                        }

                        //TODO write tests for this; maybe also add an improved command that uses discord fields
                        val embedBuilders: MutableList<EmbedBuilder> = ArrayList()

                        try {
                            @Suppress("DEPRECATION")
                            val embedSource = GsonService.gson.fromJson(
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
                        } catch (_: JsonSyntaxException) {
                            throw InvalidEmbedJsonWarning()
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
                name = "add".toKey()
                description = "Add an embed to a message sent by this bot.".toKey()

                action {
                    respond {
                        val embedBuilders: MutableList<EmbedBuilder> = mutableListOf()

                        try {
                            @Suppress("DEPRECATION")
                            val embedSource = GsonService.gson.fromJson(
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
                        } catch (_: JsonSyntaxException) {
                            throw InvalidEmbedJsonWarning()
                        } catch (exception: java.lang.Exception) {
                            throw CommandExecutionException(exception)
                        }

                        if (embedBuilders.isEmpty()) {
                            throw CommandExecutionException("Please provide any embeds to send.")
                        }

                        val message = arguments.getMessage() ?: throw InvalidOptionException("link")

                        //TODO how to handle interaction responses?
                        if (message.author?.isSelf != true) {
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
                name = "edit".toKey()
                description = "Edit an embed sent by this bot.".toKey()

                action {
                    respond {
                        val embed = EmbedBuilder()
                        try {
                            @Suppress("DEPRECATION")
                            val embedSource: JsonObject = GsonService.gson.fromJson(
                                arguments.embed,
                                JsonObject::class.java
                            )

                            embedSource.entrySet().forEach { entry: Map.Entry<String, JsonElement> ->
                                embed.applyJson(
                                    entry.key,
                                    entry.value
                                )
                            }
                        } catch (_: JsonSyntaxException) {
                            throw InvalidEmbedJsonWarning()
                        } catch (exception: Exception) {
                            throw CommandExecutionException(exception)
                        }

                        val count = arguments.count ?: 0

                        val message = arguments.getMessage()

                        //TODO how to handle interaction responses?


                        if (message?.author?.isSelf != true) {
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
            name = "link".toKey()
            description = "Please paste the link to the message here.".toKey()
        }

        suspend fun getMessage(): Message? {
            return kord.loadMessageByLink(messageLink)
        }
    }

    inner class GetArguments : MessageLinkArguments() {
        val type by optionalStringChoice {
            name = "type".toKey()
            description = "Select how you want to get the embed data.".toKey()
            choices =
                mutableMapOf("beautiful".toKey() to "beautiful", "source".toKey() to "source", "cdn".toKey() to "cdn")
        }

        val count by optionalInt {
            name = "count".toKey()
            description = "Select which embed you want to get (0-based counting).".toKey()
            minValue = 0
            maxValue = 25
        }
    }

    open inner class MessageLinkEmbedArguments : MessageLinkArguments() {
        val embed by string {
            name = "embed".toKey()
            description = "The embed data to send.".toKey()
        }
    }

    class SendArguments : Arguments() {
        val embed by string {
            name = "embed".toKey()
            description = "The embed data to send.".toKey()
        }

        val channel by optionalChannel {
            name = "channel".toKey()
            description = "The channel to send the embed into.".toKey()

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
            name = "count".toKey()
            description = "Select which embed you want to edit (0-based counting).".toKey()
            minValue = 0
            maxValue = 25
        }
    }
}