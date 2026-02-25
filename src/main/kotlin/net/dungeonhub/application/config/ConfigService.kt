package net.dungeonhub.application.config

import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartPriority
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.application.service.ApplicationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.minutes

@OnStart(priority = StartPriority.CONFIGURATION_LOADER)
object ConfigService : ConfigFile<ConfigProperty>() {
    private val logger: Logger = LoggerFactory.getLogger(ConfigService::class.java)
    private lateinit var scheduler: Scheduler

    override val possibleProperties
        get() = ConfigProperty.properties

    private suspend fun resetScheduler() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = DhScheduler()

        scheduler.schedule(5.minutes, startNow = true, name = "Config-Service-Schedule", repeat = true) {
            logger.debug("Config reloaded!")
            reloadConfig()
        }
    }

    val configFolder: String
        get() = ApplicationService.dungeonHubDirectory + File.separator + "config"

    override val configFile: File
        get() = File(configFolder + File.separator + "application_config.properties")

    override suspend fun preStart() {
        reloadConfig()
    }

    override suspend fun onStart() {
        resetScheduler()
    }
}