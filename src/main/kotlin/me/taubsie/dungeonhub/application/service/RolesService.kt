package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.getMutualServers
import net.dungeonhub.connection.DiscordRoleConnection
import net.dungeonhub.connection.DiscordRoleGroupConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.model.discord_role.DiscordRoleModel
import net.dungeonhub.model.discord_role_group.DiscordRoleGroupModel
import java.util.stream.Collectors

object RolesService {
    fun updateRoles(user: User): Map<Long, List<Role>> {
        return runBlocking {
            async {
                user.getMutualServers().map { member ->
                    member.guildId.value.toLong() to updateRoles(member)
                }.toList().toMap()
            }.await()
        }
    }

    suspend fun updateRoles(member: Member): List<Role> {
        val newRoles = calculateRoles(member)

        member.edit {
            roles = newRoles.map { role -> role.id }.toMutableSet()
        }

        return newRoles
    }

    suspend fun calculateRoles(member: Member): List<Role> {
        val serverRoles = (DiscordRoleConnection[member.guildId.value.toLong()].allRoles ?: emptyList())
                .stream()
                .collect(
                    Collectors.toMap(
                        { obj: DiscordRoleModel -> obj.id },
                        { discordRoleModel: DiscordRoleModel -> discordRoleModel })
                )

        var discordRoles: MutableSet<Snowflake> = member.roleIds.toMutableSet()

        val isVerified = DiscordUserConnection.getLinkedById(member.id.value.toLong()) != null

        val rolesToAdd = serverRoles.values.stream()
            .filter { roleModel -> roleModel.shouldBeAdded(isVerified) }
            .map { obj: DiscordRoleModel -> obj.id }
            .map { id: Long? ->
                runBlocking {
                    async {
                        member.guild.getRoleOrNull(
                            Snowflake(id!!)
                        )
                    }
                }
            }
            .toList()

        val rolesToRemove = serverRoles.values.stream()
            .filter { roleModel -> roleModel.shouldBeRemoved(isVerified) }
            .map { obj: DiscordRoleModel -> obj.id }
            .map { id: Long? ->
                runBlocking {
                    async {
                        member.guild.getRoleOrNull(
                            Snowflake(id!!)
                        )
                    }
                }
            }
            .toList()

        discordRoles.addAll(rolesToAdd.map { role -> role.await()?.id!! })
        discordRoles.removeAll(rolesToRemove.map { role -> role.await()?.id!! }.toSet())

        var lastRoles = 0
        while (lastRoles != discordRoles.size) {
            lastRoles = discordRoles.size
            discordRoles = applyRoleGroups(member.guild, discordRoles)
        }

        return discordRoles.map { id -> member.guild.getRole(id) }
    }

    fun applyRoleGroups(server: GuildBehavior, roles: MutableSet<Snowflake>): MutableSet<Snowflake> {
        @Suppress("NAME_SHADOWING") var roles = roles
        val roleGroups = DiscordRoleGroupConnection[server.id.value.toLong()].all ?: emptyList()

        var lastRoles = 0
        while (lastRoles != roles.size) {
            lastRoles = roles.size

            roles = applyRoleGroups(roles, roleGroups)
        }

        return roles
    }

    fun applyRoleGroups(
        roles: MutableSet<Snowflake>,
        roleGroups: List<DiscordRoleGroupModel>
    ): MutableSet<Snowflake> {
        roleGroups.stream()
            .filter { discordRoleGroupModel: DiscordRoleGroupModel ->
                roles.stream().anyMatch { role ->
                    role.value.toLong() == discordRoleGroupModel.discordRole.id
                }
            }
            .map { obj: DiscordRoleGroupModel -> obj.roleGroup }
            .map { obj: DiscordRoleModel -> obj.id }
            .map { id: Long? ->
                Snowflake(id!!)
            }
            .forEach { e ->
                roles.add(
                    e
                )
            }

        val roleModels = java.util.List.copyOf(roleGroups).stream()
            .collect(
                Collectors.toMap(
                    { discordRoleGroupModel: DiscordRoleGroupModel ->
                        discordRoleGroupModel.roleGroup.id
                    },
                    { discordRoleGroupModel: DiscordRoleGroupModel ->
                        mutableListOf(
                            discordRoleGroupModel.discordRole
                        )
                    },
                    { o: MutableList<DiscordRoleModel>, o2: List<DiscordRoleModel>? ->
                        o.addAll(
                            o2!!
                        )
                        o
                    }
                ))

        var rolesBefore = 0
        while (rolesBefore != roles.size) {
            rolesBefore = roles.size

            for ((key, value) in roleModels) {
                if (roles.stream().anyMatch { role -> role.value.toLong() == key }
                    && value.stream()
                        .allMatch { discordRoleModel: DiscordRoleModel ->
                            roles.stream()
                                .noneMatch { role -> role.value.toLong() == discordRoleModel.id }
                        }
                ) {
                    roles.removeIf { role -> role.value.toLong() == key }
                }
            }
        }

        return roles
    }

    suspend fun removeRoleGroup(member: Member, roleGroupId: Long) {
        var userRoles = member.roleIds.toMutableSet()

        val roleGroups = DiscordRoleGroupConnection[member.guild.id.value.toLong()].all ?: listOf()

        var lastRoles = 0
        while (lastRoles != userRoles.size) {
            lastRoles = userRoles.size

            userRoles = removeRoleGroups(userRoles, roleGroups, setOf(roleGroupId))
        }

        member.edit {
            roles = userRoles
        }
    }

    private fun removeRoleGroups(
        userRoles: MutableSet<Snowflake>,
        roleGroups: List<DiscordRoleGroupModel>,
        removeRoleGroups: Set<Long>
    ): MutableSet<Snowflake> {
        if(removeRoleGroups.isEmpty()) {
            return userRoles
        }

        val newRoleGroups = roleGroups.stream().filter {
            userRoles.contains(Snowflake(it.discordRole.id)) || userRoles.contains(Snowflake(it.roleGroup.id))
        }.toList()

        val toRemove = roleGroups.stream()
            .filter { roleGroup ->
                removeRoleGroups.any { roleGroup.roleGroup.id == it }
            }
            .map { Snowflake(it.discordRole.id) }
            .collect(Collectors.toSet())

        userRoles.removeAll(toRemove)

        return removeRoleGroups(userRoles, newRoleGroups, toRemove.map { it.value.toLong() }.toSet())
    }
}