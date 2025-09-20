package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.request.KtorRequestException
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.commands.addLeaderboardButtons
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.ScoreLeaderboard
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.application.service.ApplicationService.footer
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.score.ScoreLeaderboardModel
import net.dungeonhub.model.score.ScoreModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CompletionException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant.Companion.fromEpochMilliseconds

@OnStart(priority = StartPriority.POST_BOT)
@OptIn(ExperimentalTime::class)
object LeaderboardService : StartupListener {
    private const val REFRESH_COOLDOWN: Long = 15L
    private val LEADERBOARD_DESCRIPTION by lazy {
        "To see how score is calculated, use `/help topic:score`.\n" +
                "To check your current score, use ${runBlocking { ApplicationService.getSlashCommandDisplay("score") }}."
    }
    private val logger: Logger = LoggerFactory.getLogger(LeaderboardService::class.java)
    private lateinit var scheduler: Scheduler
    private var lastRefresh: Instant? = null

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

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        this.lastRefresh = Instant.now().minusSeconds(REFRESH_COOLDOWN + 10L)

        val task = scheduler.schedule(15.minutes, startNow = false, name = "Leaderboard-Schedule", repeat = true) {
            refreshLeaderboard()
        }

        scheduler.launch {
            delay(30.seconds)
            task.callNow()
            task.start()
        }
    }

    private fun generateCompactLeaderboard(scoreLeaderboards: List<ScoreLeaderboard>): List<EmbedBuilder> {
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

    private fun generateLeaderboard(scoreLeaderboards: List<ScoreLeaderboard>): List<EmbedBuilder> {
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

    private fun refreshLeaderboardInChannel(channel: GuildMessageChannel, scoreLeaderboards: List<ScoreLeaderboard>) {
        val embeds: MutableList<EmbedBuilder> = mutableListOf()

        if (ServerProperty.COMPACT_LEADERBOARD.getValue(channel.guildId.value.toLong()).map { it == "true" }
                .orElse(false)) {
            embeds.addAll(generateCompactLeaderboard(scoreLeaderboards))
        } else {
            embeds.addAll(generateLeaderboard(scoreLeaderboards))
        }

        if (embeds.isNotEmpty()) {
            if (!scoreLeaderboards[0].isEmpty) {
                embeds[0].description = LEADERBOARD_DESCRIPTION
            }

            embeds[embeds.size - 1].footer = footer
            embeds[embeds.size - 1].timestamp = fromEpochMilliseconds(Instant.now().toEpochMilli())
        }

        scheduler.launch {
            try {
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
            } catch (_: KtorRequestException) {
                // Ignore, this simply means that the leaderboard channel can't be accessed anymore - Should this be handled / logged somewhere?
            }
        }
    }

    /**
     * This doesn't refresh the leaderboard; it just suggests that the leaderboard should be refreshed.
     * Cooldown for a refresh is {@value REFRESH_COOLDOWN} seconds.
     */
    suspend fun refreshLeaderboard(): Boolean {
        if (lastRefresh!!.plusSeconds(REFRESH_COOLDOWN - 1L).isAfter(
                Instant.now()
            )
        ) {
            return false
        }

        this.lastRefresh = Instant.now()
        logger.debug("Leaderboard refresh started!")

        val leaderboards: MutableMap<GuildMessageChannel, MutableList<ScoreLeaderboard>> = HashMap()

        for (serverModel in DiscordServerConnection.authenticated().loadAllServers() ?: mutableListOf()) {
            for (carryType in CarryTypeConnection[serverModel.id].authenticated().allCarryTypes ?: listOf()) {
                val leaderboardChannel = carryType.leaderboardChannel?.let { id: Long? ->
                        try {
                            DiscordConnection.bot
                                ?.kordRef
                                ?.getChannelOf<GuildMessageChannel>(Snowflake(id!!))
                        } catch (_: RequestException) {
                            null
                        }
                }

                if (leaderboardChannel == null) {
                    continue
                }

                for (scoreType in ScoreType.entries) {
                    if (scoreType == ScoreType.Event && carryType.isEventActive == false) {
                        continue
                    }

                    if (leaderboards.containsKey(leaderboardChannel)) {
                        leaderboards[leaderboardChannel]!!.add(
                            ScoreLeaderboard(
                                scoreType.getLeaderboardTitle(carryType),
                                ScoreConnection[carryType].authenticated().loadLeaderboard(scoreType, 0)
                            )
                        )
                    } else {
                        leaderboards[leaderboardChannel] = mutableListOf(
                            ScoreLeaderboard(
                                scoreType.getLeaderboardTitle(carryType),
                                ScoreConnection[carryType].authenticated().loadLeaderboard(scoreType, 0)
                            )
                        )
                    }
                }
            }

            ServerProperty.TOTAL_SCORE_LEADERBOARD_CHANNEL.getValue(serverModel.id)
                .orElse(null)
                ?.let { id ->
                    try {
                        DiscordConnection.bot
                            ?.kordRef
                            ?.getChannelOf<GuildMessageChannel>(Snowflake(id))
                    } catch (_: CompletionException) {
                        null
                    } catch (_: RequestException) {
                        null
                    }
                }
                ?.let { leaderboardChannel: GuildMessageChannel ->
                    for (scoreType in ScoreType.entries) {
                        if (leaderboards.containsKey(leaderboardChannel)) {
                            leaderboards[leaderboardChannel]!!.add(
                                ScoreLeaderboard(
                                    scoreType.getLeaderboardTitle(null),
                                    DiscordServerConnection.authenticated().loadTotalLeaderboard(serverModel.id, scoreType, 0)
                                )
                            )
                        } else {
                            leaderboards[leaderboardChannel] = mutableListOf(
                                ScoreLeaderboard(
                                    scoreType.getLeaderboardTitle(null),
                                    DiscordServerConnection.authenticated().loadTotalLeaderboard(serverModel.id, scoreType, 0)
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