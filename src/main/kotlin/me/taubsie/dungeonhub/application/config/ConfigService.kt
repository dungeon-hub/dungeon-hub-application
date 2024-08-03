package me.taubsie.dungeonhub.application.config

import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartPriority
import me.taubsie.dungeonhub.common.DungeonHubService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Time
import java.util.*

@OnStart(priority = StartPriority.CONFIGURATION_LOADER)
object ConfigService : ConfigFile<ConfigProperty>() {
    private val logger: Logger = LoggerFactory.getLogger(ConfigService::class.java)
    private var timer: Timer? = null

    override val possibleProperties
        get() = ConfigProperty.properties

    private fun resetTimer() {
        if (timer != null) {
            timer!!.cancel()
        }

        timer = Timer()

        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                logger.debug("Config reloaded!")
                reloadConfig()
            }
        }, Time(System.currentTimeMillis() + 10000), 1000L * 60 * 5)
    }

    val configFolder: String
        get() = DungeonHubService.getInstance().mainFolder + File.separator + "config"

    override val configFile: File
        get() = File(configFolder + File.separator + "application_config.properties")

    override suspend fun preStart() {
        reloadConfig()
    }

    override suspend fun onStart() {
        resetTimer()
    }
}