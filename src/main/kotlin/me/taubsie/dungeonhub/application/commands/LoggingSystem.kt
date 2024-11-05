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
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.QueueConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection
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
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.model.carry_queue.CarryQueueCreationModel
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.carry_queue.CarryQueueUpdateModel
import net.dungeonhub.model.score.LoggedCarryModel
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
                            DiscordServerConnection.getInstance()
                                .getCarryTierFromCategory(guild!!.id.value.toLong(), categoryId.value.toLong())
                        }?.orElse(null)

                    if (carryTier == null) {
                        throw CommandExecutionException("Please use this in a carry-ticket. If this is one, tell the administrators to do `/setup`!")
                    }

                    if (QueueConnection.getInstance()
                            .getCarryQueueByRelatedIdAndQueueStep(channel.id.value.toLong(), QueueStep.Confirmation)
                            .stream()
                            .filter { it != null }
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
                                        val carryQueue =
                                            QueueConnection.getInstance()
                                                .getCarryQueueByRelatedIdAndQueueStep(
                                                    channel.id.value.toLong(),
                                                    QueueStep.Confirmation
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

                                        QueueConnection.getInstance()
                                            .deleteQueue(carryQueue.get().id)

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

                    val carryDifficulty =
                        CarryDifficultyConnection.getInstance(
                            carryTier
                        )
                            .getByIdentifier(arguments.carryDifficulty)

                    if (carryDifficulty.isEmpty) {
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

                    val carryQueueModel =
                        QueueConnection.getInstance()
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
            .getCarryQueueByRelatedIdAndQueueStep(message.id.value.toLong(), QueueStep.Approving)
            .orElse(HashSet<CarryQueueModel>())) {
            val carrier = event.kord.getUser(Snowflake(queueModel.carrier.id))

            //TODO request exception
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

            QueueConnection.getInstance()
                .deleteQueue(queueModel.id)
        }

        message.delete()
    }

    private suspend fun acceptLog(event: GuildButtonInteractionCreateEvent) {
        event.interaction.deferPublicMessageUpdate()

        val message = event.interaction.message

        for (queueModel: CarryQueueModel in QueueConnection.getInstance()
            .getCarryQueueByRelatedIdAndQueueStep(message.id.value.toLong(), QueueStep.Approving)
            .orElse(java.util.HashSet())) {
            val updateModel = queueModel.getUpdateModel()
            updateModel.approver = event.interaction.user.id.value.toLong()

            val loggedCarryModel = QueueConnection.getInstance().logQueue(queueModel.id, updateModel)

            if (loggedCarryModel.isEmpty) {
                return
            }

            val updatedScore = loggedCarryModel.stream()
                .map(LoggedCarryModel::scoreModels)
                .flatMap { obj: List<ScoreModel> -> obj.stream() }
                .filter { scoreModel: ScoreModel -> (scoreModel.scoreType == ScoreType.Default) }
                .findFirst()
                .map { obj: ScoreModel -> obj.scoreAmount }
                .orElseGet {
                    ScoreConnection.getInstance(queueModel.carryType)
                        .getScore(queueModel.carrier.id)
                        .map { obj: ScoreModel -> obj.scoreAmount }
                        .orElse(0L)
                }

            val carrier = event.kord.getUser(Snowflake(queueModel.carrier.id))

            //TODO request exception
            carrier?.dm {
                content = "Your carry was logged!\n\n" +
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
                    ?.let { id: Long ->
                        runBlocking { event.interaction.guild.getChannelOfOrNull<GuildMessageChannel>(Snowflake(id)) }
                    }
                    ?.let { serverTextChannel ->
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
            .getCarryQueueByRelatedIdAndQueueStep(channel.id.value.toLong(), QueueStep.Confirmation).stream()
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

        val updateModel = carryQueue.getUpdateModel()
        updateModel.queueStep = QueueStep.Transcript

        val responder = event.interaction.deferEphemeralResponse()

        val carryQueueModel = QueueConnection.getInstance()
            .updateQueue(carryQueue.id, updateModel)
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

        QueueConnection.getInstance()
            .deleteQueue(carryQueue.id)

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