package me.taubsie.dungeonhub.application.commands

import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.pagination.pages.Page
import me.taubsie.dungeonhub.application.connection.copy
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.application.service.LeaderboardService
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_type.CarryTypeModel

@LoadExtension
class LeaderboardCommand : Extension() {
    override val name = "leaderboard-command"

    @OptIn(AlwaysPublicResponse::class)
    override suspend fun setup() {
        publicSlashCommand(::LeaderboardArguments) {
            name = "leaderboard".toKey()
            description = "Shows you a certain leaderboard.".toKey()
            allowInDms = false

            action {
                val carryType: CarryTypeModel? =
                    CarryTypeConnection[guild?.id?.value!!.toLong()].getByIdentifier(arguments.carryType)

                val scoreType: ScoreType = arguments.scoreType ?: ScoreType.Default

                val leaderboardTitle = scoreType.getLeaderboardTitle(carryType)

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
    }

    inner class LeaderboardArguments : Arguments() {
        val carryType by optionalString {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
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