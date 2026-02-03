package net.dungeonhub.application.misc

import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DhScheduler : Scheduler() {
    override val coroutineContext = Dispatchers.IO + SupervisorJob()
}