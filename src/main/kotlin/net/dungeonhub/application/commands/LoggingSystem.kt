package net.dungeonhub.application.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.dm
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.takeWhile
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.exceptions.MissingPermissionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.application.service.PermissionService
import net.dungeonhub.application.service.StaticMessageService
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.i18n.Translations.Command.Log
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.carry_queue.CarryQueueCreationModel
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.carry_tier.CarryTierModel
import net.dungeonhub.model.carry_type.CarryTypeModel
import net.dungeonhub.model.score.ScoreModel
import net.dungeonhub.model.ticket.TicketModel
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.time.ExperimentalTime

@LoadExtension
@OptIn(ExperimentalTime::class)
class LoggingSystem : Extension() {
    override val name = "logging-system"
    private val logger = LoggerFactory.getLogger(LoggingSystem::class.java)

    override suspend fun setup() {
        publicSlashCommand(::LogArguments) {
            name = Log.name
            description = Log.description
            allowInDms = false

            action {
                respond {
                    val ticket = DiscordServerConnection.authenticated().findTickets(guild!!.id.value.toLong(), channelId = channel.id.value.toLong())?.firstOrNull()

                    val carryTier = ticket?.let { getCarryTierFromTicket(guild!!.id.value.toLong(), it) }
                        ?: channel.asChannelOfOrNull<CategorizableChannel>()
                            ?.categoryId
                            ?.let { categoryId ->
                                DiscordServerConnection.authenticated().getCarryTierFromCategory(
                                    guild!!.id.value.toLong(),
                                    categoryId.value.toLong()
                                )
                            }

                    if (carryTier == null) {
                        throw CommandExecutionException("Please use this in a carry-ticket. If this is one, tell the administrators to check [the documentation](https://docs.dungeon-hub.net/) to setup the bot correctly!")
                    }

                    if (QueueConnection.authenticated().getCarryQueueByRelatedIdAndQueueStep(
                            channel.id.value.toLong(),
                            QueueStep.Confirmation
                        )?.firstOrNull() != null
                    ) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Negative.color
                        embed.description = " Someone is already logging this carry.\n" +
                                "If you think this is a mistake, clear the log using the buttons below.\n" +
                                "Otherwise, simply click dismiss to delete this message."

                        embeds = mutableListOf(embed)

                        components {
                            ephemeralButton {
                                label = "Clear log".toKey()
                                style = ButtonStyle.Primary

                                action {
                                    respond innerrespond@{
                                        val carryQueue =
                                            QueueConnection.authenticated().getCarryQueueByRelatedIdAndQueueStep(
                                                channel.id.value.toLong(),
                                                QueueStep.Confirmation
                                            )?.firstOrNull()

                                        if (carryQueue == null) {
                                            val innerEmbed = ApplicationService.embed
                                            innerEmbed.color = EmbedColor.Information.color
                                            innerEmbed.description = "That log request was already cleared."

                                            embeds = mutableListOf(innerEmbed)

                                            return@innerrespond
                                        }

                                        QueueConnection.authenticated().deleteQueue(carryQueue.id)

                                        val innerEmbed = ApplicationService.embed
                                        innerEmbed.color = EmbedColor.Positive.color
                                        innerEmbed.description = "The log request was cleared, you can now log again!"

                                        embeds = mutableListOf(innerEmbed)

                                        message.delete()
                                    }
                                }
                            }

                            ephemeralButton {
                                label = "Dismiss".toKey()
                                style = ButtonStyle.Danger

                                deferredAck = true

                                action {
                                    event.interaction.message.delete()
                                }
                            }
                        }

                        return@respond
                    }

                    val carryDifficulty = CarryDifficultyConnection[carryTier].authenticated()
                        .findCarryDifficultyByString(arguments.carryDifficulty)

                    if (carryDifficulty == null) {
                        embeds = mutableListOf(
                            ApplicationService.getErrorEmbed(
                                InvalidOptionException(
                                    "carry-difficulty",
                                    "`${arguments.carryDifficulty}` is no valid type."
                                )
                            )
                        )
                        return@respond
                    }

                    val carried = if(ticket != null) {
                        ticket.user.id
                    } else {
                        val interactionId = event.interaction.id
                        val messageCount = channel.getMessagesBefore(interactionId, null).count()

                        val firstMessage = try {
                            channel.withStrategy(EntitySupplyStrategy.cachingRest)
                                .getMessagesBefore(event.interaction.id, null)
                                .takeWhile { true }
                                .reduce { message1, message2 -> if (message1.timestamp < message2.timestamp) message1 else message2 }
                        } catch (_: ArrayIndexOutOfBoundsException) {
                            null
                        } catch (_: NoSuchElementException) {
                            null
                        }

                        if (firstMessage == null || try {
                                firstMessage.mentionedUsers.count() != 1
                            } catch (_: ArrayIndexOutOfBoundsException) {
                                false
                            }
                        ) {
                            logger.error("Couldn't load bot message, I only found the message '${firstMessage?.content}' by ${firstMessage?.author?.id} when searching through $messageCount messages that were sent before $interactionId.")
                            throw CommandExecutionException("Couldn't retrieve bot message, so this ticket can't be logged - please retry this.\nIf you still have issues, please report this.")
                        }

                        firstMessage.mentionedUsers.first().id.value.toLong()
                    }

                    val time = Instant.now()

                    val creationModel = CarryQueueCreationModel(
                        queueStep = QueueStep.Confirmation,
                        time = time,
                        amount = arguments.carryAmount,
                        player = carried,
                        carrier = user.id.value.toLong(),
                        relationId = channel.id.value.toLong()
                    )

                    val carryQueueModel = QueueConnection.authenticated().addNewQueue(carryDifficulty, creationModel)
                        ?: throw CommandExecutionException(
                            "Unable to log this. Please contact an administrator of this bot."
                        )

                    val embed = ApplicationService.loadEmbedFromCarryQueue(carryQueueModel)
                    embed.title = "Are you sure that you want to log this?"

                    embeds = mutableListOf(embed)

                    actionRow {
                        interactionButton(ButtonStyle.Success, "send_log") {
                            label = "Confirm"
                        }

                        interactionButton(ButtonStyle.Danger, "discard") {
                            label = "Cancel"
                        }
                    }
                }
            }
        }

