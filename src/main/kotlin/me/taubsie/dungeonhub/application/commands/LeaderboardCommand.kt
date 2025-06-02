package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.pagination.pages.Page
import dev.kordex.core.utils.getLocale
import me.taubsie.dungeonhub.application.connection.copy
import me.taubsie.dungeonhub.application.enums.HelpTopic
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.application.service.LeaderboardService
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.i18n.Translations.Command.Leaderboard
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.carry_type.CarryTypeModel

@LoadExtension
class LeaderboardCommand : Extension() {
    override val name = "leaderboard-command"

    @OptIn(AlwaysPublicResponse::class)
    override suspend fun setup() {
        publicSlashCommand(::LeaderboardArguments) {
            name = Leaderboard.name
            description = Leaderboard.description
            allowInDms = false

            action {
                val carryType: CarryTypeModel? =
                    CarryTypeConnection[guild?.id?.value!!.toLong()].getByIdentifier(arguments.carryType)

                val scoreType: ScoreType = arguments.scoreType ?: ScoreType.Default

                val leaderboardTitle = scoreType.getLeaderboardTitle(carryType, event.getLocale())

                val firstPage = if (carryType != null) {
                    ScoreConnection[carryType]
                        .loadLeaderboard(scoreType, 0, user.id.value.toLong())
                } else {
                    DiscordServerConnection
                        .loadTotalLeaderboard(guild?.id?.value!!.toLong(), scoreType, 0, user.id.value.toLong())
                }

                if (firstPage == null || firstPage.totalPages == 0) {
                    respond {
                        embeds = mutableListOf(LeaderboardService.getEmptyLeaderboardEmbed(leaderboardTitle))
                    }
                    return@action
                }

                respondingPaginator {
                    owner = user

                    for (i in 0..<firstPage.totalPages) {
                        val leaderboardModel = if (carryType != null) {
                            ScoreConnection[carryType].loadLeaderboard(scoreType, i, user.id.value.toLong())
                        } else {
                            DiscordServerConnection.loadTotalLeaderboard(
                                guild?.id?.value!!.toLong(),
                                scoreType,
                                i,
                                user.id.value.toLong()
                            )
                        }

                        page(
                            Page {
                                val embed = LeaderboardService.getLeaderboardEmbed(leaderboardTitle, leaderboardModel)

                                copy(embed)
                            }
                        )
                    }
                }.send()
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("help-score" == event.interaction.componentId)
            }

            action {
                event.interaction.respondEphemeral {
                    //TODO make this generation code a method somewhere

                    val helpTopic = HelpTopic.SCORE

                    val embedBuilder = EmbedBuilder()
                    embedBuilder.title = "**" + helpTopic.title + "**"

                    val helpDisplay = helpTopic.description.getDescription(
                        event.interaction.user,
                        event.interaction.getGuildOrNull()
                    )

                    embedBuilder.color = helpDisplay.embedColor.color
                    embedBuilder.description = helpDisplay.description

                    helpDisplay.fields.forEach { embedBuilder.field(it.key, false) { it.value } }

                    embeds = mutableListOf(embedBuilder)
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("show-score" == event.interaction.componentId)
            }

            action {
                event.interaction.respondEphemeral {
                    embeds = ScoreCommand.generateScoreEmbeds(
                        event.interaction.user,
                        event.interaction.user,
                        event.interaction.guild,
                        event.getLocale()
                    )
                }
            }
        }
    }

    inner class LeaderboardArguments : Arguments() {
        val carryType by optionalString {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val scoreType by optionalEnumChoice<ScoreType> {
            name = "score-type".toKey()
            description = "Select which type of score you want.".toKey()
            typeName = "ScoreType".toKey()
        }
    }
}

fun ActionRowBuilder.addLeaderboardButtons() {
    interactionButton(ButtonStyle.Primary, "help-score") {
        emoji(ReactionEmoji.Unicode("❔"))
        label = "How score is calculated"
    }

    interactionButton(ButtonStyle.Primary, "show-score") {
        emoji(ReactionEmoji.Unicode("\uD83C\uDFAF"))
        label = "Your score"
    }
}