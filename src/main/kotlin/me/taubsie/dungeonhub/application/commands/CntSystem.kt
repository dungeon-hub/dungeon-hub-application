package me.taubsie.dungeonhub.application.commands

import com.google.common.collect.Iterables
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.channel.GuildChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.components.components
import dev.kordex.core.components.disabledButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import kotlinx.datetime.Clock
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CntRequestConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.enums.CntRequestType
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.common.model.cnt_request.CntRequestCreationModel
import me.taubsie.dungeonhub.common.model.cnt_request.CntRequestUpdateModel
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel
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
                    val requesterUser = event.interaction.user
                    try {
                        event.interaction.modal("Crafts And Transfers", requestType.modalId) {
                            actionRow {
                                textInput(TextInputStyle.Short, requestType.descriptionId, "Request Description") {}
                            }
                            actionRow {
                                textInput(TextInputStyle.Short, requestType.valueId, "Value") {}
                            }
                            actionRow {
                                textInput(TextInputStyle.Short, requestType.requirementId, "Craft Requirement") {}
                            }
                        }

                        //TODO is this needed?
                        requesterUser.dm {
                            val requesterUserDm = EmbedBuilder()
                            requesterUserDm.title = "test"
                            requesterUserDm.description = "hi"

                            embeds = mutableListOf(requesterUserDm)
                        }
                    } catch (throwable: Throwable) {
                        event.interaction.respondEphemeral {
                            content =
                                "An error occurred while processing your request. Please try again later. Error code 1"
                            embeds = ApplicationService.getErrorEmbeds(throwable, "An error occured while processing your CNT request.")
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
                try {
                    val cntRequest = CntRequestConnection
                        .getInstance(event.interaction.guild.id.value.toLong())
                        .findCntRequest(event.interaction.message.id.value.toLong())
                        .orElseThrow { CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?") }

                    if(cntRequest.claimer != null) {
                        event.interaction.respondEphemeral {
                            content = "This request has already been claimed!"
                        }
                        return@action
                    }

                    val claimerId = event.interaction.user.id.value.toLong()

                    val claimer = DiscordUserConnection.getInstance().getById(claimerId).or {
                        DiscordUserConnection.getInstance().updateUser(claimerId, DiscordUserUpdateModel())
                    }.orElseThrow { CommandExecutionException("Couldn't load CNT claimer!") }

                    val updateModel = CntRequestUpdateModel.builder()
                        .claimer(claimer)
                        .build()

                    val updatedCntRequest = CntRequestConnection.getInstance(event.interaction.guild.id.value.toLong())
                        .updateCntRequest(cntRequest.id, updateModel)
                        .orElseThrow { CommandExecutionException("Couldn't update CNT request!") }

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
                            actionRow {
                                disabledButton {
                                    style = ButtonStyle.Primary
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
                    }
                } catch (e: Exception) {
                    event.interaction.respondEphemeral {
                        embeds = ApplicationService.getErrorEmbeds(e, "An error occured: ${e.message}")
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("cnt_unclaim" == event.interaction.componentId)
            }

            action {
                //TODO check if cnt is currently claimed by issuer

                val cntRequest = CntRequestConnection
                    .getInstance(event.interaction.guild.id.value.toLong())
                    .findCntRequest(event.interaction.message.id.value.toLong())
                    .orElseThrow { CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?") }

                val unclaimMessage = EmbedBuilder()
                unclaimMessage.title = "Unclaimed!"
                unclaimMessage.description = "The request has been unclaimed. It is now available for others to claim."

                val updatedCntRequest = CntRequestConnection.getInstance(event.interaction.guild.id.value.toLong())
                    .updateCntRequest(cntRequest.id, CntRequestUpdateModel.builder().removeClaimer(true).build())
                    .orElseThrow { CommandExecutionException("Couldn't update CNT request!") }

                event.interaction.respondEphemeral {
                    embeds = mutableListOf(unclaimMessage)
                }

                event.interaction.message.edit {
                    embeds = mutableListOf(ApplicationService.getCntEmbed(updatedCntRequest))
                    components {
                        actionRow {
                            interactionButton(ButtonStyle.Secondary, "cnt_claim") {
                                label = "Claim"
                            }
                            interactionButton(ButtonStyle.Secondary, "cnt_unclaim") {
                                label = "Unclaim"
                            }
                            interactionButton(ButtonStyle.Secondary, "cnt_done") {
                                label = "Done"
                            }
                        }
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

                    try {
                        val requestDescription = event.interaction.textInputs[requestType.descriptionId]?.value!!
                        val coinValue = event.interaction.textInputs[requestType.valueId]?.value!!
                        val requirement = event.interaction.textInputs[requestType.requirementId]?.value!!

                        val cntEmbed = ApplicationService.getCntEmbed(
                            requestDescription,
                            coinValue,
                            requirement,
                            Clock.System.now(),
                            requesterUser.id.value.toLong()
                        )

                        val response = event.interaction.deferPublicResponse().respond {
                            embeds = mutableListOf(cntEmbed)

                            actionRow {
                                interactionButton(ButtonStyle.Secondary, "cnt_claim") {
                                    label = "Claim"
                                }
                                interactionButton(ButtonStyle.Secondary, "cnt_unclaim") {
                                    label = "Unclaim"
                                }
                                interactionButton(ButtonStyle.Secondary, "cnt_done") {
                                    label = "Done"
                                }
                            }
                        }

                        val messageId = response.message.id

                        val creationModel = CntRequestCreationModel(
                            messageId.value.toLong(),
                            requesterUser.id.value.toLong(),
                            null,
                            Instant.now(),
                            coinValue,
                            requestDescription,
                            requirement
                        )

                        val channel = event.interaction.channel
                        if (channel !is GuildChannelBehavior) {
                            event.interaction.respondEphemeral {
                                content = "Please use this on a server, DMs are not supported."
                            }
                            return@action
                        }

                        val embed = CntRequestConnection
                            .getInstance(channel.guildId.value.toLong())
                            .createCntRequest(creationModel)
                            .map { mutableListOf(ApplicationService.getCntEmbed(it)) }
                            .orElseGet { ApplicationService.getErrorEmbeds(CommandExecutionException("Could not persist CNT request."), "Could not persist CNT request.") }

                        response.edit {
                            embeds = embed
                        }
                    } catch (throwable: Throwable) {
                        event.interaction.respondEphemeral {
                            content =
                                "An error occurred while processing your request. Please try again later. Error code 3"
                        }
                    }
                }
            }
        }

        publicSlashCommand {
            name = "send-CNT-message"
            description = "Send the CNT message with this bot!"

            action {
                respond {
                    val CNTCommandMessage = EmbedBuilder()
                    CNTCommandMessage.title = "Crafts and Transfers Request"
                    CNTCommandMessage.description = """
                            Requests will be sent to (add channel here).
                            The craft and transfer service is free, although **collateral is NOT allowed**.
                            Please read (crafts and transfers info) before requesting.
                            Click the buttons below depending on the value of your request.
                        """
                    embeds = mutableListOf(CNTCommandMessage)

                    Iterables.partition(CntRequestType.entries.asIterable(), 5).forEach { requestTypes ->
                        actionRow {
                            requestTypes.forEach { requestType ->
                                interactionButton(ButtonStyle.Secondary, requestType.buttonId) {
                                    label = requestType.description
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}