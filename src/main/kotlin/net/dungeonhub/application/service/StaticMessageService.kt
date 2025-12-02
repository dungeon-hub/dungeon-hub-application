package net.dungeonhub.application.service

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.commands.addLeaderboardButtons
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.ScoreLeaderboard
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.application.service.ApplicationService.footer
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.connection.StaticMessageConnection
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.enums.StaticMessageType
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.reputation.ReputationLeaderboardModel
import net.dungeonhub.model.reputation.ReputationSumModel
import net.dungeonhub.model.static_message.StaticMessageModel
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant.Companion.fromEpochMilliseconds

@OnStart(priority = StartPriority.POST_BOT)
object StaticMessageService : StartupListener {
    private val logger = LoggerFactory.getLogger(StaticMessageService::class.java)
    lateinit var scheduler: Scheduler

    val reputationLeaderboardDescription by lazy {
        "Check `/help topic:reputation` to see how you can gain reputation.\n" +
                "To check your current score, use ${runBlocking { ApplicationService.getSlashCommandDisplay("leaderboard reputation") }}."
    }

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        val task = scheduler.schedule(4.hours, startNow = false, name = "Static-Message-Schedule", repeat = true) {
            refreshAllStaticMessages()
        }

        scheduler.launch {
            delay(30.seconds)
            task.callNow()
            task.start()
        }
    }

    // TODO rather use a list and a worker that handles those jobs
    suspend fun refreshAllStaticMessages() {
        val staticMessages = DiscordServerConnection.authenticated().findGlobalStaticMessages()
            ?: return

        for(staticMessage in staticMessages) {
            updateStaticMessage(staticMessage)
        }
    }

    suspend fun updateScoreLeaderboard(carryTypes: List<CarryTypeModel>) {
        for(carryType in carryTypes) {
            updateStaticMessages(carryType.server.id, StaticMessageType.ScoreLeaderboard, listOf(carryType.id))
        }

        val servers = carryTypes.map { it.server.id }.distinct()
        for(server in servers) {
            updateStaticMessages(server, StaticMessageType.TotalLeaderboard, null)
        }
    }

    suspend fun updateStaticMessages(server: Long, staticMessageType: StaticMessageType, objectIds: List<Long>?) {
        var staticMessages = StaticMessageConnection[server].authenticated().findStaticMessage(staticMessageType, null) ?: emptyList()

        if(objectIds != null) {
            staticMessages = staticMessages.filter { staticMessage -> staticMessage.objectIds.any { objectIds.contains(it) } }
        }

        for(staticMessage in staticMessages) {
            updateStaticMessage(staticMessage)
        }
    }

    suspend fun updateStaticMessage(staticMessage: StaticMessageModel) {
        val channel = try {
            DiscordConnection.bot?.kordRef
                ?.getChannel(Snowflake(staticMessage.channelId))
                ?.asChannelOfOrNull<MessageChannel>()
        } catch (_: RestRequestException) {
            null
        }

        if(channel == null) {
            logger.warn("Couldn't find channel with id ${staticMessage.channelId}.")
            return
        }

        val originalMessage = staticMessage.messageId?.let {
            channel.getMessageOrNull(Snowflake(it))
        }

        var updatedStaticMessage = staticMessage

        val message = if(originalMessage != null) {
            originalMessage
        } else {
            val createdMessage = try {
                channel.createMessage {
                    embed {
                        description = "Building static message..."
                    }
                }
            } catch (_: RestRequestException) {
                null
            }

            if(createdMessage != null) {
                val updateModel = staticMessage.getUpdateModel()
                updateModel.messageId = createdMessage.id.value.toLong()
                updatedStaticMessage = StaticMessageConnection[staticMessage.server.id].authenticated().updateStaticMessage(staticMessage.id, updateModel) ?: staticMessage
            }

            createdMessage
        } ?: return

        updateStaticMessage(updatedStaticMessage, message)
    }

    suspend fun updateStaticMessage(staticMessage: StaticMessageModel, message: MessageBehavior) {
        message.edit {
            embeds = getStaticMessageEmbeds(staticMessage)
            setAdditionalMessageProperties(staticMessage)()
        }
    }

    suspend fun sendStaticMessage(connection: StaticMessageConnection, staticMessage: StaticMessageModel, channel: MessageChannelBehavior): StaticMessageModel {
        val sentStaticMessage = channel.createMessage {
            embeds = getStaticMessageEmbeds(staticMessage)
            setAdditionalMessageProperties(staticMessage)()
        }

        val updateModel = staticMessage.getUpdateModel()
        updateModel.messageId = sentStaticMessage.id.value.toLong()

        return connection.updateStaticMessage(staticMessage.id, updateModel)
            ?: throw CommandExecutionException("Couldn't update static message after being sent.")
    }

    fun setAdditionalMessageProperties(staticMessage: StaticMessageModel): MessageBuilder.() -> Unit {
        when (staticMessage.staticMessageType) {
            StaticMessageType.ScoreLeaderboard, StaticMessageType.TotalLeaderboard -> {
                return {
                    actionRow {
                        addLeaderboardButtons()
                    }
                }
            }

            StaticMessageType.ReputationLeaderboard -> {
                return {
                    actionRow {
                        interactionButton(ButtonStyle.Primary, "help-rep") {
                            emoji(ReactionEmoji.Unicode("❔"))
                            label = "How reputation works"
                        }
                    }
                }
            }
        }
    }

    fun getStaticMessageEmbeds(staticMessage: StaticMessageModel): MutableList<EmbedBuilder> {
        when (staticMessage.staticMessageType) {
            StaticMessageType.ScoreLeaderboard -> {
                val carryTypeConnection = CarryTypeConnection[staticMessage.server.id].authenticated()
                val carryTypes = mutableListOf<CarryTypeModel>()

                for(carryTypeId in staticMessage.objectIds) {
                    val carryType = carryTypeConnection.getById(carryTypeId)

                    if (carryType != null) {
                        carryTypes.add(carryType)
                    } else {
                        logger.warn("Couldn't find carry type with id $carryTypeId. Removing it from the score leaderboard ${staticMessage.id}.")
                        val connection = StaticMessageConnection[staticMessage.server.id].authenticated()
                        val currentStaticMessage = connection.getById(staticMessage.id) ?: staticMessage
                        val updateModel = staticMessage.getUpdateModel()
                        updateModel.objectIds = currentStaticMessage.objectIds.filter { it != carryTypeId }
                        connection.updateStaticMessage(staticMessage.id, updateModel)
                    }
                }

                if(carryTypes.isEmpty()) {
                    val embed = embed
                    embed.title = "Leaderboard"
                    embed.description = "Please assign an object to this leaderboard."
                    embed.color(EmbedColor.Negative)
                    return mutableListOf(embed)
                }

                val leaderboards: MutableList<ScoreLeaderboard> = mutableListOf()

                for (carryType in carryTypes) {
                    for (scoreType in ScoreType.entries) {
                        if (scoreType == ScoreType.Event && carryType.isEventActive == false) {
                            continue
                        }

                        leaderboards.add(
                            ScoreLeaderboard(
                                scoreType.getLeaderboardTitle(carryType),
                                ScoreConnection[carryType].authenticated().loadLeaderboard(scoreType, 0)
                            )
                        )
                    }
                }

                return handleScoreLeaderboards(staticMessage.server.id, leaderboards)
            }

            StaticMessageType.TotalLeaderboard -> {
                val leaderboards: MutableList<ScoreLeaderboard> = mutableListOf()

                for (scoreType in ScoreType.entries) {
                    if (scoreType == ScoreType.Event && !ServerProperty.TOTAL_SCORE_EVENT.getValue(staticMessage.server.id).map { it == "true" }.orElse(false)) {
                        continue
                    }

                    leaderboards.add(
                        ScoreLeaderboard(
                            scoreType.getLeaderboardTitle(null),
                            DiscordServerConnection.authenticated().loadTotalLeaderboard(staticMessage.server.id, scoreType, 0)
                        )
                    )
                }

                return handleScoreLeaderboards(staticMessage.server.id, leaderboards)
            }

            StaticMessageType.ReputationLeaderboard -> {
                val embeds = mutableListOf<EmbedBuilder>()

                val discordServerConnection = DiscordServerConnection.authenticated()

                val leaderboardTitle = "Leaderboard | Reputation"

                val leaderboardModel = discordServerConnection.loadReputationLeaderboard(staticMessage.server.id, 0)

                embeds.add(getReputationEmbed(leaderboardTitle, leaderboardModel))
                return embeds
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun handleScoreLeaderboards(guildId: Long, leaderboards: List<ScoreLeaderboard>): MutableList<EmbedBuilder> {
        val embeds = mutableListOf<EmbedBuilder>()

        val compactLeaderboard = ServerProperty.COMPACT_LEADERBOARD.getValue(guildId).map { it == "true" }.orElse(false)

        if (compactLeaderboard) {
            embeds.addAll(LeaderboardService.generateCompactLeaderboard(leaderboards))
        } else {
            embeds.addAll(LeaderboardService.generateLeaderboard(leaderboards))
        }

        if (embeds.isNotEmpty()) {
            if (compactLeaderboard || !leaderboards[0].isEmpty) {
                embeds[0].description = LeaderboardService.LEADERBOARD_DESCRIPTION
            }

            embeds[embeds.size - 1].footer = footer
            embeds[embeds.size - 1].timestamp = fromEpochMilliseconds(Instant.now().toEpochMilli())
        }
        return embeds
    }

    fun getReputationEmbed(title: String?, leaderboardModel: ReputationLeaderboardModel?): EmbedBuilder {
        if (leaderboardModel == null || leaderboardModel.totalPages == 0) {
            return getEmptyReputationLeaderboardEmbed(title)
        }

        val embed = embed
        embed.title = title
        embed.description = reputationLeaderboardDescription
        embed.color = EmbedColor.Default.color

        // 0 -> starts with 1; 1 -> starts with 11; 2 -> starts with 21; etc.
        var counter = 10 * leaderboardModel.page

        for (reputationSum in leaderboardModel.reputation) {
            embed.field(
                "#" + ++counter + " Crafter",
                false
            ) { getPlayerReputation(reputationSum) }
        }

        leaderboardModel.playerReputation?.let { playerReputation: ReputationSumModel? ->
            if (leaderboardModel.playerPosition?.let { it != -1 } == true) {
                embed.field(
                    "__**Your rank:**__ #" + (leaderboardModel.playerPosition!! + 1),
                    false
                ) { getPlayerReputation(playerReputation!!) }
            }
        }

        return embed
    }

    fun getEmptyReputationLeaderboardEmbed(title: String?): EmbedBuilder {
        val embed = embed
        embed.title = title
        embed.color = EmbedColor.Negative.color
        embed.description = """
                 No reputation has been gained yet!
                 $reputationLeaderboardDescription
                 """.trimIndent()
        return embed
    }

    fun getPlayerReputation(reputation: ReputationSumModel): String {
        return "<@${reputation.user.id}> - ${reputation.amount} reputation"
    }
}