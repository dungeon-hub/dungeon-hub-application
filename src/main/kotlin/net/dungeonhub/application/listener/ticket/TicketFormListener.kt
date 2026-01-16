package net.dungeonhub.application.listener.ticket

import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.TicketPanelConnection
import net.dungeonhub.enums.FormType
import net.dungeonhub.model.ticket.TicketFormResponseModel
import net.dungeonhub.model.ticket_panel.TicketPanelFormModel
import net.dungeonhub.model.ticket_panel.TicketPanelModel

@LoadExtension
class TicketFormListener : Extension() {
    override val name = "ticket-form-listener"

    override suspend fun setup() {
        event<GuildModalSubmitInteractionCreateEvent> {
            check {
                failIfNot(event.interaction.modalId.startsWith("ticket-form-"))
            }

            action {
                val panelId = event.interaction.modalId.removePrefix("ticket-form-")

                val ticketPanel = panelId.toLongOrNull()?.let {
                    TicketPanelConnection[event.interaction.guildId.value.toLong()].authenticated().getById(it)
                }

                if(!TicketCreateListener.checkTicketOpen(event, ticketPanel, panelId)) return@action

                val responses = mutableListOf<TicketFormResponseModel>()
                var responseCounter = 0
                for (input in event.interaction.textInputs) {
                    val validationResult = validateForm(ticketPanel!!, input.key, input.value.value)

                    if(validationResult != null) {
                        event.interaction.deferEphemeralResponse().respond {
                            addEmbed {
                                title = "Invalid Input"
                                description = validationResult
                                color(EmbedColor.Negative)
                            }
                        }
                        return@action
                    }

                    responses += TicketFormResponseModel(
                        responseCounter++,
                        input.key,
                        input.value.value ?: "no-input"
                    )
                }

                TicketCreateListener.doTicketOpen(event, ticketPanel!!, responses)
            }
        }
    }

    fun findRelatedQuestion(ticketPanel: TicketPanelModel, key: String): TicketPanelFormModel? {
        for(formQuestion in ticketPanel.formQuestions) {
            if(formQuestion.type == FormType.Predefined && formQuestion.data == key) {
                return formQuestion
            }
        }

        // TODO implement for custom questions
        return null
    }

    fun validateForm(ticketPanel: TicketPanelModel, key: String, value: String?): String? {
        val relatedQuestion = findRelatedQuestion(ticketPanel, key) ?: return null

        if(relatedQuestion.type == FormType.Custom) {
            return validateCustomForm(relatedQuestion, value)
        }

        // TODO enum?
        return when(relatedQuestion.data) {
            "carry-difficulty" -> {
                if(value == null) return null // TODO setting about "required"?

                // TODO dedicated endpoint
                val carryTier = DiscordServerConnection.authenticated().getAllCarryTiers(ticketPanel.discordServer.id)?.firstOrNull { carryTier ->
                    carryTier.relatedTicketPanel?.id == ticketPanel.id
                } ?: return "This ticket panel doesn't have a linked carry tier."

                val carryDifficultyConnection = CarryDifficultyConnection[carryTier].authenticated()

                if(carryDifficultyConnection.findCarryDifficultyByString(value) == null) {
                    "The carry difficulty $value does not exist; select one of the following:\n${
                        carryDifficultyConnection.allCarryDifficulties?.joinToString(", ") { it.displayName }
                    }"
                } else null
            }
            "carry-amount" -> {
                if(value?.toIntOrNull() == null || value.toIntOrNull()?.let { it <= 0 } == true) {
                    "The carry amount must be a positive number"
                } else null
            }

            else -> null
        }
    }

    fun validateCustomForm(relatedQuestion: TicketPanelFormModel, value: String?): String? {
        // TODO implement
        return null
    }
}