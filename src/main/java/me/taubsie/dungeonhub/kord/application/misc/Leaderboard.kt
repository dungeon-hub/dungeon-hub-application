package me.taubsie.dungeonhub.kord.application.misc

import dev.kord.rest.builder.message.EmbedBuilder
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel
import me.taubsie.dungeonhub.kord.application.service.LeaderboardService.getEmptyLeaderboardEmbed
import me.taubsie.dungeonhub.kord.application.service.LeaderboardService.getLeaderboardEmbed

/**
 * This class holds the data for a simple leaderboard without pages.
 * It contains the title of the leaderboard and the score data, to allow easy querying and building.
 */
class Leaderboard(private val leaderboardTitle: String, private val leaderboardModel: LeaderboardModel?) {
    val isEmpty: Boolean
        get() = leaderboardModel == null || leaderboardModel.scores.isEmpty()

    val embed: EmbedBuilder
        get() {
            if (isEmpty) {
                return getEmptyLeaderboardEmbed(leaderboardTitle)
            }

            return getLeaderboardEmbed(leaderboardTitle, leaderboardModel)
        }
}