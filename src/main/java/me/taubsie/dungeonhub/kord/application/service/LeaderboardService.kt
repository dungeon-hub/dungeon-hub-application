package me.taubsie.dungeonhub.kord.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import me.taubsie.dungeonhub.kord.application.misc.Leaderboard
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
import me.taubsie.dungeonhub.common.DungeonHubService
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel
import me.taubsie.dungeonhub.common.model.score.ScoreModel
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty
import me.taubsie.dungeonhub.kord.application.loader.OnStart
import me.taubsie.dungeonhub.kord.application.loader.StartPriority
import me.taubsie.dungeonhub.kord.application.loader.StartupListener
import me.taubsie.dungeonhub.kord.application.service.ApplicationService.embed
import me.taubsie.dungeonhub.kord.application.service.ApplicationService.footer
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
        embed.color = EmbedColor.DEFAULT.color

        var counter = DungeonHubService.getInstance().getOffsetFromPageNumber(leaderboardModel.page)

        for (score in leaderboardModel.scores) {
            embed.field(
                "#" + ++counter + " Carrier",
                false
            ) { getPlayerScore(score!!) }
        }

        leaderboardModel.playerScore.ifPresent { playerScore: ScoreModel? ->
            leaderboardModel.playerPosition.filter { pos -> pos != -1 }.ifPresent { position: Int ->
                embed.field(
                    "__**Your rank:**__ #" + (position + 1),
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
        embed.color = EmbedColor.NEGATIVE.color
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
                val message = channel.messages.filter { message -> message.author?.kord?.selfId == message.author?.id }
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

        for (serverModel in DiscordServerConnection.getInstance().loadAllServers()
            .orElse(mutableListOf())) {
            for (carryType in CarryTypeConnection.getInstance(serverModel.id).allCarryTypes.orElse(listOf<CarryTypeModel>())) {
                val leaderboardChannel = carryType.leaderboardChannel
                    .flatMap { id: Long? ->
                        runBlocking {
                            Optional.ofNullable(
                                DiscordConnection.bot
                                    ?.kordRef
                                    ?.getChannelOf<GuildMessageChannel>(Snowflake(id!!))
                            )
                        }
                    }

                if (leaderboardChannel.isEmpty) {
                    continue
                }

                for (scoreType in ScoreType.entries) {
                    if (scoreType == ScoreType.EVENT && !carryType.isEventActive) {
                        continue
                    }

                    if (leaderboards.containsKey(leaderboardChannel.get())) {
                        leaderboards[leaderboardChannel.get()]!!.add(
                            Leaderboard(
                                getLeaderboardTitle(carryType, scoreType),
                                ScoreConnection.getInstance(carryType).loadLeaderboard(scoreType, 0).orElse(null)
                            )
                        )
                    } else {
                        leaderboards[leaderboardChannel.get()] = mutableListOf(
                            Leaderboard(
                                getLeaderboardTitle(carryType, scoreType),
                                ScoreConnection.getInstance(carryType).loadLeaderboard(scoreType, 0).orElse(null)
                            )
                        )
                    }
                }
            }

            ServerProperty.TOTAL_SCORE_LEADERBOARD_CHANNEL.getValue(serverModel.id)
                .flatMap { id: String? ->
                    try {
                        runBlocking {
                            return@runBlocking Optional.ofNullable(
                                DiscordConnection.bot
                                    ?.kordRef
                                    ?.getChannelOf<GuildMessageChannel>(Snowflake(id!!))
                            )
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
                                    DiscordServerConnection.getInstance()
                                        .loadTotalLeaderboard(serverModel.id, scoreType, 0).orElse(null)
                                )
                            )
                        } else {
                            leaderboards[leaderboardChannel] = mutableListOf(
                                Leaderboard(
                                    getLeaderboardTitle(null, scoreType),
                                    DiscordServerConnection.getInstance()
                                        .loadTotalLeaderboard(serverModel.id, scoreType, 0).orElse(null)
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