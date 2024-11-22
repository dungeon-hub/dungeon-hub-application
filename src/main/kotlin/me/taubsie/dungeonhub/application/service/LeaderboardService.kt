package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.ServerProperty
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartPriority
import me.taubsie.dungeonhub.application.loader.StartupListener
import me.taubsie.dungeonhub.application.misc.Leaderboard
import me.taubsie.dungeonhub.application.service.ApplicationService.embed
import me.taubsie.dungeonhub.application.service.ApplicationService.footer
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_type.CarryTypeModel
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
    private const val LEADERBOARD_DESCRIPTION = "To see how score works, use `/help score`"
    private val logger: Logger = LoggerFactory.getLogger(LeaderboardService::class.java)
    private var lastRefresh: Instant? = null

    fun getLeaderboardTitle(carryType: CarryTypeModel?, scoreType: ScoreType): String {
        val suffix = scoreType.leaderboardSuffix ?: ""

        if (carryType == null) {
            return "Leaderboard | Total score$suffix"
        }

        return "Leaderboard | ${carryType.displayName}-Carries$suffix"
    }

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

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                refreshLeaderboard()
            }
        }, Time(System.currentTimeMillis() + 15000), 1000L * 60 * 5)
    }

    private fun refreshLeaderboardInChannel(channel: GuildMessageChannel, leaderboards: List<Leaderboard>) {
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

        if (embeds.isNotEmpty()) {
            if (!leaderboards[0].isEmpty) {
                embeds[0].description = LEADERBOARD_DESCRIPTION
            }

            embeds[embeds.size - 1].footer = footer
            embeds[embeds.size - 1].timestamp = fromEpochMilliseconds(Instant.now().toEpochMilli())
        }

        runBlocking {
            launch {
                val message = channel.messages.filter { message -> message.kord.selfId == message.author?.id }
                    .firstOrNull()

                if (message == null) {
                    channel.createMessage {
                        this.embeds = embeds
                    }
                } else {
                    message.edit {
                        this.embeds = embeds
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
                }

                if (leaderboardChannel == null) {
                    continue
                }

                for (scoreType in ScoreType.entries) {
                    if (scoreType == ScoreType.Event && carryType.isEventActive == false) {
                        continue
                    }

                    if (leaderboards.containsKey(leaderboardChannel.get())) {
                        leaderboards[leaderboardChannel.get()]!!.add(
                            Leaderboard(
                                getLeaderboardTitle(carryType, scoreType),
                                ScoreConnection[carryType].loadLeaderboard(scoreType, 0)
                            )
                        )
                    } else {
                        leaderboards[leaderboardChannel.get()] = mutableListOf(
                            Leaderboard(
                                getLeaderboardTitle(carryType, scoreType),
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
                                    getLeaderboardTitle(null, scoreType),
                                    DiscordServerConnection.loadTotalLeaderboard(serverModel.id, scoreType, 0)
                                )
                            )
                        } else {
                            leaderboards[leaderboardChannel] = mutableListOf(
                                Leaderboard(
                                    getLeaderboardTitle(null, scoreType),
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