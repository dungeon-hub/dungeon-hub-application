package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.utils.dm
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
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.QueueConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.kord.application.exceptions.MissingPermissionException
import me.taubsie.dungeonhub.common.enums.QueueStep
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueCreationModel
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueUpdateModel
import me.taubsie.dungeonhub.common.model.score.LoggedCarryModel
import me.taubsie.dungeonhub.common.model.score.ScoreModel
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import me.taubsie.dungeonhub.kord.application.service.AutoCompletion
import me.taubsie.dungeonhub.kord.application.service.LeaderboardService.refreshLeaderboard
import me.taubsie.dungeonhub.kord.application.service.PermissionService
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

            action {
                respond {
                    val carryTier = channel.asChannelOfOrNull<CategorizableChannel>()
                        ?.categoryId
                        ?.let { categoryId ->
                            DiscordServerConnection.getInstance()
                                .getCarryTierFromCategory(guild!!.id.value.toLong(), categoryId.value.toLong())
                        }?.orElse(null)

                    if (carryTier == null) {
                        throw CommandExecutionException("Please use this in a carry-ticket. If this is one, tell the administrators to do `/setup`!")
                    }

                    if (QueueConnection.getInstance()
                            .getCarryQueueByRelatedIdAndQueueStep(channel.id.value.toLong(), QueueStep.CONFIRMATION)
                            .stream()
                            .flatMap<CarryQueueModel?> { obj: Set<CarryQueueModel?> -> obj.stream() }
                            .findFirst().isPresent
                    ) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.NEGATIVE.color
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
                                        val carryQueue = QueueConnection.getInstance()
                                            .getCarryQueueByRelatedIdAndQueueStep(
                                                channel.id.value.toLong(),
                                                QueueStep.CONFIRMATION
                                            )
                                            .stream()
                                            .flatMap { obj: Set<CarryQueueModel> -> obj.stream() }
                                            .findFirst()

                                        if (carryQueue.isEmpty) {
                                            val innerEmbed = ApplicationService.embed
                                            innerEmbed.color = EmbedColor.INFORMATION.color
                                            innerEmbed.description = "That log request was already cleared."

                                            embeds = mutableListOf(innerEmbed)

                                            return@innerrespond
                                        }

                                        QueueConnection.getInstance().deleteQueue(carryQueue.get().id)

                                        val innerEmbed = ApplicationService.embed
                                        innerEmbed.color = EmbedColor.POSITIVE.color
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

                    val carryDifficulty = CarryDifficultyConnection.getInstance(carryTier)
                        .getByIdentifier(arguments.carryDifficulty)

                    if (carryDifficulty.isEmpty) {
                        embeds = mutableListOf(
                            ApplicationService.getErrorEmbed(
                                InvalidOptionException(
                                    "carry-difficulty",
                                    "${arguments.carryDifficulty} is no valid type."
                                )
                            )
                        )
                        return@respond
                    }

                    val interactionId = event.interaction.id
                    val messageCount = channel.getMessagesBefore(interactionId, null).count()

                    val firstMessage = try {
                        channel.withStrategy(EntitySupplyStrategy.rest)
                            .getMessagesBefore(event.interaction.id, null)
                            .takeWhile { true }
                            .reduce { message1, message2 -> if (message1.timestamp < message2.timestamp) message1 else message2 }
                    } catch (_: NoSuchElementException) {
                        null
                    }

                    if (firstMessage == null || firstMessage.mentionedUsers.count() != 1) {
                        logger.error("Couldn't load bot message, I only found the message '${firstMessage?.content}' by ${firstMessage?.author?.id} when searching through $messageCount messages that were sent before $interactionId.")
                        throw CommandExecutionException("Couldn't retrieve bot message, so this ticket can't be logged. Please report this.")
                    }

                    val time = Instant.now()
                    val carried = firstMessage.mentionedUsers.first()

                    val creationModel = CarryQueueCreationModel()
                        .setQueueStep(QueueStep.CONFIRMATION)
                        .setTime(time)
                        .setAmount(arguments.carryAmount)
                        .setPlayer(carried.id.value.toLong())
                        .setCarrier(user.id.value.toLong())
                        .setRelationId(channel.id.value.toLong())

                    val carryQueueModel = QueueConnection.getInstance()
                        .addNewQueue(carryDifficulty.get(), creationModel)

                    if (carryQueueModel.isEmpty) {
                        throw CommandExecutionException(
                            "Unable to log this. Please contact an administrator of this bot."
                        )
                    }

                    val embed = ApplicationService.loadEmbedFromCarryQueue(carryQueueModel.get())
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

        for (queueModel in QueueConnection.getInstance()
            .getCarryQueueByRelatedIdAndQueueStep(message.id.value.toLong(), QueueStep.APPROVING)
            .orElse(HashSet<CarryQueueModel>())) {
            val carrier = event.kord.getUser(Snowflake(queueModel.carrier.id))

            carrier?.dm {
                content = "Your log was denied by ${event.interaction.user.mention}."

                val embed = ApplicationService.loadEmbedFromCarryQueue(queueModel)
                embed.color = EmbedColor.NEGATIVE.color
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
                            embed.color = EmbedColor.NEGATIVE.color
                            embed.title = "Carry denied"
                            embed.field("Denied by", true) { event.interaction.user.mention }

                            embeds = mutableListOf(embed)
                        }
                    }
                }

            logger.debug("Carry denied: {}", queueModel)

            QueueConnection.getInstance().deleteQueue(queueModel.id)
        }

        message.delete()
    }

    private suspend fun acceptLog(event: GuildButtonInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()

        val message = event.interaction.message

        for (queueModel: CarryQueueModel in QueueConnection.getInstance()
            .getCarryQueueByRelatedIdAndQueueStep(message.id.value.toLong(), QueueStep.APPROVING)
            .orElse(java.util.HashSet())) {
            val updateModel = CarryQueueUpdateModel()
                .setApprover(event.interaction.user.id.value.toLong())

            val loggedCarryModel = QueueConnection.getInstance()
                .logQueue(queueModel.id, updateModel)

            if (loggedCarryModel.isEmpty) {
                return
            }

            val updatedScore = loggedCarryModel.stream()
                .map(LoggedCarryModel::scoreModels)
                .flatMap { obj: List<ScoreModel> -> obj.stream() }
                .filter { scoreModel: ScoreModel -> (scoreModel.scoreType == ScoreType.DEFAULT) }
                .findFirst()
                .map { obj: ScoreModel -> obj.scoreAmount }
                .orElseGet {
                    ScoreConnection.getInstance(queueModel.carryType)
                        .getScore(queueModel.carrier.id)
                        .map { obj: ScoreModel -> obj.scoreAmount }
                        .orElse(0L)
                }
            val gainedScore = queueModel.calculateScore()

            val carrier = event.kord.getUser(Snowflake(queueModel.carrier.id))

            carrier?.dm {
                content = "Your carry was logged!\n\n" +
                        "**Score gained:** " + gainedScore +
                        "\n**Your Updated Score:** " + updatedScore

                val embed = ApplicationService.loadEmbedFromCarryQueue(queueModel)
                embed.title = "Information"
                embed.color = EmbedColor.DEFAULT.color

                embeds = mutableListOf(embed)
            }

            try {
                loggedCarryModel.get().carryModel
                    .carryType
                    .logChannel
                    .map { id: Long ->
                        runBlocking { event.interaction.guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id)) }
                    }
                    .ifPresent { serverTextChannel ->
                        runBlocking {
                            serverTextChannel.createMessage {
                                val embed = ApplicationService.loadEmbedFromCarry(loggedCarryModel.get().carryModel)
                                embed.title = "Carry accepted."
                                embed.color = EmbedColor.POSITIVE.color

                                embeds = mutableListOf(embed)
                            }
                        }
                    }
            } catch (ignored: NullPointerException) {
            }

            logger.debug("Carry logged: {}", queueModel)
        }

        refreshLeaderboard()

        message.delete()
    }

    private suspend fun sendLog(event: GuildButtonInteractionCreateEvent) {
        val channel = event.interaction.channel

        val carryQueue = QueueConnection.getInstance()
            .getCarryQueueByRelatedIdAndQueueStep(channel.id.value.toLong(), QueueStep.CONFIRMATION).stream()
            .flatMap { obj: Set<CarryQueueModel> -> obj.stream() }
            .findFirst()
            .orElse(null)

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

        val updateModel = CarryQueueUpdateModel()
            .setQueueStep(QueueStep.TRANSCRIPT)

        val responder = event.interaction.deferEphemeralResponse()

        val carryQueueModel = QueueConnection.getInstance().updateQueue(carryQueue.id, updateModel)
        if (carryQueueModel.isEmpty) {
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
                val embed = ApplicationService.loadEmbedFromCarryQueue(carryQueueModel.get())
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

        val carryQueue = QueueConnection.getInstance()
            .getCarryQueueByRelatedId(channel.id.value.toLong())
            .map { obj -> obj.stream() }
            .flatMap { obj -> obj.findFirst() }
            .orElse(null)

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

        QueueConnection.getInstance().deleteQueue(carryQueue.id)

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
            autoCompleteCallback = AutoCompletion.carryDifficulty
        }
    }
}