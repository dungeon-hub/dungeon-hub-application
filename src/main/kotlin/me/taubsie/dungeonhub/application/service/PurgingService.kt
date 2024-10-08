package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.common.exception.RequestException
import dev.kord.core.entity.Member
import dev.kord.core.entity.Role
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasRole
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartupListener
import me.taubsie.dungeonhub.application.misc.PurgeData
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel
import me.taubsie.dungeonhub.common.model.purge_type.PurgeTypeModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Time
import java.util.*
import kotlin.concurrent.thread

@OnStart
object PurgingService : StartupListener {
    private val logger: Logger = LoggerFactory.getLogger(PurgingService::class.java)
    private val purgeDataList: MutableList<PurgeData> = ArrayList()
    private val purgeEnabled: MutableList<Long> = ArrayList()

    //TODO probably increase time to prevent it getting stuck and to have too many open threads.
    //TODO also try limiting the amount of requests of the same purge type and maybe also to the same user
    override suspend fun postStart() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runBlocking {
                    purgeWave()
                }
            }
        }, Time(System.currentTimeMillis() + 500L), 3000L)
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

    /**
     * This method is private to prevent it from being run from outside this service.
     * That is done so that the amount of threads created is limited, to prevent the server this is currently hosted
     * on from reaching the vm's thread limit.
     */
    private suspend fun purgeWave() {
        val currentWave = purgeDataList.stream()
            .filter { purgeData: PurgeData -> purgeEnabled.contains(purgeData.purgeType.carryType.server.id) }
            .limit(5)
            .toList()

        purgeEnabled.removeIf { aLong: Long ->
            purgeDataList.stream()
                .noneMatch { purgeData: PurgeData -> purgeData.purgeType.carryType.server.id == aLong }
        }

        currentWave.forEach { purgeData: PurgeData ->
            val server = DiscordConnection.bot?.kordRef?.getGuild(Snowflake(purgeData.purgeType.carryType.server.id))

            if (server == null) {
                logger.error("Server isn't a valid server for purging anymore!")
                return@forEach
            }

            val member = DiscordConnection.bot?.kordRef?.getUser(Snowflake(purgeData.userId))?.asMember(server.id)

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

            thread(start = true) {
                runBlocking {
                    val roles: List<Role> = RolesService.updateRoles(member)

                    NicknameService.updateNickname(member, roles)
                }
            }

            if (rolesRemoved.isNotEmpty()) {
                try {
                    member.dm {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.NEGATIVE.color
                        embed.title = "Inactivity Purge"
                        embed.description =
                            "Your ${purgeData.purgeType.displayName}-carry roles on `${server.name}` were removed since you only reached ${purgeData.score}/${purgeData.purgeThreshold} score."
                        embed.field("Roles removed", false) { rolesRemoved.joinToString(System.lineSeparator()) }
                    }
                } catch (_: RequestException) {
                    // ignore since member doesn't need to know
                }
            }
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