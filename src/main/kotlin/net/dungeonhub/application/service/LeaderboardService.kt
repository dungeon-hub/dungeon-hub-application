package net.dungeonhub.application.service

import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.ScoreLeaderboard
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.model.score.ScoreLeaderboardModel
import net.dungeonhub.model.score.ScoreModel
import kotlin.time.ExperimentalTime

@OnStart(priority = StartPriority.POST_BOT)
@OptIn(ExperimentalTime::class)
object LeaderboardService : StartupListener {
    val LEADERBOARD_DESCRIPTION by lazy {
        "To see how score is calculated, use `/help topic:score`.\n" +
                "To check your current score, use ${runBlocking { ApplicationService.getSlashCommandDisplay("score") }}."
    }

    fun getLeaderboardEmbed(title: String?, leaderboardModel: ScoreLeaderboardModel?): EmbedBuilder {
        if (leaderboardModel == null) {
            return getEmptyLeaderboardEmbed(title)
        }

        val embed = embed
        embed.title = title
        embed.description = LEADERBOARD_DESCRIPTION
        embed.color = EmbedColor.Default.color

        // 0 -> starts with 1; 1 -> starts with 11; 2 -> starts with 21; etc.
        var counter = 10 * leaderboardModel.page

        for (score in leaderboardModel.scores) {
            embed.field(
                "#" + ++counter + " Carrier",
                false
            ) { getPlayerScore(score) }
        }

        leaderboardModel.playerScore?.let { playerScore: ScoreModel? ->
            if (leaderboardModel.playerPosition?.let { it != -1 } == true) {
                embed.field(
                    "__**Your rank:**__ #" + (leaderboardModel.playerPosition!! + 1),
                    false
                ) { getPlayerScore(playerScore!!) }
            }
        }

        return embed
    }

    fun getPlayerScore(score: ScoreModel): String {
        return "<@${score.carrier.id}> - ${score.scoreAmount} Score"
    }

    fun getEmptyLeaderboardEmbed(title: String?): EmbedBuilder {
        val embed = embed
        embed.title = title
        embed.color = EmbedColor.Negative.color
        embed.description = """
             No score has been gained yet!
             $LEADERBOARD_DESCRIPTION
             """.trimIndent()
        return embed
    }

    fun generateCompactLeaderboard(scoreLeaderboards: List<ScoreLeaderboard>): List<EmbedBuilder> {
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        embeds.addAll(scoreLeaderboards.windowed(4, 4, true).map { leaderboardWindow ->
            generateSingleCompactLeaderboard(leaderboardWindow)
        })

        return embeds
    }

    private fun generateSingleCompactLeaderboard(scoreLeaderboards: List<ScoreLeaderboard>): EmbedBuilder {
        val result = embed
        result.title = "Leaderboard"
        result.color(EmbedColor.Default)

        var count = 0

        for (leaderboard in scoreLeaderboards) {
            val embed = leaderboard.embed
            result.field(embed.title?.replace("Leaderboard | ", "") ?: "", true) {
                if (embed.fields.isEmpty()) {
                    "No score has been gained yet!"
                } else {
                    embed.fields.joinToString(separator = "\n") { field ->
                        field.name.replace("Carrier", "") + field.value
                    }
                }
            }

            // Add a dummy field as a 3rd entry to space out the entries
            count++
            if (count == 2 && scoreLeaderboards.size > 3) {
                result.field(".", true) { "." }
            }
        }

        return result
    }

    fun generateLeaderboard(scoreLeaderboards: List<ScoreLeaderboard>): List<EmbedBuilder> {
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        for (leaderboard in scoreLeaderboards) {
            val embed = leaderboard.embed
            embed.footer = null
            embed.timestamp = null

            if (!leaderboard.isEmpty) {
                embed.description = null
            }

            embeds.add(embed)
        }

        return embeds
    }
}