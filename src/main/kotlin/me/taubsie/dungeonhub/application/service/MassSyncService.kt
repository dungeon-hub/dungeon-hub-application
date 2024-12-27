package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.User
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartupListener
import okhttp3.internal.toImmutableList
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@OnStart
object MassSyncService : StartupListener {
    private var timerTask: ScheduledFuture<*>? = null
    private val logger = LoggerFactory.getLogger(MassSyncService::class.java)

    val usersToSync = mutableListOf<Snowflake>()

    suspend fun syncWave() {
        val currentWave = usersToSync.stream()
            .limit(10)
            .toList().toImmutableList()

        usersToSync.removeAll(currentWave)

        currentWave.map { DiscordConnection.bot!!.kordRef.getUser(it) }.filterNotNull().forEach { user ->
            syncUser(user)
        }
    }

    suspend fun syncUser(user: User) {
        try {
            val roles = RolesService.updateRoles(user, cacheExpiration = 60 * 24 * 365)

            NicknameService.updateNickname(user, roles)
        }
        catch (_: NotLinkedException) {
            //ignore, just don't sync those users
        }
        catch (e: Exception) {
            logger.error("Error during mass sync for user ${user.id}", e)
        }
    }

    override suspend fun postStart() {
        if (timerTask != null) {
            timerTask!!.cancel(false)
        }

        timerTask = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            runBlocking {
                syncWave()
            }
        }, 15, 30, TimeUnit.SECONDS)
    }
}