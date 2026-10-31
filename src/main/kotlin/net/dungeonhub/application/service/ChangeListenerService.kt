package net.dungeonhub.application.service

import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.connection.ChangeStreamConnection
import net.dungeonhub.connection.ChangeStreamConnection.ChangeStreamEvent
import net.dungeonhub.enums.ChangeEventType
import net.dungeonhub.model.change.ChangeEventModel
import net.dungeonhub.structure.model.Model
import org.slf4j.LoggerFactory

/**
 * Listens to the change stream of the API and forwards every change to the services that react to it.
 * The events are processed one after another, in the order they were sent by the API.
 */
@OnStart(priority = StartPriority.POST_BOT)
object ChangeListenerService : StartupListener {
    private val logger = LoggerFactory.getLogger(ChangeListenerService::class.java)
    private lateinit var scheduler: Scheduler

    private val listenedTypes = setOf(
        ChangeEventType.StaticMessage,
        ChangeEventType.CarryQueue,
        ChangeEventType.CarryType,
        ChangeEventType.CarryTier,
        ChangeEventType.CarryDifficulty,
        ChangeEventType.TicketPanel,
        ChangeEventType.Score,
        ChangeEventType.Reputation
    )

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = DhScheduler()

        scheduler.launch {
            ChangeStreamConnection.authenticated().changes(listenedTypes).collect { event ->
                try {
                    when (event) {
                        is ChangeStreamEvent.Connected -> if (!event.resumed) onResyncRequired()
                        is ChangeStreamEvent.Change -> onChange(event.change)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    logger.error("Couldn't process the change stream event $event.", exception)
                }
            }
        }
    }

    /**
     * Called when changes might have been missed, e.g. on the first connect or after a longer disconnect.
     */
    private suspend fun onResyncRequired() {
        StaticMessageService.refreshAllStaticMessages()
        CarryQueueNotificationService.notifyUnnotifiedQueues()
    }

    private suspend fun onChange(change: ChangeEventModel<out Model>) {
        StaticMessageService.onChange(change)
        CarryQueueNotificationService.onChange(change)
    }
}
