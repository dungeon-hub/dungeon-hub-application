package me.taubsie.dungeonhub.application.listener

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.entity.channel.DmChannel
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.components.components
import dev.kordex.core.components.disabledButton
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.components.linkButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.addReaction
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.respond
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.ServerProperty
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadEmbedException
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundWarning
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.LeaderboardService
import me.taubsie.dungeonhub.application.service.color
import net.dungeonhub.connection.*
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.score.ScoreModel
import net.dungeonhub.mojang.connection.MojangConnection
import net.dungeonhub.service.MoshiService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.concurrent.thread
import kotlin.time.toKotlinDuration

@LoadExtension
class MessageListener : Extension() {
    companion object {
        private const val APPROVE_AMOUNT_THRESHOLD: Long = 11
        private const val APPROVE_SCORE_THRESHOLD: Long = 34

        private val CHANNEL_FROM_TRANSCRIPT: Pattern = Pattern.compile("^\\s*Channel: [^(]*\\((?<channel>\\d*)\\)")

        private val logger = LoggerFactory.getLogger(MessageListener::class.java)
    }

    override val name = "message-listener"

    override suspend fun setup() {
        event<MessageCreateEvent> {
            action {
                thread {
                    runBlocking {
                        addReactionToPets(event)

                        logTicket(event)

                        loadSkycryptFromTicket(event)
                    }
                }
            }
        }

        event<MessageUpdateEvent> {
            action {
                thread {
                    runBlocking {
                        logTicket(event)
                    }
                }
            }
        }
    }

    private suspend fun logTicket(event: MessageCreateEvent) {
        logTicket(event.message, event.getGuildOrNull())
    }

    private suspend fun logTicket(event: MessageUpdateEvent) {
        logTicket(
            event.message.asMessage(),
            event.message.getChannelOrNull()?.asChannelOfOrNull<GuildMessageChannel>()?.getGuildOrNull()
        )
    }

