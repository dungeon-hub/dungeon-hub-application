package net.dungeonhub.application.misc

import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DhScheduler : Scheduler() {
    override val coroutineContext = executor.asCoroutineDispatcher()

    companion object {
        val executor: ExecutorService = Executors.newCachedThreadPool()
    }
}