        event<GuildButtonInteractionCreateEvent> {
            check {
                failIfNot(listOf("send_log", "discard", "accept_log", "deny").contains(event.interaction.componentId))
            }

            action {
                when (event.interaction.componentId) {
                    "send_log" -> sendLog(event)
                    "discard" -> discard(event)
                    "accept_log" -> acceptLog(event)
                    "deny" -> deny(event)
                }
            }
        }
    }

    fun getCarryTierFromTicket(guildId: Long, ticket: TicketModel): CarryTierModel? {
        // TODO dedicated endpoint
        return DiscordServerConnection.authenticated().getAllCarryTiers(guildId)?.firstOrNull { carryTier ->
            carryTier.relatedTicketPanel == ticket.ticketPanel
        }
    }

    private suspend fun deny(event: GuildButtonInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()

        val message = event.interaction.message

        for (queueModel in QueueConnection.authenticated().getCarryQueueByRelatedIdAndQueueStep(
            message.id.value.toLong(),
            QueueStep.Approving
        ) ?: HashSet()) {
            val carrier = event.kord.getUser(Snowflake(queueModel.carrier.id))

            carrier?.dm {
                content = "Your log was denied by ${event.interaction.user.mention}."

                val embed = ApplicationService.loadEmbedFromCarryQueue(queueModel)
                embed.color = EmbedColor.Negative.color
                embed.title = "Information"

                embeds = mutableListOf(embed)
            }

            ServerProperty.SCORE_LOGS_CHANNEL
                .getValue(event.interaction.guild.id.value.toLong())
                .orElse(null)
                ?.let { id: String ->
                    event.interaction.guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id))
                }
                ?.let { serverTextChannel ->
                    serverTextChannel.createMessage {
                        val embed = ApplicationService.loadEmbedFromCarryQueue(queueModel)
                        embed.color = EmbedColor.Negative.color
                        embed.title = "Carry denied"
                        embed.field("Denied by", true) { event.interaction.user.mention }

                        embeds = mutableListOf(embed)
                    }
                }

            logger.debug("Carry denied: {}", queueModel)

            QueueConnection.authenticated().deleteQueue(queueModel.id)
        }

        message.delete()
    }

    private suspend fun acceptLog(event: GuildButtonInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()

        val message = event.interaction.message

        val carryTypes: MutableList<CarryTypeModel> = mutableListOf()

        for (queueModel: CarryQueueModel in QueueConnection.authenticated()
            .getCarryQueueByRelatedIdAndQueueStep(message.id.value.toLong(), QueueStep.Approving) ?: HashSet()) {
            val updateModel = queueModel.getUpdateModel()
            updateModel.approver = event.interaction.user.id.value.toLong()

            val loggedCarryModel = QueueConnection.authenticated().logQueue(queueModel.id, updateModel)
                ?: return

            carryTypes.add(loggedCarryModel.carryModel.carryType)

            val updatedScore = loggedCarryModel.scoreModels
                .firstOrNull { scoreModel: ScoreModel -> (scoreModel.scoreType == ScoreType.Default) }
                ?.scoreAmount
                ?: (ScoreConnection[loggedCarryModel.carryModel.carryType].authenticated()
                    .getScore(loggedCarryModel.carryModel.carrier.id)?.scoreAmount ?: 0)

            val carrier = event.kord.getUser(Snowflake(loggedCarryModel.carryModel.carrier.id))

            carrier?.dm {
                content = "Your carry was logged!\n\n**Your Updated Score:** $updatedScore"

                val embed = ApplicationService.loadEmbedFromCarry(loggedCarryModel.carryModel)
                embed.title = "Information"
                embed.color = EmbedColor.Default.color

                embeds = mutableListOf(embed)
            }

            try {
                loggedCarryModel.carryModel
                    .carryType
                    .logChannel
                    ?.let { id: Long ->
                        event.interaction.guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id))
                    }
                    ?.let { serverTextChannel ->
                        serverTextChannel.createMessage {
                            val embed = ApplicationService.loadEmbedFromCarry(loggedCarryModel.carryModel)
                            embed.title = "Carry accepted."
                            embed.color = EmbedColor.Positive.color

                            embeds = mutableListOf(embed)
                        }
                    }
            } catch (_: NullPointerException) {
            }

            logger.debug("Carry logged: {}", loggedCarryModel.carryModel)
        }

        StaticMessageService.updateScoreLeaderboard(carryTypes.distinctBy { it.id })

        message.delete()
    }

    private suspend fun sendLog(event: GuildButtonInteractionCreateEvent) {
        val response = event.interaction.deferEphemeralResponse()

        val channel = event.interaction.channel

        val carryQueue = QueueConnection.authenticated()
            .getCarryQueueByRelatedIdAndQueueStep(channel.id.value.toLong(), QueueStep.Confirmation)
            ?.firstOrNull()

        if (carryQueue == null) {
            response.respond {
                embeds = mutableListOf(
                    ApplicationService.getErrorEmbed(CommandExecutionException("Carry isn't in queue anymore. Please discard and log this again!"))
                )
            }
            return
        }

        if (carryQueue.carrier.id != event.interaction.user.id.value.toLong()) {
            response.respond {
                embeds = mutableListOf(
                    ApplicationService.getErrorEmbed(MissingPermissionException())
                )
            }
            return
        }

        val updateModel = carryQueue.getUpdateModel()
        updateModel.queueStep = QueueStep.Transcript

        val carryQueueModel = QueueConnection.authenticated().updateQueue(carryQueue.id, updateModel)

        if (carryQueueModel == null) {
            response.respond {
                content = "Couldn't log this ticket. Please contact an administrator."
            }

            logger.error("Error logging ticket '{}'.", channel.id)
            return
        }

        response.respond {
            content =
                "**Thank you for your service. Your carry will be sent to the staff team for review once the ticket is closed.**\n" +
                        "**You will be notified once it has been reviewed.**"

            channel.createMessage {
                val embed = ApplicationService.loadEmbedFromCarryQueue(carryQueueModel)
                embed.description = "This will get sent when the ticket is deleted.\n" +
                        "If the client doesn't want any more carries, please delete this ticket."
                embed.title = "Carry logged"

                embeds = mutableListOf(embed)
            }

            event.interaction.message.delete()
        }
    }

    private suspend fun discard(event: GuildButtonInteractionCreateEvent) {
        val response = event.interaction.deferEphemeralResponse()

        val channel = event.interaction.channel

        val carryQueue = QueueConnection.authenticated()
            .getCarryQueueByRelatedId(channel.id.value.toLong())
            ?.firstOrNull()

        if (carryQueue == null) {
            response.respond {
                content = "Carry isn't in queue anymore. Please log this again!"
            }

            event.interaction.message.delete("Carry not in queue - weird...")
            return
        }

        if (!PermissionService.mayManageServices(event.interaction.user) && carryQueue.carrier.id != event.interaction.user.id.value.toLong()) {
            response.respond {
                embeds = mutableListOf(ApplicationService.getErrorEmbed(MissingPermissionException()))
            }
            return
        }

        response.respond {
            content = "Log discarded!"
        }

        QueueConnection.authenticated().deleteQueue(carryQueue.id)

        event.interaction.message.delete()
    }

    class LogArguments : Arguments() {
        val carryAmount by int {
            name = Log.Arguments.Amount.name
            description = Log.Arguments.Amount.description
            minValue = 1
            maxValue = 200
        }

        val carryDifficulty by string {
            name = CommonArguments.CarryDifficulty.name
            description = Log.Arguments.CarryDifficulty.description
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }
    }
}