    @Synchronized
    private fun logTicket(message: Message, server: Guild?) {
        if (server == null) {
            return
        }

        if (ServerProperty.TRANSCRIPTS_CHANNEL.getValue(server.id.value.toLong()).map { s: String ->
                message.channelId.toString() == s
            }.orElse(false)) {
            val attachments = message.attachments

            if (attachments.size != 1) {
                return
            }

            val attachment = attachments.first()

            val attachmentRequest = Request.Builder().url(attachment.url.toHttpUrl()).build()

            val attachmentData: ByteArray = DungeonHubConnection.executeRawRequest(attachmentRequest)?.result
                ?: throw CommandExecutionException("Couldn't read file data.")

            val content = String(attachmentData, StandardCharsets.UTF_8)

            val channelId: Optional<Long> = content.lines().stream()
                .limit(7)
                .map { obj: String -> obj.trim() }
                .map { input: String ->
                    CHANNEL_FROM_TRANSCRIPT.matcher(
                        input
                    )
                }
                .filter { obj: Matcher -> obj.find() }
                .map { matcher: Matcher ->
                    matcher.group(
                        "channel"
                    )
                }
                .map { s: String -> s.toLong() }
                .findFirst()

            var attachmentLink: String? = null

            val approvingChannel = ServerProperty.LOG_APPROVING_CHANNEL.getValue(server.id.value.toLong())
                .map { s: String ->
                    runBlocking { DiscordConnection.bot?.kordRef?.getChannelOf<TextChannel>(Snowflake(s)) }
                }

            for (queueModel in (QueueConnection.getCarryQueuesByQueueStep(QueueStep.Transcript) ?: HashSet())
                .filter { carryQueueModel: CarryQueueModel ->
                    channelId.map { aLong: Long -> aLong == carryQueueModel.relationId }
                        .orElse(false)
                }) {
                if (attachmentLink == null) {
                    val attachmentUrl = ContentConnection.uploadFile(attachmentData, "{uuid}.html")

                    if (attachmentUrl == null) {
                        logger.error(
                            "Couldn't upload content of attachment on message {}.",
                            message.id
                        )
                        return
                    }

                    attachmentLink = ContentConnection.getCdnUrl(attachmentUrl).toString()

                    thread(start = true) {
                        runBlocking {
                            message.respond {
                                this.content = attachmentLink
                            }
                        }
                    }
                }

                val firstUpdateModel = queueModel.getUpdateModel()
                firstUpdateModel.attachmentLink = attachmentLink

                val updatedModel = QueueConnection.updateQueue(queueModel.id, firstUpdateModel) ?: queueModel

                if ((updatedModel.amount >= APPROVE_AMOUNT_THRESHOLD
                            || updatedModel.calculateScore() >= APPROVE_SCORE_THRESHOLD)
                    && approvingChannel.isPresent
                ) {
                    runBlocking {
                        val createdMessage = approvingChannel.get()
                            .createMessage {
                                val embed = ApplicationService.loadEmbedFromCarryQueue(updatedModel)
                                embed.title = "Accept carry-log?"
                                embed.color = EmbedColor.Default.color

                                embeds = mutableListOf(embed)

                                actionRow {
                                    interactionButton(ButtonStyle.Success, "accept_log") {
                                        label = "Accept"
                                    }

                                    interactionButton(ButtonStyle.Danger, "deny") {
                                        label = "Deny"
                                    }
                                }
                            }

                        thread(start = true) {
                            runBlocking {
                                DiscordConnection.bot?.kordRef
                                    ?.getUser(Snowflake(updatedModel.carrier.id))
                                    ?.dm {
                                        val embed = ApplicationService.embed
                                        embed.color(EmbedColor.Information)
                                        embed.title = "Approval needed"
                                        embed.description =
                                            "Due to the high number of score (${updatedModel.calculateScore()}) or carries (${updatedModel.amount}), your ${updatedModel.carryTier.displayName} - ${updatedModel.carryDifficulty.displayName} log request has to be manually approved by our server's staff team\n" +
                                                    "You will be notified here once it was approved or denied."

                                        embeds = mutableListOf(embed)
                                    }
                            }
                        }

                        val secondUpdateModel = updatedModel.getUpdateModel()

                        secondUpdateModel.queueStep = QueueStep.Approving
                        secondUpdateModel.relationId = createdMessage.id.value.toLong()

                        QueueConnection.updateQueue(updatedModel.id, secondUpdateModel)
                    }
                } else {
                    val updatedScore = QueueConnection.logQueue(updatedModel.id, firstUpdateModel)
                        ?.scoreModels
                        ?.firstOrNull { scoreModel: ScoreModel -> scoreModel.scoreType == ScoreType.Default }
                        ?.scoreAmount
                        ?: (ScoreConnection[updatedModel.carryType].getScore(updatedModel.carrier.id)?.scoreAmount ?: 0)

                    runBlocking {
                        val carrier = DiscordConnection.bot?.kordRef?.getUser(Snowflake(updatedModel.carrier.id))

                        if (carrier != null) {
                            carrier.dm {
                                this.content = "Your carry was logged!\n\n" +
                                        "**Your Updated Score:** $updatedScore"

                                val embed = ApplicationService.loadEmbedFromCarryQueue(updatedModel)
                                embed.title = "Information"
                                embed.color = EmbedColor.Default.color

                                embeds = mutableListOf(embed)
                            }

                            val logChannel = updatedModel.carryTier
                                .carryType
                                .logChannel
                                ?.let { id: Long ->
                                    runBlocking { server.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id)) }
                                }

                            if (logChannel != null) {
                                logger.debug(
                                    "Carry logged: {}",
                                    MoshiService.moshi.adapter(CarryQueueModel::class.java).toJson(updatedModel)
                                )

                                logChannel.createMessage {
                                    val embed = ApplicationService.getEmbed(
                                        updatedModel.time?.toKotlinInstant() ?: Clock.System.now()
                                    )
                                    embed.title = "Carry accepted."
                                    embed.color = EmbedColor.Positive.color
                                    embed.field("Number of carries", true) { updatedModel.amount.toString() }
                                    embed.field("Type of carry", true) {
                                        "${updatedModel.carryTier.displayName} - ${updatedModel.carryDifficulty.displayName}"
                                    }
                                    embed.field("Player", true) {
                                        "<@${updatedModel.player.id}>"
                                    }
                                    embed.field("Carrier", true) {
                                        "<@${updatedModel.carrier.id}>"
                                    }
                                    embed.field("Transcript-Link", true) {
                                        "[Click to open](${updatedModel.attachmentLink})"
                                    }

                                    embeds = mutableListOf(embed)
                                }
                            }

                            QueueConnection.deleteQueue(updatedModel.id)
                        }
                    }
                }

                LeaderboardService.refreshLeaderboard()
            }
        }
    }

    private suspend fun addReactionToPets(event: MessageCreateEvent) {
        if (event.guildId != null) {
            val serverId = event.guildId!!
            val channelId = event.message.channel.id
            if ((serverId.value.toLong() == 1023684107877761196L && channelId.value.toLong() == 1220895875102937098L)
                || (serverId.value.toLong() == 693263712626278553L && channelId.value.toLong() == 1219427157655289908L)
            ) {
                if (event.message.attachments.isEmpty() && event.message.author?.isBot == false &&
                    event.message.embeds.stream()
                        .map { embed -> embed.thumbnail }
                        .filter { it != null }
                        .findFirst().isEmpty
                ) {
                    return
                }

                val emoji: String = getRandomEmoji()

                event.message.addReaction(emoji)
            }
        }
    }

    private fun getEmojiPool(): List<String> {
        return listOf(
            "Woah:1220111116651204608",
            "woah:1220111081150615572",
            "girlwow:1220111157742800956",
            "catelove:1204407157848678430",
            "ZTcool:1204406493353353256",
            "pepega:697756021048868894",
            "smikecate:1204406375791333426",
            "poggorfish:694270485613117480"
        )
    }

    private fun getRandomEmoji(): String {
        val bound = 101.0

        val emojiPool = getEmojiPool()

        val random = Random().nextInt(bound.toInt())

        var emoji = emojiPool[(random * (emojiPool.size / bound)).toInt()]

        if (random == 100) {
            emoji = "swag:708383726370947132"
        }

        return emoji
    }

    private suspend fun loadSkycryptFromTicket(event: MessageCreateEvent) {
        if (event.guildId == null || event.message.channel is DmChannel) {
            return
        }

        val categoryId = event.message.channel.asChannelOfOrNull<CategorizableChannel>()?.categoryId?.value?.toLong()

        if (categoryId != null && !listOf(
                796769131880906782,
                1172688079262330900,
                1168970186666283148,
                992922801075912764,
                992922867857641594,
                1112458713958195250,
                842840550704939053,
                805833601748828200,
                805833672678309908,
                805833843633815664,
                805834037108670464,
                1360864176238493767,
                1360489146321338461,
                1360579229221130421,
                1360575091640897546,
                1364432423345061928,
                1360860377315020941
            ).contains(categoryId)
        ) {
            return
        }

        if (event.message.channel.getMessagesAfter(Snowflake.min, 5).count() != 1) {
            return
        }

        val firstMessage = event.message.channel.messages.reduce { _, message2 -> message2 }

        if (firstMessage.mentionedUsers.count() != 1 || firstMessage.author?.isBot == false) {
            return
        }

        val user = firstMessage.mentionedUsers.first()

        val ign = DiscordUserConnection.getLinkedById(user.id.value.toLong())
            ?.minecraftId
            ?.let { MojangConnection.getNameByUUID(it) }
            ?: return

        sendPlayerDataEmbed(ign, event.message.channel, user.id.value.toLong())
    }

    //TODO threads threads threads
    private suspend fun sendPlayerDataEmbed(ign: String, channel: MessageChannelBehavior, discordId: Long? = null) {
        try {
            channel.createMessage {
                embeds = mutableListOf(ApplicationService.getPlayerDataEmbed(ign, discordId))

                components {
                    linkButton {
                        label = "SkyCrypt".toKey()
                        url = ConfigProperty.SKYCRYPT_API_URL.value + "stats/" + ign
                    }
                }
            }
        } catch (playerNotFoundWarning: PlayerNotFoundWarning) {
            //TODO load scammer data from discord?

            channel.createEmbed { ApplicationService.getErrorEmbed(playerNotFoundWarning) }
        } catch (failedToLoadEmbedException: FailedToLoadEmbedException) {
            channel.createMessage {
                embeds = mutableListOf(failedToLoadEmbedException.embed)

                components(Duration.ofMinutes(5).toKotlinDuration()) {
                    linkButton {
                        label = "SkyCrypt".toKey()
                        url = ConfigProperty.SKYCRYPT_API_URL.value + "stats/" + ign
                    }

                    ephemeralButton {
                        style = ButtonStyle.Secondary
                        label = "Reload".toKey()
                        id = "reload_playerdata"

                        action {
                            edit {
                                components {
                                    linkButton {
                                        label = "SkyCrypt".toKey()
                                        url = ConfigProperty.SKYCRYPT_API_URL.value + "stats/" + ign
                                    }

                                    disabledButton {
                                        style = ButtonStyle.Secondary
                                        label = "Reload".toKey()
                                        id = "reload_playerdata"
                                    }
                                }

                                val embed = ApplicationService.embed
                                embed.description = "Loading..."

                                embeds = mutableListOf(embed)

                                thread {
                                    runBlocking {
                                        components {
                                            embeds = mutableListOf(
                                                try {
                                                    ApplicationService.getPlayerDataEmbed(ign, null)
                                                } catch (failedToLoadAgain: FailedToLoadEmbedException) {
                                                    failedToLoadAgain.embed
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
