package net.dungeonhub.application.misc

import kotlinx.coroutines.launch
import net.dungeonhub.application.config.ConfigFile
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ServerService
import java.io.File

/**
 * This class holds all config properties for a server.
 * While this is an important class, it is only really used by
 * [ServerService] and [ServerProperty], as they offer a cleaner
 * API.
 * @see ServerService
 *
 * @see ServerProperty
 */
class ServerData(val id: Long) : ConfigFile<ServerProperty>() {
    init {
        scheduler.launch {
            reloadConfig()
        }
    }

    override val possibleProperties: Set<ServerProperty>
        get() = ServerProperty.entries.filter { it.enabled }.toSet()

    override val configFile: File
        get() = File(serverFolder + File.separator + "config.properties")

    fun isEnabled(serverProperty: ServerProperty): Boolean {
        return serverProperty.isEnabled(id)
    }

    val serverFolder: String
        get() = configFolder + File.separator + id

    companion object {
        val configFolder: String
            get() = ApplicationService.dungeonHubDirectory + File.separator + "servers"
    }
}