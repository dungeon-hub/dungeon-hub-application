package net.dungeonhub.application.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralMessageCommand
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.pagination.pages.Page
import net.dungeonhub.application.connection.copy
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.CommandExecutionWarning
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService.toEmbed
import net.dungeonhub.application.service.StaticMessageService
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.StaticMessageConnection
import net.dungeonhub.enums.StaticMessageType
import net.dungeonhub.i18n.Translations.Command.StaticMessage
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.static_message.StaticMessageCreationModel

// TODO new subcommand to edit the message override
@OptIn(AlwaysPublicResponse::class)
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
                        emptyList(),
                        null
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
                        val embed = staticMessageModel.toEmbed()
                        embed.description = "Static message created: https://discord.com/channels/${sentMessage.server.id}/${channel.id.value.toLong()}/${sentMessage.messageId}"
                        embed.color(EmbedColor.Positive)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::StaticMessageFindArguments) {
                name = StaticMessage.Find.name
                description = StaticMessage.Find.description

                action {
                    val channel = arguments.channel?.asChannelOfOrNull<MessageChannel>()

                    val connection = StaticMessageConnection[guild!!.id.value.toLong()].authenticated()

                    val staticMessages = connection.findStaticMessages(channelId = channel?.id?.value?.toLong())
                        ?: emptyList()

                    if(staticMessages.isEmpty()) {
                        respond {
                            embed {
                                color(EmbedColor.Negative)
                                description = "Couldn't find any static messages."
                            }
                        }
                    }

                    respondingPaginator {
                        owner = this@action.user

                        for (staticMessage in staticMessages) {
                            page(
                                Page {
                                    val embed = staticMessage.toEmbed()

                                    copy(embed)
                                }
                            )
                        }
                    }.send()
                }
            }

            publicSubCommand(::StaticMessageAssignObjectArguments) {
                name = StaticMessage.AssignObject.name
                description = StaticMessage.AssignObject.description

                action {
                    val connection = StaticMessageConnection[guild!!.id.value.toLong()].authenticated()

                    val staticMessage = connection.getById(arguments.id)
                        ?: throw CommandExecutionWarning("Couldn't find static message with the given id ${arguments.id}.")

                    if(staticMessage.objectIds.contains(arguments.objectId)) {
                        throw CommandExecutionWarning("Static message already contains the given object id ${arguments.objectId}.")
                    }

                    val updateModel = staticMessage.getUpdateModel()
                    updateModel.objectIds = (staticMessage.objectIds + arguments.objectId)

                    val updatedStaticMessage = connection.updateStaticMessage(staticMessage.id, updateModel)

                    respond {
                        embed {
                            updatedStaticMessage?.let { copy(it.toEmbed()) }
                            title = "Updated Static Message #${updatedStaticMessage?.id}"
                            color(EmbedColor.Positive)
                        }
                    }

                    updatedStaticMessage?.let {
                        StaticMessageService.updateStaticMessage(it)
                    }
                }
            }

            publicSubCommand(::StaticMessageRemoveObjectArguments) {
                name = StaticMessage.RemoveObject.name
                description = StaticMessage.RemoveObject.description

                action {
                    val connection = StaticMessageConnection[guild!!.id.value.toLong()].authenticated()

                    val staticMessage = connection.getById(arguments.id)
                        ?: throw CommandExecutionWarning("Couldn't find static message with the given id ${arguments.id}.")

                    val updateModel = staticMessage.getUpdateModel()
                    updateModel.objectIds = (staticMessage.objectIds.filter { it != arguments.objectId })

                    val updatedStaticMessage = connection.updateStaticMessage(staticMessage.id, updateModel)

                    respond {
                        embed {
                            updatedStaticMessage?.let { copy(it.toEmbed()) }
                            title = "Updated Static Message #${updatedStaticMessage?.id}"
                            color(EmbedColor.Positive)
                        }
                    }

                    updatedStaticMessage?.let {
                        StaticMessageService.updateStaticMessage(it)
                    }
                }
            }

            publicSubCommand(::StaticMessageEditArguments) {
                name = "edit".toKey() // TODO add translation
                description = "Edit the properties of a static message.".toKey() // TODO add translation

                action {
                    val connection = StaticMessageConnection[guild!!.id.value.toLong()].authenticated()

                    val staticMessage = connection.getById(arguments.id)
                        ?: throw CommandExecutionWarning("Couldn't find static message with the given id ${arguments.id}.")

                    val updateModel = staticMessage.getUpdateModel()
                    updateModel.embedOverride = arguments.embedOverride

                    val updatedStaticMessage = connection.updateStaticMessage(staticMessage.id, updateModel)

                    if(updatedStaticMessage == null) {
                        respond {
                            embed {
                                color(EmbedColor.Negative)
                                description = "Couldn't update static message."
                            }
                        }
                        return@action
                    }

                    respond {
                        addEmbed {
                            copy(updatedStaticMessage.toEmbed())
                            title = "Edited Static Message #${updatedStaticMessage.id}"
                            color(EmbedColor.Positive)
                        }
                    }

                    StaticMessageService.updateStaticMessage(updatedStaticMessage)
                }
            }

            ephemeralMessageCommand {
                name = "Static Message Info".toKey() // TODO add translation

                action {
                    val connection = StaticMessageConnection[guild!!.id.value.toLong()].authenticated()

                    val staticMessage = event.interaction.messages.keys.firstOrNull()?.let {
                        connection.findStaticMessages(messageId = it.value.toLong())
                    }?.firstOrNull()
                        ?: throw CommandExecutionWarning("The message you interacted with is not a static message.")

                    respond {
                        addEmbed {
                            copy(staticMessage.toEmbed())
                            title = "Static Message #${staticMessage.id}"
                            color(EmbedColor.Positive)
                        }
                        actionRow {
                            interactionButton(ButtonStyle.Primary, "update-sm-${staticMessage.id}") {
                                label = "Update Static Message" // TODO add translation
                            }
                        }
                    }
                }
            }

            event<GuildButtonInteractionCreateEvent> {
                check {
                    failIfNot(event.interaction.componentId.startsWith("update-sm-"))
                }

                action {
                    val response = event.interaction.deferEphemeralResponse()

                    val staticMessageId = event.interaction.componentId.removePrefix("update-sm-").toLongOrNull()

                    val connection = StaticMessageConnection[event.interaction.guild.id.value.toLong()].authenticated()

                    val staticMessage = staticMessageId?.let { connection.getById(it) }
                        ?: throw CommandExecutionWarning("Couldn't find static message with id ${staticMessageId}.")

                    response.respond {
                        try {
                            StaticMessageService.refreshStaticMessage(staticMessage)

                            embed {
                                copy(staticMessage.toEmbed())
                                title = "Updating Static Message #${staticMessage.id}"
                                color(EmbedColor.Positive)
                            }
                        } catch (e: Exception) {
                            embed {
                                color(EmbedColor.Negative)
                                description = "Couldn't update static message: ${e.message}"
                            }
                        }
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

    class StaticMessageFindArguments : Arguments() {
        val channel by optionalChannel {
            name = StaticMessage.Find.Arguments.Channel.name
            description = StaticMessage.Find.Arguments.Channel.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }
    }

    class StaticMessageAssignObjectArguments : Arguments() {
        val id by long {
            name = StaticMessage.AssignObject.Arguments.Id.name
            description = StaticMessage.AssignObject.Arguments.Id.description
        }

        val objectId by long {
            name = StaticMessage.AssignObject.Arguments.Object.name
            description = StaticMessage.AssignObject.Arguments.Object.description
        }
    }

    class StaticMessageRemoveObjectArguments : Arguments() {
        val id by long {
            name = StaticMessage.RemoveObject.Arguments.Id.name
            description = StaticMessage.RemoveObject.Arguments.Id.description
        }

        val objectId by long {
            name = StaticMessage.RemoveObject.Arguments.Object.name
            description = StaticMessage.RemoveObject.Arguments.Object.description
        }
    }

    class StaticMessageEditArguments : Arguments() {
        val id by long {
            name = StaticMessage.Edit.Arguments.Id.name
            description = StaticMessage.Edit.Arguments.Id.description
        }

        val embedOverride by string {
            name = StaticMessage.Edit.Arguments.EmbedOverride.name
            description = StaticMessage.Edit.Arguments.EmbedOverride.description
        }
    }
}