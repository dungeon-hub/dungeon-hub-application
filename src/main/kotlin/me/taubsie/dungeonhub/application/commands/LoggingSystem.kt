package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.ServerProperty
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.exceptions.MissingPermissionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.application.service.LeaderboardService.refreshLeaderboard
import me.taubsie.dungeonhub.application.service.PermissionService
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.connection.ScoreConnection
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_queue.CarryQueueCreationModel
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.score.ScoreModel
import org.slf4j.LoggerFactory
import java.time.Instant

@LoadExtension
class LoggingSystem : Extension() {
    override val name = "logging-system"
    private val logger = LoggerFactory.getLogger(LoggingSystem::class.java)

    override suspend fun setup() {
        publicSlashCommand(::LogArguments) {
            name = "log"
            description = "Use this to log your carries."
            allowInDms = false

            action {
                respond {
                    val carryTier = channel.asChannelOfOrNull<CategorizableChannel>()
                        ?.categoryId
                        ?.let { categoryId ->
                            DiscordServerConnection.getCarryTierFromCategory(
                                guild!!.id.value.toLong(),
                                categoryId.value.toLong()
                            )
                        }

                    if (carryTier == null) {
                        throw CommandExecutionException("Please use this in a carry-ticket. If this is one, tell the administrators to do `/setup`!")
                    }

                    if (QueueConnection.getCarryQueueByRelatedIdAndQueueStep(
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
                                label = "Clear log"
                                style = ButtonStyle.Primary

                                action {
                                    respond innerrespond@{
                                        val carryQueue =
                                            QueueConnection.getCarryQueueByRelatedIdAndQueueStep(
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

                                        QueueConnection.deleteQueue(carryQueue.id)

                                        val innerEmbed = ApplicationService.embed
                                        innerEmbed.color = EmbedColor.Positive.color
                                        innerEmbed.description = "The log request was cleared, you can now log again!"

                                        embeds = mutableListOf(innerEmbed)

                                        message.delete()
                                    }
                                }
                            }

                            ephemeralButton {
                                label = "Dismiss"
                                style = ButtonStyle.Danger

                                deferredAck = true

                                action {
                                    event.interaction.message.delete()
                                }
                            }
                        }

                        return@respond
                    }

                    val carryDifficulty =
                        CarryDifficultyConnection[carryTier].getByIdentifier(arguments.carryDifficulty)

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

                    val time = Instant.now()
                    val carried = firstMessage.mentionedUsers.first()

                    val creationModel = CarryQueueCreationModel(
                        queueStep = QueueStep.Confirmation,
                        time = time,
                        amount = arguments.carryAmount,
                        player = carried.id.value.toLong(),
                        carrier = user.id.value.toLong(),
                        relationId = channel.id.value.toLong()
                    )

                    val carryQueueModel = QueueConnection.addNewQueue(carryDifficulty, creationModel)
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

    private suspend fun deny(event: GuildButtonInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()

        val message = event.interaction.message

        for (queueModel in QueueConnection.getCarryQueueByRelatedIdAndQueueStep(
            message.id.value.toLong(),
            QueueStep.Approving
        ) ?: HashSet()) {
            val carrier = event.kord.getUser(Snowflake(queueModel.carrier.id))

            //TODO request exception
            carrier?.dm {
                content = "Your log was denied by ${event.interaction.user.mention}."

                val embed = ApplicationService.loadEmbedFromCarryQueue(queueModel)
                embed.color = EmbedColor.Negative.color
                embed.title = "Information"

                embeds = mutableListOf(embed)
            }

            ServerProperty.SCORE_LOGS_CHANNEL
                .getValue(event.interaction.guild.id.value.toLong())
                .map { id: String ->
                    runBlocking { event.interaction.guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id)) }
                }
                .ifPresent { serverTextChannel ->
                    runBlocking {
                        serverTextChannel.createMessage {
                            val embed = ApplicationService.loadEmbedFromCarryQueue(queueModel)
                            embed.color = EmbedColor.Negative.color
                            embed.title = "Carry denied"
                            embed.field("Denied by", true) { event.interaction.user.mention }

                            embeds = mutableListOf(embed)
                        }
                    }
                }

            logger.debug("Carry denied: {}", queueModel)

            QueueConnection.deleteQueue(queueModel.id)
        }

        message.delete()
    }

    private suspend fun acceptLog(event: GuildButtonInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()

        val message = event.interaction.message

        for (queueModel: CarryQueueModel in QueueConnection
            .getCarryQueueByRelatedIdAndQueueStep(message.id.value.toLong(), QueueStep.Approving) ?: HashSet()) {
            val updateModel = queueModel.getUpdateModel()
            updateModel.approver = event.interaction.user.id.value.toLong()

            val loggedCarryModel = QueueConnection.logQueue(queueModel.id, updateModel)
                ?: return

            val updatedScore = loggedCarryModel.scoreModels
                .firstOrNull { scoreModel: ScoreModel -> (scoreModel.scoreType == ScoreType.Default) }
                ?.scoreAmount
                ?: (ScoreConnection[loggedCarryModel.carryModel.carryType].getScore(loggedCarryModel.carryModel.carrier.id)?.scoreAmount ?: 0)

            val carrier = event.kord.getUser(Snowflake(loggedCarryModel.carryModel.carrier.id))

            //TODO request exception
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
                        runBlocking { event.interaction.guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id)) }
                    }
                    ?.let { serverTextChannel ->
                        runBlocking {
                            serverTextChannel.createMessage {
                                val embed = ApplicationService.loadEmbedFromCarry(loggedCarryModel.carryModel)
                                embed.title = "Carry accepted."
                                embed.color = EmbedColor.Positive.color

                                embeds = mutableListOf(embed)
                            }
                        }
                    }
            } catch (ignored: NullPointerException) {
            }

