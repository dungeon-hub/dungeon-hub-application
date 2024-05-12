package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.annotations.AlwaysPublicResponse
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.pagination.pages.Page
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
import me.taubsie.dungeonhub.kord.application.service.LeaderboardService
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel
import me.taubsie.dungeonhub.kord.application.connection.copy
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.AutoCompletion

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
                val carryType: CarryTypeModel? = CarryTypeConnection.getInstance(guild?.id?.value!!.toLong())
                    .getByIdentifier(arguments.carryType)?.orElse(null)

                val scoreType: ScoreType = arguments.scoreType ?: ScoreType.DEFAULT

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
                                ScoreConnection.getInstance(carryType)
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
            autoCompleteCallback = AutoCompletion.carryType
        }

        val scoreType by optionalEnumChoice<ScoreType> {
            name = "score-type"
            description = "Select which type of score you want."
            typeName = "ScoreType"
        }
    }
}