package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.dungeonhub.application.commands.addLeaderboardButtons
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.Leaderboard
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.application.service.ApplicationService.footer
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.score.LeaderboardModel
import net.dungeonhub.model.score.ScoreModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Time
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletionException

@OnStart(priority = StartPriority.POST_BOT)
object LeaderboardService : StartupListener {
    private const val REFRESH_COOLDOWN: Long = 15L
    private val LEADERBOARD_DESCRIPTION by lazy {
        "To see how score is calculated, use `/help topic:score`.\n" +
                "To check your current score, use ${runBlocking { ApplicationService.getGlobalCommandId("score")?.let { "</score:$it>" } } ?: "`/score`"}."
    }
    private val logger: Logger = LoggerFactory.getLogger(LeaderboardService::class.java)
    private var timer: Timer? = null
    private var lastRefresh: Instant? = null

    fun getLeaderboardEmbed(title: String?, leaderboardModel: LeaderboardModel?): EmbedBuilder {
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

    fun getNextPossibleRefresh(): Long {
        return lastRefresh?.plusSeconds(REFRESH_COOLDOWN)?.epochSecond ?: 0
    }

    override suspend fun postStart() {
        this.lastRefresh = Instant.now().minusSeconds(REFRESH_COOLDOWN + 10L)

        if (timer != null) {
            timer!!.cancel()
        }

        timer = Timer()

        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                refreshLeaderboard()
            }
        }, Time(System.currentTimeMillis() + 15000), 1000L * 60 * 5)
    }

    private fun generateCompactLeaderboard(leaderboards: List<Leaderboard>): List<EmbedBuilder> {
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        embeds.addAll(leaderboards.windowed(4, 4, true).map { leaderboardWindow ->
            generateSingleCompactLeaderboard(leaderboardWindow)
        })

        return embeds
    }

    private fun generateSingleCompactLeaderboard(leaderboards: List<Leaderboard>): EmbedBuilder {
        val result = embed
        result.title = "Leaderboard"
        result.color(EmbedColor.Default)

        var count = 0

        for (leaderboard in leaderboards) {
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
            if (count == 2 && leaderboards.size > 3) {
                result.field(".", true) { "." }
            }
        }

        return result
    }

    private fun generateLeaderboard(leaderboards: List<Leaderboard>): List<EmbedBuilder> {
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        for (leaderboard in leaderboards) {
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

    private fun refreshLeaderboardInChannel(channel: GuildMessageChannel, leaderboards: List<Leaderboard>) {
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        if (ServerProperty.COMPACT_LEADERBOARD.getValue(channel.guildId.value.toLong()).map { it == "true" }
                .orElse(false)) {
            embeds.addAll(generateCompactLeaderboard(leaderboards))
        } else {
            embeds.addAll(generateLeaderboard(leaderboards))
        }

        if (embeds.isNotEmpty()) {
            if (!leaderboards[0].isEmpty) {
                embeds[0].description = LEADERBOARD_DESCRIPTION
            }

            embeds[embeds.size - 1].footer = footer
            embeds[embeds.size - 1].timestamp = fromEpochMilliseconds(Instant.now().toEpochMilli())
        }

        runBlocking {
            launch {
                val message = channel.messages.firstOrNull { message -> message.kord.selfId == message.author?.id }

                if (message == null) {
                    channel.createMessage {
                        this.embeds = embeds
                        actionRow {
                            addLeaderboardButtons()
                        }
                    }
                } else {
                    message.edit {
                        this.embeds = embeds
                        actionRow {
                            addLeaderboardButtons()
                        }
                    }
                }
            }
        }
    }

    /**
     * Doesn't actually refresh the leaderboard, it just suggests that the leaderboard should be refreshed.
     * Cooldown for a refresh is {@value REFRESH_COOLDOWN} seconds.
     */
    fun refreshLeaderboard(): Boolean {
        if (lastRefresh!!.plusSeconds(REFRESH_COOLDOWN - 1L).isAfter(
                Instant.now()
            )
        ) {
            return false
        }

        this.lastRefresh = Instant.now()
        logger.debug("Leaderboard refresh started!")

        val leaderboards: MutableMap<GuildMessageChannel, MutableList<Leaderboard>> = HashMap()

        for (serverModel in DiscordServerConnection.loadAllServers() ?: mutableListOf()) {
            for (carryType in CarryTypeConnection[serverModel.id].allCarryTypes ?: listOf()) {
                val leaderboardChannel = carryType.leaderboardChannel?.let { id: Long? ->
                    runBlocking {
                        try {
                            return@runBlocking Optional.ofNullable(
                                DiscordConnection.bot
                                    ?.kordRef
                                    ?.getChannelOf<GuildMessageChannel>(Snowflake(id!!))
                            )
                        } catch (exception: RequestException) {
                            return@runBlocking Optional.empty()
                        }
                    }
                }?.orElse(null)

                if (leaderboardChannel == null) {
                    continue
                }

                for (scoreType in ScoreType.entries) {
                    if (scoreType == ScoreType.Event && carryType.isEventActive == false) {
                        continue
                    }

                    if (leaderboards.containsKey(leaderboardChannel)) {
                        leaderboards[leaderboardChannel]!!.add(
                            Leaderboard(
                                scoreType.getLeaderboardTitle(carryType),
                                ScoreConnection[carryType].loadLeaderboard(scoreType, 0)
                            )
                        )
                    } else {
                        leaderboards[leaderboardChannel] = mutableListOf(
                            Leaderboard(
                                scoreType.getLeaderboardTitle(carryType),
                                ScoreConnection[carryType].loadLeaderboard(scoreType, 0)
                            )
                        )
                    }
                }
            }

            ServerProperty.TOTAL_SCORE_LEADERBOARD_CHANNEL.getValue(serverModel.id)
                .flatMap { id: String? ->
                    try {
                        runBlocking {
                            try {
                                return@runBlocking Optional.ofNullable(
                                    DiscordConnection.bot
                                        ?.kordRef
                                        ?.getChannelOf<GuildMessageChannel>(Snowflake(id!!))
                                )
                            } catch (exception: RequestException) {
                                return@runBlocking Optional.empty()
                            }
                        }
                    } catch (completionException: CompletionException) {
                        return@flatMap Optional.empty()
                    }
                }.ifPresent { leaderboardChannel: GuildMessageChannel ->
                    for (scoreType in ScoreType.entries) {
                        if (leaderboards.containsKey(leaderboardChannel)) {
                            leaderboards[leaderboardChannel]!!.add(
                                Leaderboard(
                                    scoreType.getLeaderboardTitle(null),
                                    DiscordServerConnection.loadTotalLeaderboard(serverModel.id, scoreType, 0)
                                )
                            )
                        } else {
                            leaderboards[leaderboardChannel] = mutableListOf(
                                Leaderboard(
                                    scoreType.getLeaderboardTitle(null),
                                    DiscordServerConnection.loadTotalLeaderboard(serverModel.id, scoreType, 0)
                                )
                            )
                        }
                    }
                }
        }

        leaderboards.forEach(this::refreshLeaderboardInChannel)

        return true
    }
}