            logger.debug("Carry logged: {}", loggedCarryModel.carryModel)
        }

        refreshLeaderboard()

        message.delete()
    }

    private suspend fun sendLog(event: GuildButtonInteractionCreateEvent) {
        val channel = event.interaction.channel

        val carryQueue = QueueConnection
            .getCarryQueueByRelatedIdAndQueueStep(channel.id.value.toLong(), QueueStep.Confirmation)
            ?.firstOrNull()

        if (carryQueue == null) {
            event.interaction.respondEphemeral {
                embeds = mutableListOf(
                    ApplicationService.getErrorEmbed(CommandExecutionException("Carry isn't in queue anymore. Please discard and log this again!"))
                )
            }
            return
        }

        if (carryQueue.carrier.id != event.interaction.user.id.value.toLong()) {
            event.interaction.respondEphemeral {
                embeds = mutableListOf(
                    ApplicationService.getErrorEmbed(MissingPermissionException())
                )
            }
            return
        }

        val updateModel = carryQueue.getUpdateModel()
        updateModel.queueStep = QueueStep.Transcript

        val responder = event.interaction.deferEphemeralResponse()

        val carryQueueModel = QueueConnection.updateQueue(carryQueue.id, updateModel)

        if (carryQueueModel == null) {
            responder.respond {
                content = "Couldn't log this ticket. Please contact an administrator."
            }

            logger.error("Error logging ticket '{}'.", channel.id)
            return
        }

        responder.respond {
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
        val channel = event.interaction.channel

        val carryQueue = QueueConnection
            .getCarryQueueByRelatedId(channel.id.value.toLong())
            ?.firstOrNull()

        if (carryQueue == null) {
            event.interaction.respondEphemeral {
                content = "Carry isn't in queue anymore. Please log this again!"
            }

            event.interaction.message.delete("Carry not in queue - weird...")
            return
        }

        if (!PermissionService.mayManageServices(event.interaction.user) && carryQueue.carrier.id != event.interaction.user.id.value.toLong()) {
            event.interaction.respondEphemeral {
                embeds = mutableListOf(ApplicationService.getErrorEmbed(MissingPermissionException()))
            }
            return
        }

        event.interaction.respondEphemeral {
            content = "Log discarded!"
        }

        QueueConnection.deleteQueue(carryQueue.id)

        event.interaction.message.delete()
    }

    inner class LogArguments : Arguments() {
        val carryAmount by long {
            name = "amount"
            description = "The amount of carries you did."
            minValue = 1
            maxValue = 200
        }

        val carryDifficulty by string {
            name = "carry-difficulty"
            description = "The difficulty of the carry."
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }
    }
}