package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.disabledButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CntRequestConnection
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.common.model.cnt_request.CntRequestCreationModel
import java.time.Instant

class CraftsAndTransfersButtonExtension : Extension() {
    override val name = "send-cnt-message"

    override suspend fun setup() {
        val buttonIds = listOf("<3", "3-5", "5-10", "10-15", "15-20", "20-25", "25-50", "50-100", "100-200", "200-400", "400m+")

        buttonIds.forEach { id ->
            event<GuildButtonInteractionCreateEvent> {
                check {
                    failIfNot(listOf(id).contains(event.interaction.componentId))
                }
                action {
                    val requesterUser = event.interaction.user
                    try {
                        event.interaction.modal("Crafts And Transfers", "${id}Modal") {
                            actionRow {
                                textInput(TextInputStyle.Short, "${id}TextInput1", "Request Description") {}
                            }
                            actionRow {
                                textInput(TextInputStyle.Short, "${id}TextInput2", "Value") {}
                            }
                            actionRow {
                                textInput(TextInputStyle.Short, "${id}TextInput3", "Craft Requirement") {}
                            }
                        }

                        requesterUser.dm {
                            val requesterUserDm = EmbedBuilder()
                            requesterUserDm.title = "test"
                            requesterUserDm.description = "hi"

                            embeds = mutableListOf(requesterUserDm)
                        }
                    } catch (throwable: Throwable) {
                        event.interaction.respondEphemeral {
                            content = "An error occurred while processing your request. Please try again later. Error code 1"
                        }
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(listOf("claim").contains(event.interaction.componentId))
            }
            action {
                val cntRequest =
                    CntRequestConnection
                    .getInstance(event.interaction.guild.id.value.toLong())
                    .findCntRequest(event.interaction.message.id.value.toLong())
                    .orElseThrow{CommandExecutionWarning("CNT request didn't load properly, are you sure this is one?")}

                try {
                    val claimMessage = EmbedBuilder()
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
                        components {
                            actionRow {
                                disabledButton {
                                    style = ButtonStyle.Primary
                                    label = "Claimed"
                                }
                                interactionButton(ButtonStyle.Secondary, "unclaim") {
                                    label = "Unclaim"
                                }
                                interactionButton(ButtonStyle.Secondary, "done") {
                                    label = "Done"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    event.interaction.respondEphemeral {
                        content = "An error occurred while processing your request. Please try again later. Error code 2"
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(listOf("unclaim").contains(event.interaction.componentId))
            }
            check {

            }

            action {
                val unclaimMessage = EmbedBuilder()
                unclaimMessage.title = "Unclaimed!"
                unclaimMessage.description = """
            The request has been unclaimed. It is now available for others to claim.
        """

                event.interaction.message.edit {
                    embeds = mutableListOf(unclaimMessage)
                    components {
                        actionRow {
                            interactionButton(ButtonStyle.Secondary, "claim") {
                                label = "Claim"
                            }
                            interactionButton(ButtonStyle.Secondary, "unclaim") {
                                label = "Unclaim"
                            }
                            interactionButton(ButtonStyle.Secondary, "done") {
                                label = "Done"
                            }
                        }
                    }
                }

                event.interaction.respondEphemeral {
                    content = "You have successfully unclaimed this request."
                }
            }
        }


        buttonIds.forEach { id ->
            event<ModalSubmitInteractionCreateEvent> {
                check {
                    failIfNot(listOf("${id}Modal").contains(event.interaction.modalId))
                }
                action {
                    val requesterUser = event.interaction.user

                    try {
                        val requestDescription = event.interaction.textInputs["${id}TextInput1"]?.value
                        val requestValue = event.interaction.textInputs["${id}TextInput2"]?.value
                        val craftRequirement = event.interaction.textInputs["${id}TextInput3"]?.value

                        val CNTrequestmessage = EmbedBuilder()
                        CNTrequestmessage.title = "Craft and Transfers Request"
                        CNTrequestmessage.description = """ 
                                Description: $requestDescription
                                Value: $requestValue
                                Requirement: $craftRequirement
                            """

                        val messageId = event.interaction.message!!.id

                        event.interaction.respondPublic {
                            embeds = mutableListOf(CNTrequestmessage)

                            actionRow {
                                interactionButton(ButtonStyle.Secondary, "claim") {
                                    label = "Claim"
                                }
                                interactionButton(ButtonStyle.Secondary, "unclaim") {
                                    label = "Unclaim"
                                }
                                interactionButton(ButtonStyle.Secondary, "done") {
                                    label = "Done"
                                }
                            }
                        }

                        val creationModel: CntRequestCreationModel = CntRequestCreationModel(
                            messageId.value.toLong(),
                            requesterUser.id.value.toLong(),
                            null,
                            Instant.now(),
                            requestValue,
                            requestDescription,
                            craftRequirement
                        )
                    } catch (throwable: Throwable) {
                        event.interaction.respondEphemeral {
                            content = "An error occurred while processing your request. Please try again later. Error code 3"
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

                    actionRow {
                        buttonIds.take(5).forEach { id ->
                            interactionButton(ButtonStyle.Secondary, id) {
                                label = when (id) {
                                    "<3" -> "Less than 3m"
                                    "3-5" -> "3m-5m"
                                    "5-10" -> "5m-10m"
                                    "10-15" -> "10m-15m"
                                    "15-20" -> "15m-20m"
                                    else -> id
                                }
                            }
                        }
                    }

                    actionRow {
                        buttonIds.drop(6).forEach { id ->
                            interactionButton(ButtonStyle.Secondary, id) {
                                label = when (id) {
                                    "20-25" -> "20m-25m"
                                    "25-50" -> "25m-50m"
                                    "50-100" -> "50m-100m"
                                    "100-200" -> "100m-200m"
                                    "200-400" -> "200m-400m"
                                    "400+" -> "400m+"
                                    else -> id
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}