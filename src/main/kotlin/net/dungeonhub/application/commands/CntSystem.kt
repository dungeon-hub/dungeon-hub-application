package net.dungeonhub.application.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.components
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.hasPermission
import kotlinx.datetime.Clock
import net.dungeonhub.application.enums.CntRequestType
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.CommandExecutionWarning
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.CntRequestConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.connection.ReputationConnection
import net.dungeonhub.i18n.Translations
import net.dungeonhub.model.cnt_request.CntRequestCreationModel
import net.dungeonhub.model.discord_user.DiscordUserUpdateModel
import net.dungeonhub.model.reputation.ReputationCreationModel
import java.time.Instant

@LoadExtension
class CntSystem : Extension() {
    override val name = "cnt-system"

    override suspend fun setup() {
        CntRequestType.entries.forEach { requestType ->
            event<GuildButtonInteractionCreateEvent> {
                check {
                    failIfNot(requestType.buttonId == event.interaction.componentId)
                }

                action {
                    event.interaction.modal("Crafts And Transfers", requestType.modalId) {
                        actionRow {
                            textInput(TextInputStyle.Short, requestType.descriptionId, "Request Description")
                        }
                        actionRow {
                            textInput(TextInputStyle.Short, requestType.valueId, "Value")
                        }
                        actionRow {
                            textInput(TextInputStyle.Short, requestType.requirementId, "Craft Requirement")
                        }
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_claim" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequest(event.interaction.message.id.value.toLong())
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (cntRequest.claimer != null) {
                    event.interaction.respondEphemeral {
                        content = "This request has already been claimed!"
                    }
                    return@action
                }

                val claimerId = event.interaction.user.id.value.toLong()

                val claimer = DiscordUserConnection.authenticated().getById(claimerId)
                    ?: DiscordUserConnection.authenticated().updateUser(claimerId, DiscordUserUpdateModel(null))
                    ?: throw CommandExecutionException("Couldn't load CNT claimer!")

                val updateModel = cntRequest.getUpdateModel()
                updateModel.claimer = claimer

                val updatedCntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .updateCntRequest(cntRequest.id, updateModel)
                    ?: throw CommandExecutionException("Couldn't update CNT request!")

                val claimMessage = ApplicationService.embedWithoutTimestamp
                claimMessage.title = "Claimed!"
                claimMessage.description = """ 
                    You have claimed a crafts and transfers request.
                    Do NOT visit the requester. 
                    You are not allowed to give collateral.
                """

                event.interaction.respondEphemeral {
                    embeds = mutableListOf(claimMessage)
                }

                val originalMessage = event.interaction.message
                originalMessage.edit {
                    embeds = mutableListOf(ApplicationService.getCntEmbed(updatedCntRequest))

                    components {
                        addClaimedCntButtons()
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_unclaim" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequest(event.interaction.message.id.value.toLong())
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.claimer?.id
                    && !event.interaction.user.hasPermission(Permission.Administrator)
                ) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is claimed by someone else!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                val unclaimMessage = EmbedBuilder()
                unclaimMessage.title = "Unclaimed!"
                unclaimMessage.description = "The request has been unclaimed. It is now available for others to claim."

                val updateModel = cntRequest.getUpdateModel()
                updateModel.claimer = null

                val updatedCntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .updateCntRequest(cntRequest.id, updateModel)
                    ?: throw CommandExecutionException("Couldn't update CNT request!")

                event.interaction.respondEphemeral {
                    embeds = mutableListOf(unclaimMessage)
                }

                event.interaction.message.edit {
                    embeds = mutableListOf(ApplicationService.getCntEmbed(updatedCntRequest))
                    components {
                        addUnclaimedCntButtons()
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_done" == event.interaction.componentId)
            }

            action {
                val cntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .findCntRequest(event.interaction.message.id.value.toLong())
                    ?: throw CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")

                if (event.interaction.user.id.value.toLong() != cntRequest.user.id
                    && !event.interaction.user.hasPermission(Permission.Administrator)
                ) {
                    val embed = ApplicationService
                        .getErrorEmbed(CommandExecutionWarning("The CNT request is not yours!"))

                    event.interaction.respondEphemeral { embeds = mutableListOf(embed) }

                    return@action
                }

                val updateModel = cntRequest.getUpdateModel()
                updateModel.completed = true

                val updatedCntRequest = CntRequestConnection[event.interaction.guild.id.value.toLong()].authenticated()
                    .updateCntRequest(cntRequest.id, updateModel)
                    ?: throw CommandExecutionException("Couldn't update CNT request!")

                event.interaction.respondEphemeral {
                    val embed = ApplicationService.embed
                    embed.description =
                        "Your CNT request is now marked as completed.\n__Thanks for using our services!__"
                    embeds = mutableListOf(embed)
                }

                event.interaction.message.edit {
                    val embed = ApplicationService.getCntEmbed(updatedCntRequest)
                    embed.color(EmbedColor.Positive)
                    embed.description = "### Craft and Transfers Request completed!"

                    embeds = mutableListOf(embed)
                    components {
                        addDoneCntButtons()
                    }
                }
            }
        }

        CntRequestType.entries.forEach { requestType ->
            event<ModalSubmitInteractionCreateEvent> {
                check {
                    failIfNot(requestType.modalId == event.interaction.modalId)
                }

                action {
                    val requesterUser = event.interaction.user

                    val requestDescription = event.interaction.textInputs[requestType.descriptionId]?.value!!
                    val coinValue = event.interaction.textInputs[requestType.valueId]?.value!!
                    val requirement = event.interaction.textInputs[requestType.requirementId]?.value!!

                    val channel = event.interaction.channel
                    if (channel !is GuildMessageChannelBehavior) {
                        event.interaction.respondEphemeral {
                            content = "Please use this on a server, DMs are not supported."
                        }
                        return@action
                    }

                    val channelId =
                        ServerProperty.CNT_MESSAGES_CHANNEL.getValue(channel.guildId.value.toLong()).orElse(null)

                    val responseEmbed = ApplicationService.embed
                    responseEmbed.color(EmbedColor.Default)
                    responseEmbed.description =
                        "Thanks for trusting in our service! I'm now trying to send your CNT request into <#$channelId>"

                    val cntEmbed = ApplicationService.getCntEmbed(
                        requestDescription,
                        coinValue,
                        requirement,
                        Clock.System.now(),
                        requesterUser.id.value.toLong()
                    )

                    val response: Message

                    if (channelId != null) {
                        event.interaction.respondEphemeral {
                            embeds = mutableListOf(responseEmbed)
                        }

                        val cntChannel = channel.guild.getChannelOrNull(Snowflake(channelId))
                            ?.asChannelOfOrNull<GuildMessageChannel>() ?: channel

                        response = cntChannel.createMessage {
                            embeds = mutableListOf(cntEmbed)

                            addUnclaimedCntButtons()
                        }
                    } else {
                        response = event.interaction.deferPublicResponse().respond {
                            embeds = mutableListOf(cntEmbed)

                            addUnclaimedCntButtons()
                        }.message

                    }

                    val messageId = response.id

                    val creationModel = CntRequestCreationModel(
                        messageId.value.toLong(),
                        requesterUser.id.value.toLong(),
                        null,
                        Instant.now(),
                        coinValue,
                        requestDescription,
                        requirement
                    )

                    val embed = CntRequestConnection[channel.guildId.value.toLong()].authenticated()
                        .createCntRequest(creationModel)
                        ?.let { mutableListOf(ApplicationService.getCntEmbed(it)) }
                        ?: ApplicationService.getErrorEmbeds(
                            CommandExecutionException("Could not persist CNT request."),
                            "Could not persist CNT request."
                        )

                    response.edit {
                        embeds = embed
                    }
                }
            }
        }

        publicSlashCommand {
            name = Translations.Command.Rep.name
            description = Translations.Command.Rep.description
            allowInDms = false

            publicSubCommand(::RepAddArguments) {
                name = Translations.Command.Rep.Add.name
                description = Translations.Command.Rep.Add.description

                action {
                    respond {
                        val userToRep = arguments.user.asMemberOrNull(guild!!.id)

                        if (userToRep == null) {
                            addEmbed {
                                description = "That user is not on the server!"
                                color(EmbedColor.Negative)
                            }
                            return@respond
                        }

                        val repCreationModel = ReputationCreationModel(
                            userToRep.id.value.toLong(),
                            user.id.value.toLong(),
                            REPUTATION_VALUE,
                            arguments.reason
                        )

                        val reputation = ReputationConnection[userToRep].authenticated().addReputation(repCreationModel)

                        addEmbed {
                            title = "Rep added"
                            description = "You gave <@${reputation!!.user.id}> ${reputation.amount} rep."
                            color(EmbedColor.Positive)
                        }
                    }
                }
            }
        }
    }

    private fun MessageBuilder.addClaimedCntButtons() {
        actionRow {
            interactionButton(ButtonStyle.Primary, "cnt_claim") {
                disabled = true
                label = "Claimed"
            }

            interactionButton(ButtonStyle.Secondary, "cnt_unclaim") {
                label = "Unclaim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_done") {
                label = "Done"
            }
        }
    }

    private fun MessageBuilder.addUnclaimedCntButtons() {
        actionRow {
            interactionButton(ButtonStyle.Primary, "cnt_claim") {
                label = "Claim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_unclaim") {
                disabled = true
                label = "Unclaim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_done") {
                label = "Done"
            }
        }
    }

    private fun MessageBuilder.addDoneCntButtons() {
        actionRow {
            interactionButton(ButtonStyle.Primary, "cnt_claim") {
                disabled = true
                label = "Claim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_unclaim") {
                disabled = true
                label = "Unclaim"
            }
            interactionButton(ButtonStyle.Secondary, "cnt_done") {
                disabled = true
                label = "Done"
            }
        }
    }

    private class RepAddArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The discord user to rep.".toKey()
        }

        val reason by optionalString {
            name = "reason".toKey()
            description =
                "You can provide an additional reason for the rep, e.g. a certain service they helped you with.".toKey()
        }
    }

    companion object {
        private const val REPUTATION_VALUE = 1
    }
}