package me.taubsie.dungeonhub.application.commands

import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.pagination.pages.Page
import me.taubsie.dungeonhub.application.connection.copy
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.application.service.LeaderboardService
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.score.LeaderboardModel

@LoadExtension
class LeaderboardCommand : Extension() {
    override val name = "leaderboard-command"

    @OptIn(AlwaysPublicResponse::class)
    override suspend fun setup() {
        publicSlashCommand(::LeaderboardArguments) {
            name = "leaderboard"
            description = "Shows you a certain leaderboard."
            allowInDms = false

            action {
                val carryType: CarryTypeModel? =
                    CarryTypeConnection.getInstance(guild?.id?.value!!.toLong())
                        .getByIdentifier(arguments.carryType)?.orElse(null)

                val scoreType: ScoreType = arguments.scoreType ?: ScoreType.Default

                val leaderboardTitle = LeaderboardService.getLeaderboardTitle(carryType, scoreType)

                val firstPage: LeaderboardModel? =
                    if (carryType != null)
                        ScoreConnection.getInstance(carryType)
                            .loadLeaderboard(scoreType, 0, user.id.value.toLong())
                            .orElse(null)
                    else
                        DiscordServerConnection.getInstance()
                            .loadTotalLeaderboard(guild?.id?.value!!.toLong(), scoreType, 0, user.id.value.toLong())
                            .orElse(null)

                if (firstPage == null || firstPage.totalPages == 0) {
                    respond {
                        embeds =
                            mutableListOf(LeaderboardService.getEmptyLeaderboardEmbed(leaderboardTitle))
                    }
                    return@action
                }

                respondingPaginator {
                    owner = user

                    for (i in 0..<firstPage.totalPages) {
                        val leaderboardModel: LeaderboardModel =
                            if (carryType != null)
                                ScoreConnection.getInstance(
                                    carryType
                                )
                                    .loadLeaderboard(scoreType, i, user.id.value.toLong())
                                    .orElse(null)
                            else
                                DiscordServerConnection.getInstance()
                                    .loadTotalLeaderboard(
                                        guild?.id?.value!!.toLong(),
                                        scoreType,
                                        i,
                                        user.id.value.toLong()
                                    )
                                    .orElse(null)

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

    inner class LeaderboardArguments : Arguments() {
        val carryType by optionalString {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val scoreType by optionalEnumChoice<ScoreType> {
            name = "score-type"
            description = "Select which type of score you want."
            typeName = "ScoreType"
        }
    }
}