package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kordex.core.utils.scheduling.Scheduler
import io.ktor.util.collections.*
import kotlinx.coroutines.cancel
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.exceptions.NotLinkedException
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@OnStart
object MassSyncService : StartupListener {
    private const val WAVE_SIZE = 1
    private val waveDuration = 15.seconds

    private lateinit var scheduler: Scheduler
    private val logger = LoggerFactory.getLogger(MassSyncService::class.java)

    // Map structure to segregate users by guild (key: guild ID, value: set of user IDs).
    // This prevents duplicate sync entries and ensures users are processed per guild.
    private val usersToSync = ConcurrentHashMap<Snowflake, MutableSet<Snowflake>>()

    private suspend fun syncWave() {
        val guildId = usersToSync.filter { it.value.isNotEmpty() }.entries.randomOrNull()?.key ?: return

        val currentWave = getUsersToSync(guildId).take(WAVE_SIZE).toSet()

        getUsersToSync(guildId).removeAll(currentWave)

        try {
            currentWave.mapNotNull { DiscordConnection.bot.kordRef.getGuild(guildId).getMemberOrNull(it) }
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

    fun syncUser(guildId: Snowflake, userId: Snowflake) {
        getUsersToSync(guildId).add(userId)
    }

    fun syncUsers(guildId: Snowflake, userIds: Collection<Snowflake>) {
        getUsersToSync(guildId).addAll(userIds)
    }

    fun getUsersToSync(guildId: Snowflake) : MutableSet<Snowflake> {
        return usersToSync.computeIfAbsent(guildId) { ConcurrentSet() }
    }

    fun clearUsers(guildId: Snowflake) : Int {
        val count = getUsersToSync(guildId).size

        getUsersToSync(guildId).clear()

        return count
    }

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        scheduler.schedule(waveDuration, startNow = true, name = "Mass-Sync-Schedule", repeat = true) {
            syncWave()
        }
    }
}