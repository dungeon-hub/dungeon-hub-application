package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartupListener
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@OnStart
object MassSyncService : StartupListener {
    private var timerTask: ScheduledFuture<*>? = null
    private val logger = LoggerFactory.getLogger(MassSyncService::class.java)

    // Map structure to segregate users by guild (key: guild ID, value: set of user IDs).
    // This prevents duplicate sync entries and ensures users are processed per guild.
    private val usersToSync = mutableMapOf<Snowflake, MutableSet<Snowflake>>()

    suspend fun syncWave() {
        if(lastGuild == null) {
            return
        }

        val currentWave = usersToSync.stream()
            .limit(1)
            .toList().toImmutableList()

        usersToSync.removeAll(currentWave)

        try {
            currentWave.mapNotNull { DiscordConnection.bot!!.kordRef.getGuild(lastGuild!!).getMember(it) }
                .forEach { user ->
                    syncUser(user)
                }
        } catch (e: Exception) { logger.error("Uncaught error during mass sync for users: $currentWave", e) }
    }

    private suspend fun syncUser(member: Member) {
        try {
            val roles = RolesService.updateRoles(member, cacheExpiration = 60 * 24 * 365)

            NicknameService.updateNickname(member, roles, cacheExpiration = 60 * 24 * 365)
        }
        catch (_: NotLinkedException) {
            //ignore, just don't sync those users
        }
        catch (e: Exception) {
            logger.error("Error during mass sync for user ${member.id}", e)
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
        }, 15, 15, TimeUnit.SECONDS)
    }
}