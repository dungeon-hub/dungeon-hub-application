package net.dungeonhub.application.listener

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.addReaction
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.event.TicketTranscriptCreatedEvent
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.StaticMessageService
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.score.ScoreModel
import net.dungeonhub.model.ticket.TicketModel
import net.dungeonhub.service.MoshiService
import org.slf4j.LoggerFactory
import java.util.*

@LoadExtension
class MessageListener : Extension() {
    private lateinit var scheduler: Scheduler

    companion object {
        private const val APPROVE_AMOUNT_THRESHOLD: Long = 11
        private const val APPROVE_SCORE_THRESHOLD: Long = 34
        private val mutex = Mutex()

        private val logger = LoggerFactory.getLogger(MessageListener::class.java)
    }

    override val name = "message-listener"
    @OptIn(PrivilegedIntent::class)
    override val intents = mutableSetOf<Intent>(Intent.MessageContent)

    override suspend fun setup() {
        scheduler = DhScheduler()

        event<MessageCreateEvent> {
            action {
                scheduler.launch {
                    addReactionToPets(event)
                }
            }
        }

        event<TicketTranscriptCreatedEvent> {
            action {
                scheduler.launch {
                    logDungeonHubTicket(event.ticket, event.transcriptUrl)
                }
            }
        }
    }

    suspend fun logDungeonHubTicket(ticket: TicketModel, transcriptUrl: String) {
        val server = kord.getGuildOrNull(Snowflake(ticket.ticketPanel.discordServer.id)) ?: return

        val approvingChannel = ServerProperty.LOG_APPROVING_CHANNEL.getValue(server.id.value.toLong())
            ?.let { DiscordConnection.bot.kordRef.getChannelOf<TextChannel>(Snowflake(it)) }

        val carryTypes: MutableList<CarryTypeModel> = mutableListOf()

        mutex.withLock {
            val queueEntries = QueueConnection.authenticated().getCarryQueuesByQueueStep(QueueStep.Transcript)?.filter {
                ticket.channel?.id == it.relationId
            } ?: HashSet()

            // TODO make sure to compact the queue entries both for checking the approval threshold and for sending in DMs and the approval channel --> make sure that timestamps are kept
            for (queueModel in queueEntries) {
                val firstUpdateModel = queueModel.getUpdateModel()
                firstUpdateModel.attachmentLink = transcriptUrl

                val updatedModel =
                    QueueConnection.authenticated().updateQueue(queueModel.id, firstUpdateModel) ?: queueModel

                if ((updatedModel.amount >= APPROVE_AMOUNT_THRESHOLD
                            || updatedModel.calculateScore() >= APPROVE_SCORE_THRESHOLD)
                    && approvingChannel != null
                ) {
                    val createdMessage = approvingChannel.createMessage {
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

                    scheduler.launch {
                        DiscordConnection.bot.kordRef
                            .getUser(Snowflake(updatedModel.carrier.id))
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

                    val secondUpdateModel = updatedModel.getUpdateModel()

                    secondUpdateModel.queueStep = QueueStep.Approving
                    secondUpdateModel.relationId = createdMessage.id.value.toLong()

                    QueueConnection.authenticated().updateQueue(updatedModel.id, secondUpdateModel)
                } else {
                    val loggedCarryModel = QueueConnection.authenticated().logQueue(updatedModel.id, firstUpdateModel) ?: continue

                    val updatedScore = loggedCarryModel.scoreModels
                        .firstOrNull { scoreModel: ScoreModel -> scoreModel.scoreType == ScoreType.Default }
                        ?.scoreAmount
                        ?: (ScoreConnection[updatedModel.carryType].authenticated()
                            .getScore(updatedModel.carrier.id)?.scoreAmount ?: 0)

                    val carrier = DiscordConnection.bot.kordRef.getUser(Snowflake(updatedModel.carrier.id))

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
                                server.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id))
                            }

                        if (logChannel != null) {
                            logger.debug(
                                "Carry logged: {}",
                                MoshiService.moshi.adapter(CarryQueueModel::class.java).toJson(updatedModel)
                            )

                            logChannel.createMessage {
                                val embed = ApplicationService.loadEmbedFromCarry(loggedCarryModel.carryModel)
                                embed.title = "Carry accepted."
                                embed.color = EmbedColor.Positive.color
                                embeds = mutableListOf(embed)
                            }
                        }

                        QueueConnection.authenticated().deleteQueue(updatedModel.id)

                        carryTypes.add(updatedModel.carryType)
                    }
                }
            }
        }

        scheduler.launch {
            StaticMessageService.updateScoreLeaderboard(carryTypes.distinctBy { it.id })
        }
    }

    override suspend fun unload() {
        scheduler.cancel("Extension shutting down.")
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
}
