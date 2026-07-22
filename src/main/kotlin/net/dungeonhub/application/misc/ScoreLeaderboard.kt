package net.dungeonhub.application.misc

import dev.kord.rest.builder.message.EmbedBuilder
import net.dungeonhub.application.service.LeaderboardService.getEmptyLeaderboardEmbed
import net.dungeonhub.application.service.LeaderboardService.getLeaderboardEmbed
import net.dungeonhub.model.score.ScoreLeaderboardModel

/**
 * This class holds the data for a simple leaderboard without pages.
 * It contains the title of the leaderboard and the score data, to allow easy querying and building.
 */
class ScoreLeaderboard(private val leaderboardTitle: String, private val leaderboardModel: ScoreLeaderboardModel?) {
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