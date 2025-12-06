package net.dungeonhub.application.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralMessageCommand
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.StaticMessageService
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.StaticMessageConnection
import net.dungeonhub.enums.StaticMessageType
import net.dungeonhub.i18n.Translations.Command.StaticMessage
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.static_message.StaticMessageCreationModel

@LoadExtension
class StaticMessageCommand: Extension() {
    override val name = "static-message-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = StaticMessage.name
            description = StaticMessage.description
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::StaticMessageCreateArguments) {
                name = StaticMessage.Create.name
                description = StaticMessage.Create.description

                action {
                    val channel = (arguments.channel?.asChannelOfOrNull<MessageChannel>()) ?: channel

                    val creationModel = StaticMessageCreationModel(
                        (channel).id.value.toLong(),
                        null,
                        arguments.staticMessageType,
                        emptyList()
                    )

                    val connection = StaticMessageConnection[guild!!.id.value.toLong()].authenticated()

                    val staticMessageModel = connection.createStaticMessage(creationModel)
                        ?: throw CommandExecutionException("Couldn't create static message.")

                    val response = respond {
                        embed {
                            description = "Static Message created, sending into ${channel.mention}."
                            color(EmbedColor.Positive)
                        }
                    }

                    val sentMessage = StaticMessageService.sendStaticMessage(connection, staticMessageModel, channel)

                    response.edit {
                        embed {
                            description = "Static message created: https://discord.com/channels/${sentMessage.server.id}/${channel.id.value.toLong()}/${sentMessage.messageId}"
                            color(EmbedColor.Positive)
                        }
                    }
                }
            }
        }

        ephemeralMessageCommand {
            name = "Update static message".toKey()

            action {
                val response = respond {
                    embed {
                        description = "Trying to update the static message..."
                        color(EmbedColor.Default)
                    }
                }

                val staticMessage = StaticMessageConnection[guild!!.id.value.toLong()].authenticated().findStaticMessages(
                    null,
                    event.interaction.target.channelId.value.toLong()
                )?.firstOrNull { it.messageId == event.interaction.target.id.value.toLong() }

                staticMessage?.let {
                    StaticMessageService.updateStaticMessage(
                        it,
                        event.interaction.target
                    )

                    response.edit {
                        embed {
                            description = "Updated the static message (probably)."
                            color(EmbedColor.Positive)
                        }
                    }
                } ?: response.edit {
                    embed {
                        description = "Couldn't find static message."
                        color(EmbedColor.Negative)
                    }
                }
            }
        }
    }

    class StaticMessageCreateArguments : Arguments() {
        val staticMessageType by enumChoice<StaticMessageType> {
            name = CommonArguments.StaticMessageType.name
            description = StaticMessage.Create.Arguments.StaticMessageType.description
            typeName = "StaticMessageType".toKey()
        }

        val channel by optionalChannel {
            name = StaticMessage.Create.Arguments.Channel.name
            description = StaticMessage.Create.Arguments.Channel.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }
    }
}