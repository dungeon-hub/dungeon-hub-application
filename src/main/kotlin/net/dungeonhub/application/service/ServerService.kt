package net.dungeonhub.application.service

import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.ServerData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate
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
            Files.createDirectory(Path.of(serverFolder))
        } catch (_: FileAlreadyExistsException) {
            //Ignored since I just want to be sure that the folder always exists.
        } catch (ioException: IOException) {

            logger.error(null, ioException)
        }
    }

    private suspend fun loadServers() {
        serverData.clear()

        DiscordConnection.bot?.kordRef?.guilds
            ?.collect { server ->
                loadServerData(
                    server.id.value.toLong()
                )
            }
    }

    private suspend fun resetTimer() {
        scheduler.schedule(15.minutes, startNow = true, name = "Server-Config-Schedule", repeat = true) {
            logger.debug("Server configs reloaded!")
            loadServers()
        }
    }

    val serverFolder: String
        get() = ApplicationService.dungeonHubDirectory + File.separator + "servers"

    fun loadServerData(id: Long) {
        serverData.add(ServerData(id))
    }

    fun unloadServerData(id: Long) {
        serverData.removeIf { serverData1: ServerData -> serverData1.id == id }
    }

    fun getServerData(id: Long): Optional<ServerData> {
        return serverData.stream()
            .filter { data: ServerData -> data.id == id }
            .findAny()
    }

    val allServers: Set<ServerData>
        get() = serverData

    fun getServersWhere(function: Predicate<ServerData>?): List<ServerData> {
        return serverData.stream()
            .filter(function)
            .toList()
    }

    fun getActualServerProperty(id: Long, serverProperty: ServerProperty): Optional<String> {
        return getServerData(id)
            .map { data: ServerData ->
                data.getConfig(
                    serverProperty
                )
            }
            .flatMap { s -> Optional.ofNullable(s) }
            .filter { s -> s.isNotBlank() }
    }

    fun canUse(id: Long, serverProperty: ServerProperty?): Boolean {
        //TODO finish implementation (if needed here)
        if (serverProperty == null) {
            return false
        }

        return getServerData(id)
            .map { serverData1: ServerData ->
                serverData1.isEnabled(
                    serverProperty
                )
            }
            .orElse(false)
    }

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        loadServers()

        resetTimer()
    }
}