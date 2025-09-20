package net.dungeonhub.application.commands

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
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.pagination.pages.Page
import dev.kordex.core.utils.getLocale
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.connection.copy
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.HelpTopic
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.application.service.LeaderboardService
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.i18n.Translations.Command.Leaderboard
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.reputation.ReputationLeaderboardModel
import net.dungeonhub.model.reputation.ReputationSumModel

@LoadExtension
class LeaderboardCommand : Extension() {
    override val name = "leaderboard-command"

    @OptIn(AlwaysPublicResponse::class)
    override suspend fun setup() {
        publicSlashCommand {
            name = Leaderboard.name
            description = Leaderboard.description
            allowInDms = false

            publicSubCommand {
                name = Leaderboard.Reputation.name
                description = Leaderboard.Reputation.description

                action {
                    val leaderboardTitle = "Leaderboard | Reputation"

                    val firstPage = DiscordServerConnection.authenticated()
                        .loadReputationLeaderboard(guild?.id?.value!!.toLong(), 0, user.id.value.toLong())

                    if (firstPage == null || firstPage.totalPages == 0) {
                        respond {
                            embeds = mutableListOf(getEmptyLeaderboardEmbed(leaderboardTitle))
                        }
                        return@action
                    }

                    respondingPaginator {
                        owner = user

                        for (i in 0..<firstPage.totalPages) {
                            val leaderboardModel = DiscordServerConnection.authenticated().loadReputationLeaderboard(
                                guild?.id?.value!!.toLong(),
                                i,
                                user.id.value.toLong()
                            )

                            page(
                                Page {
                                    val embed = getReputationEmbed(leaderboardTitle, leaderboardModel)

                                    copy(embed)
                                }
                            )
                        }
                    }.send()
                }
            }

            publicSubCommand(::ScoreLeaderboardArguments) {
                name = Leaderboard.Score.name
                description = Leaderboard.Score.description

                action {
                    val carryType: CarryTypeModel? =
                        CarryTypeConnection[guild?.id?.value!!.toLong()].authenticated()
                            .getByIdentifier(arguments.carryType)

                    val scoreType: ScoreType = arguments.scoreType ?: ScoreType.Default

                    val leaderboardTitle = scoreType.getLeaderboardTitle(carryType, event.getLocale())

                    val firstPage = if (carryType != null) {
                        ScoreConnection[carryType].authenticated()
                            .loadLeaderboard(scoreType, 0, user.id.value.toLong())
                    } else {
                        DiscordServerConnection.authenticated()
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
                                ScoreConnection[carryType].authenticated()
                                    .loadLeaderboard(scoreType, i, user.id.value.toLong())
                            } else {
                                DiscordServerConnection.authenticated().loadTotalLeaderboard(
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
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot("help-score" == event.interaction.componentId)
            }

            action {
                event.interaction.respondEphemeral {
                    embeds = mutableListOf(
                        HelpTopic.generateHelpEmbed(
                            HelpTopic.SCORE,
                            event.interaction.user,
                            event.interaction.getGuildOrNull()
                        )
                    )
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

    class ScoreLeaderboardArguments : Arguments() {
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

    fun getReputationEmbed(title: String?, leaderboardModel: ReputationLeaderboardModel?): EmbedBuilder {
        if (leaderboardModel == null) {
            return getEmptyLeaderboardEmbed(title)
        }

        val embed = embed
        embed.title = title
        embed.description = leaderboardDescription
        embed.color = EmbedColor.Default.color

        // 0 -> starts with 1; 1 -> starts with 11; 2 -> starts with 21; etc.
        var counter = 10 * leaderboardModel.page

        for (reputationSum in leaderboardModel.reputation) {
            embed.field(
                "#" + ++counter + " Crafter",
                false
            ) { getPlayerScore(reputationSum) }
        }

        leaderboardModel.playerReputation?.let { playerReputation: ReputationSumModel? ->
            if (leaderboardModel.playerPosition?.let { it != -1 } == true) {
                embed.field(
                    "__**Your rank:**__ #" + (leaderboardModel.playerPosition!! + 1),
                    false
                ) { getPlayerScore(playerReputation!!) }
            }
        }

        return embed
    }

    fun getEmptyLeaderboardEmbed(title: String?): EmbedBuilder {
        val embed = embed
        embed.title = title
        embed.color = EmbedColor.Negative.color
        embed.description = """
             No reputation has been gained yet!
             $leaderboardDescription
             """.trimIndent()
        return embed
    }

    fun getPlayerScore(reputation: ReputationSumModel): String {
        return "<@${reputation.user.id}> - ${reputation.amount} reputation"
    }

    companion object {
        private val leaderboardDescription by lazy {
            "Check `/help topic:reputation` to see how you can gain reputation.\n" +
                    "To check your current score, use ${runBlocking { ApplicationService.getSlashCommandDisplay("leaderboard reputation") }}."
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