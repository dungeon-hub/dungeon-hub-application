package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.entity.Member
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasRole
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.application.misc.PurgeData
import net.dungeonhub.model.discord_role.DiscordRoleModel
import net.dungeonhub.model.purge_type.PurgeTypeModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

@OnStart
object PurgingService : StartupListener {
    private val logger: Logger = LoggerFactory.getLogger(PurgingService::class.java)
    private val purgeDataList: MutableList<PurgeData> = ArrayList()
    private val purgeEnabled: MutableList<Long> = ArrayList()
    private lateinit var scheduler: Scheduler

    override suspend fun postStart() {
        if(::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        scheduler.schedule(6.seconds, startNow = true, name = "Purging-Schedule", repeat = true) {
            purgeWave()
        }
    }

    fun clearServer(serverId: Long) {
        if (!purgeEnabled.contains(serverId)) {
            purgeDataList.removeIf { purgeData: PurgeData -> purgeData.purgeType.carryType.server.id == serverId }
        }
    }

    fun enablePurge(serverId: Long) {
        if (!purgeEnabled.contains(serverId)) {
            purgeEnabled.add(serverId)
        }
    }

    fun getProgress(serverId: Long): Long {
        return purgeDataList.stream()
            .filter { purgeData: PurgeData -> purgeData.purgeType.carryType.server.id == serverId }
            .count()
    }

    fun getUserProgress(serverId: Long): Long {
        return purgeDataList.stream()
            .filter { purgeData: PurgeData -> purgeData.purgeType.carryType.server.id == serverId }
            .map(PurgeData::userId)
            .distinct()
            .count()
    }

    fun isPurgeActive(serverId: Long): Boolean {
        return purgeEnabled.contains(serverId)
    }

    private suspend fun purgeWave() {
        val currentWave = purgeDataList.stream()
            .filter { purgeData: PurgeData -> purgeEnabled.contains(purgeData.purgeType.carryType.server.id) }
            .limit(3)
            .toList()

        purgeEnabled.removeIf { aLong: Long ->
            purgeDataList.stream()
                .noneMatch { purgeData: PurgeData -> purgeData.purgeType.carryType.server.id == aLong }
        }

        try {
            currentWave.forEach { purgeData: PurgeData ->
                val server = DiscordConnection.bot.kordRef.getGuildOrNull(Snowflake(purgeData.purgeType.carryType.server.id))

                if (server == null) {
                    logger.error("Server isn't a valid server for purging anymore!")
                    return@forEach
                }

                val member = DiscordConnection.bot.kordRef.getUser(Snowflake(purgeData.userId))?.asMember(server.id)

                if (member == null) {
                    logger.error("Member wasn't found anymore! I guess they escaped the purge.")
                    return@forEach
                }

                val rolesRemoved = removeRoles(
                    purgeData.rolesToRemove,
                    member,
                    purgeData.purgeType,
                    purgeData.purgeThreshold
                )

                scheduler.launch {
                    delay(5000)

                    val reloadedMember =
                        member.withStrategy(EntitySupplyStrategy.cacheWithCachingRestFallback).fetchMember()

                    RolesService.updateRoles(reloadedMember)
                }

                if (rolesRemoved.isNotEmpty()) {
                    try {
                        member.dm {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Negative.color
                            embed.title = "Inactivity Purge"
                            embed.description =
                                "Your ${purgeData.purgeType.displayName}-carry roles on `${server.name}` were removed since you only reached ${purgeData.score}/${purgeData.purgeThreshold} score."
                            embed.field("Roles removed", false) { rolesRemoved.joinToString(System.lineSeparator()) }
                            embeds = mutableListOf(embed)
                        }
                    } catch (_: RequestException) {
                        // ignore since member doesn't need to know
                    }
                }
            }
        } catch (exception: Exception) {
            logger.error("An error occurred while performing a purge wave!", exception)
        }

        purgeDataList.removeAll(currentWave)
    }

    private suspend fun removeRoles(
        rolesToRemove: List<DiscordRoleModel>, member: Member, purgeType: PurgeTypeModel,
        purgeThreshold: Long
    ): List<String> {
        val rolesRemoved: MutableList<String> = ArrayList()

        for (discordRole in rolesToRemove) {
            val role = member.guild.getRoleOrNull(Snowflake(discordRole.id))

            if (role == null) {
                logger.error("Role ${discordRole.id} not found on server ${member.guild.id}.")
                continue
            }

            if (member.hasRole(role)) {
                member.removeRole(
                    role.id,
                    "Purge of type \"${purgeType.displayName}\" with threshold $purgeThreshold."
                )

                rolesRemoved.add(role.name)
            }
        }

        return rolesRemoved
    }

    fun addPurgeData(purgeData: PurgeData) {
        purgeDataList.add(purgeData)
    }
}