package me.taubsie.dungeonhub.kord.application.misc

import lombok.Getter
import me.taubsie.dungeonhub.application.config.ConfigFile
import me.taubsie.dungeonhub.common.DungeonHubService
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty
import me.taubsie.dungeonhub.kord.application.service.ServerService
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

    override fun getPossibleProperties(): Set<ServerProperty> {
        return Arrays.stream(ServerProperty.entries.toTypedArray())
            .filter { obj: ServerProperty -> obj.enabled }
            .collect(Collectors.toSet())
    }

    fun isEnabled(serverProperty: ServerProperty): Boolean {
        return serverProperty.isEnabled(id)
    }

    val serverFolder: String
        get() = configFolder + File.separator + id

    override fun getConfigFile(): File {
        return File(serverFolder + File.separator + "config.properties")
    }

    companion object {
        val configFolder: String
            get() = DungeonHubService.getInstance().mainFolder + File.separator + "servers"
    }
}