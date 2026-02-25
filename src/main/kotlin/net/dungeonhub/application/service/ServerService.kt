package net.dungeonhub.application.service

import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.DhScheduler
import net.dungeonhub.application.misc.ServerData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

@OnStart
object ServerService : StartupListener {
    private val logger: Logger = LoggerFactory.getLogger(ServerService::class.java)
    private val serverData: MutableSet<ServerData> = HashSet()
        @Synchronized
        get

    private lateinit var scheduler: Scheduler

    init {
        try {
            Files.createDirectory(Path.of(ServerData.configFolder))
        } catch (_: FileAlreadyExistsException) {
            //Ignored since I just want to be sure that the folder always exists.
        } catch (ioException: IOException) {
            logger.error(null, ioException)
        }
    }

    private suspend fun loadServers() {
        serverData.clear()

        DiscordConnection.bot.kordRef.guilds.collect {
            loadServerData(it.id.value.toLong())
        }
    }

    private suspend fun resetScheduler() {
        scheduler.schedule(15.minutes, startNow = true, name = "Server-Config-Schedule", repeat = true) {
            logger.debug("Server configs reloaded!")
            loadServers()
        }
    }

    fun loadServerData(id: Long) {
        serverData.add(ServerData(id))
    }

    fun unloadServerData(id: Long) {
        serverData.removeIf { it.id == id }
    }

    fun getServerData(id: Long): ServerData? {
        return serverData.firstOrNull { it.id == id }
    }

    fun getActualServerProperty(id: Long, serverProperty: ServerProperty): String? {
        return getServerData(id)?.getConfig(serverProperty)?.takeIf { it.isNotBlank() }
    }

    fun canUse(id: Long, serverProperty: ServerProperty?): Boolean {
        //TODO finish implementation (if needed here)
        if (serverProperty == null) {
            return false
        }

        return getServerData(id)?.isEnabled(serverProperty) ?: false
    }

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = DhScheduler()

        loadServers()

        resetScheduler()
    }
}