package me.taubsie.dungeonhub.kord.application.service

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.kord.application.misc.ServerData
import me.taubsie.dungeonhub.common.DungeonHubService
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty
import me.taubsie.dungeonhub.kord.application.loader.OnStart
import me.taubsie.dungeonhub.kord.application.loader.StartupListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Time
import java.util.*
import java.util.function.Predicate

@OnStart
object ServerService : StartupListener {
    private val logger: Logger = LoggerFactory.getLogger(ServerService::class.java)
    private val serverData: MutableSet<ServerData> = HashSet()
    private var timer: Timer? = null

    init {
        try {
            Files.createDirectory(Path.of(serverFolder))
        } catch (ignored: FileAlreadyExistsException) {
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

    private fun resetTimer() {
        if (timer != null) {
            timer!!.cancel()
        }

        timer = Timer()

        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                logger.debug("Server configs reloaded!")
                runBlocking {
                    launch {
                        loadServers()
                    }
                }
            }
        }, Time(System.currentTimeMillis() + 1000 * 60 * 15), 1000L * 60 * 15)
    }

    val serverFolder: String
        get() = DungeonHubService.getInstance().mainFolder + File.separator + "servers"

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
        loadServers()

        resetTimer()
    }
}