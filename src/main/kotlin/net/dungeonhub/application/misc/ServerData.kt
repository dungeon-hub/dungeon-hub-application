package net.dungeonhub.application.misc

import lombok.Getter
import net.dungeonhub.application.config.ConfigFile
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ServerService
import java.io.File
import java.util.*
import java.util.stream.Collectors

/**
 * This class holds all config properties for a server.
 * While this is an important class, it is only really used by
 * [ServerService] and [ServerProperty], as they offer a cleaner
 * API.
 * @see ServerService
 *
 * @see ServerProperty
 */
@Getter
class ServerData(val id: Long) : ConfigFile<ServerProperty>() {
    init {
        reloadConfig()
    }

    override val possibleProperties: Set<ServerProperty>
        get() = Arrays.stream(ServerProperty.entries.toTypedArray())
            .filter { obj: ServerProperty -> obj.enabled }
            .collect(Collectors.toSet())

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