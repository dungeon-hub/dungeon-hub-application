package me.taubsie.dungeonhub.application.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.HelpTopic
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.misc.HelpDisplay
import me.taubsie.dungeonhub.application.service.ApplicationService

@LoadExtension
class HelpCommand : Extension() {
    override val name = "help-command"

    override suspend fun setup() {
        publicSlashCommand(::HelpArguments) {
            name = "help"
            description = "List of available commands."

            action {
                respond {
                    embeds =
                        mutableListOf(returnEmbed(user.asUserOrNull(), guild?.asGuildOrNull(), arguments.helpTopic))
                }
            }
        }

        event<ButtonInteractionCreateEvent> {
            check {
                failIf {
                    event.interaction.componentId != "show_help_linking"
                }
            }

            action {
                val helpTopic = HelpTopic.VERIFICATION
                val embedBuilder = EmbedBuilder()
                embedBuilder.title = "**" + helpTopic.title + "**"

                val helpDisplay = helpTopic.description.getDescription(
                    event.interaction.user,
                    event.interaction.message.getGuildOrNull()
                )

                embedBuilder.color = helpDisplay.embedColor.color
                embedBuilder.description = helpDisplay.description

                helpDisplay.fields.forEach { (name: String?, value: String?) ->
                    embedBuilder.field(name, false) { value }
                }

                event.interaction.respondEphemeral {
                    embeds = mutableListOf(embedBuilder)
                }
            }
        }
    }

    fun returnEmbed(user: User?, guild: Guild?, helpTopic: HelpTopic?): EmbedBuilder {
        val embed: EmbedBuilder = ApplicationService.embed

        if (helpTopic == null) {
            embed.title = "**Bot Usage:**"
            embed.color = EmbedColor.DEFAULT.color
            embed.description = """
                            This bot uses slash commands, in order to use it you must have your discord client updated (No need to worry if you're on desktop).
                                                    
                            **Usage:** 
                            `/help <topic>` - Displays more information about the selected topic, e.g. the score system.
                            `/log <amount> <carry-type (e.g. Completion | S | S+ | Tier 3 | Tier 4)>` - Run this inside the ticket you are logging to log your carries and earn score.
                            `/score` - Displays your current score.
                            `/leaderboard <leaderboard>` - Shows a leaderboard containing either the current or the all-time score.
                            `/calc-price <type> <tier> <amount>` - Calculates the price of carries.
                            """.trimIndent()

            return embed
        }

        embed.title = "**" + helpTopic.title + "**"

        val helpDisplay: HelpDisplay = helpTopic.description.getDescription(user!!, guild)
        embed.description = helpDisplay.description
        embed.color = helpDisplay.embedColor.color
        helpDisplay.fields.forEach { (name: String?, value: String?) ->
            embed.field(name, false) { value }
        }

        return embed
    }

    inner class HelpArguments : Arguments() {
        val helpTopic by optionalEnumChoice<HelpTopic> {
            name = "topic"
            description = "Select what topic you need help with."
            typeName = "HelpTopic"
        }
    }
}