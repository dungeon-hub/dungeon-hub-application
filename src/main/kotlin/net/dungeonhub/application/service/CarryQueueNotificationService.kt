package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.TextChannel
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.connection.QueueConnection
import net.dungeonhub.enums.ChangeAction
import net.dungeonhub.enums.QueueStep
import net.dungeonhub.model.carry_queue.CarryQueueModel
import net.dungeonhub.model.change.ChangeEventModel
import net.dungeonhub.structure.model.Model

/**
 * Sends a notification for carry queues that haven't been notified yet, e.g. carries that were logged in-game.
 */
object CarryQueueNotificationService {
    suspend fun onChange(change: ChangeEventModel<out Model>) {
        if (change.data !is CarryQueueModel || change.action == ChangeAction.Deleted) return

        // the model doesn't contain whether the queue was notified, so the API is asked for the queues that still need it
        notifyUnnotifiedQueues()
    }

    suspend fun notifyUnnotifiedQueues() {
        val carryQueues = QueueConnection.authenticated().getUnnotifiedQueues()?.takeIf { it.isNotEmpty() } ?: return

        for(carryQueue in carryQueues) {
            val channelId = carryQueue.relationId

            if(carryQueue.queueStep == QueueStep.Transcript && channelId != null) {
                val channel = DiscordConnection.bot.kordRef.getChannelOf<TextChannel>(Snowflake(channelId))

                channel?.createMessage {
                    embeds = mutableListOf(
                        ApplicationService.loadTicketNotificationFromCarryQueue(carryQueue)
                    )
                }
            }

            val updateModel = carryQueue.getUpdateModel()
            updateModel.notified = true
            QueueConnection.authenticated().updateQueue(carryQueue.id, updateModel)
        }
    }
}
