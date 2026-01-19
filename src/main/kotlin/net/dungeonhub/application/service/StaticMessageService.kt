package net.dungeonhub.application.service

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
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
import dev.kordex.core.utils.from
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.commands.addLeaderboardButtons
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.connection.applyJson
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.ScoreLeaderboard
import net.dungeonhub.application.service.ApplicationService.embed
import net.dungeonhub.application.service.ApplicationService.footer
import net.dungeonhub.connection.*
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.enums.StaticMessageType
import net.dungeonhub.hypixel.service.FormattingService
import net.dungeonhub.model.carry_tier.CarryTierModel
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.reputation.ReputationLeaderboardModel
import net.dungeonhub.model.reputation.ReputationSumModel
import net.dungeonhub.model.static_message.StaticMessageModel
import net.dungeonhub.service.GsonService
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
    val staticMessageUpdates = mutableListOf<StaticMessageModel>()

    val reputationLeaderboardDescription by lazy {
        "Check `/help topic:reputation` to see how you can gain reputation.\n" +
                "To check your current score, use ${runBlocking { ApplicationService.getSlashCommandDisplay("leaderboard reputation") }}."
    }

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        val refreshAllTask = scheduler.schedule(4.hours, startNow = false, name = "Static-Message-Schedule", repeat = true) {
            refreshAllStaticMessages()
        }

        val refreshScheduleTask = scheduler.schedule(3.seconds, startNow = false, name = "Static-Message-Update-Scheduler", repeat = true) {
            staticMessageUpdateWave()
        }

        scheduler.launch {
            delay(20.seconds)
            refreshAllTask.callNow()
            refreshScheduleTask.callNow()
            refreshAllTask.start()
            refreshScheduleTask.start()
        }
    }

    private suspend fun staticMessageUpdateWave() {
        val currentWave = staticMessageUpdates.removeFirstOrNull() ?: return

        refreshStaticMessage(currentWave)
    }

    fun refreshAllStaticMessages() {
        val staticMessages = DiscordServerConnection.authenticated().findGlobalStaticMessages() ?: return

        for(staticMessage in staticMessages) {
            staticMessageUpdates.addLast(staticMessage)
        }
    }

    fun updateStaticMessage(staticMessage: StaticMessageModel) {
        staticMessageUpdates.addFirst(staticMessage)
    }

    fun updateScoreLeaderboard(carryTypes: List<CarryTypeModel>) {
        for(carryType in carryTypes) {
            updateStaticMessages(carryType.server.id, StaticMessageType.ScoreLeaderboard, listOf(carryType.id))
        }

        val servers = carryTypes.map { it.server.id }.distinct()
        for(server in servers) {
            updateStaticMessages(server, StaticMessageType.TotalLeaderboard, null)
        }
    }

    fun updateStaticMessages(server: Long, staticMessageType: StaticMessageType, objectIds: List<Long>?) {
        var staticMessages = StaticMessageConnection[server].authenticated().findStaticMessages(staticMessageType, null) ?: emptyList()

        if(objectIds != null) {
            staticMessages = staticMessages.filter { staticMessage -> staticMessage.objectIds.any { objectIds.contains(it) } }
        }

        for(staticMessage in staticMessages) {
            updateStaticMessage(staticMessage)
        }
    }

    suspend fun refreshStaticMessage(staticMessage: StaticMessageModel) {
        val channel = try {
            DiscordConnection.bot.kordRef
                .getChannel(Snowflake(staticMessage.channelId))
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

        refreshStaticMessage(updatedStaticMessage, message)
    }

    private suspend fun refreshStaticMessage(staticMessage: StaticMessageModel, message: MessageBehavior) {
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

            StaticMessageType.TicketPanel -> {
                val ticketPanels = staticMessage.objectIds.mapNotNull {
                    TicketPanelConnection[staticMessage.server.id].authenticated().getById(it)
                }

                return {
                    for(panels in ticketPanels.windowed(5, 5, true)) {
                        actionRow {
                            for(panel in panels) {
                                interactionButton(ButtonStyle.Primary, "create-ticket-${panel.id}") {
                                    label = panel.displayName ?: panel.name
                                    if(panel.emoji != null) {
                                        val emoji = ReactionEmoji.from(panel.emoji!!)
                                        if(emoji is ReactionEmoji.Unicode) {
                                            emoji(emoji)
                                        } else if(emoji is ReactionEmoji.Custom) {
                                            emoji(emoji)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            StaticMessageType.PriceMessage -> {
                // TODO maybe add a button "load-prices" --> that then opens a modal that lets you enter a number of carries, for which a price is then generated
                return { }
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

            StaticMessageType.TicketPanel -> {
                val embed = EmbedBuilder()

                @Suppress("DEPRECATION")
                val embedOverride = try {
                    staticMessage.embedOverride?.let {
                        GsonService.gson.fromJson(it, JsonObject::class.java)
                    }
                } catch (_: JsonSyntaxException) {
                    null
                }

                embedOverride?.entrySet()?.forEach { entry: Map.Entry<String, JsonElement> ->
                    embed.applyJson(
                        entry.key,
                        entry.value
                    )
                }

                if(staticMessage.objectIds.isEmpty()) {
                    embed.description = "Please assign a ticket panel to this message."
                    embed.color = EmbedColor.Negative.color
                }

                if(embed.title.isNullOrEmpty() && embed.description.isNullOrEmpty()) {
                    embed.description = "Open a ticket using the buttons below."
                }

                return mutableListOf(embed)
            }

            StaticMessageType.PriceMessage -> {
                val serverConnection = DiscordServerConnection.authenticated()
                val allCarryTiers = serverConnection.getAllCarryTiers(staticMessage.server.id) ?: emptyList()

                val carryTiers = staticMessage.objectIds.mapNotNull { id -> allCarryTiers.firstOrNull { it.id == id } }

                if(carryTiers.isEmpty()) {
                    return mutableListOf(
                        buildEmbed {
                            title = "Price Message"
                            description = "Please assign an object to this leaderboard."
                            color(EmbedColor.Negative)
                        }
                    )
                }

                return addPriceFooterToLast(carryTiers.map { getPriceEmbed(it) }).toMutableList()
            }
        }
    }

    fun addPriceFooterToLast(embeds: List<EmbedBuilder>): List<EmbedBuilder> {
        for (embed in embeds) {
            embed.footer { text = "" }
        }

        if (embeds.isNotEmpty()) {
            embeds[embeds.size - 1].footer { text = ApplicationService.priceFooter }
        }

        return embeds
    }

    @OptIn(ExperimentalTime::class)
    fun getPriceEmbed(carryTier: CarryTierModel): EmbedBuilder {
        val carryDifficulties = CarryDifficultyConnection[carryTier].authenticated().allCarryDifficulties ?: listOf()

        val title = "## " + carryTier.priceTitle + "\n"
        val priceDescription = carryTier.priceDescription?.let { s: String -> s + "\n\n" } ?: ""

        val description = title + priceDescription + if (carryDifficulties.isNotEmpty()) {
            carryDifficulties.joinToString("\n") { carryDifficulty ->
                val result = StringBuilder()
                if (carryDifficulty.bulkAmount != null && carryDifficulty.bulkPrice != null) {
                    result.append("\n")
                }

                result.append("**")
                    .append(carryDifficulty.priceName)
                    .append("**: ")

                val priceText = if (carryDifficulty.price != 0
                ) FormattingService.makeNumberReadable(carryDifficulty.price.toLong()) + " coins"
                else "Free"

                result.append(priceText)

                if (carryDifficulty.bulkAmount != null && carryDifficulty.bulkPrice != null) {
                    result.append("\n\\*")
                        .append(FormattingService.makeNumberReadable(carryDifficulty.bulkPrice!!.toLong()))
                        .append(" per carry if you buy ")
                        .append(carryDifficulty.bulkAmount)
                        .append("+ carries.")
                }
                result
            }
        } else {
            "Please add at least one carry difficulty to the carry tier `${carryTier.displayName}`."
        }

        return buildEmbed(null) {
            this.description = description
            color(if (carryDifficulties.isEmpty()) EmbedColor.Negative else EmbedColor.Default)
            carryTier.thumbnailUrl?.let { thumbnail { this.url = it } }
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