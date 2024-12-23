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
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import me.taubsie.dungeonhub.application.connection.getMutualServers
import net.dungeonhub.connection.*
import net.dungeonhub.enums.RoleRequirementType
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.hypixel.entities.CurrentMember
import net.dungeonhub.hypixel.entities.KnownSkill
import net.dungeonhub.model.discord_role.DiscordRoleModel
import net.dungeonhub.model.discord_role_group.DiscordRoleGroupModel
import net.dungeonhub.model.role_requirement.RoleRequirementModel
import java.util.stream.Collectors
import kotlin.time.Duration

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

        discordRoles.addAll(rolesToAdd.filter { it.await() != null }.map { role -> role.await()?.id!! })
        discordRoles.removeAll(rolesToRemove.filter { it.await() != null }.map { role -> role.await()?.id!! }.toSet())

        val roleRequirements = calculateRoleRequirements(member)

        discordRoles.addAll(roleRequirements.filter { it.value }.map { Snowflake(it.key.discordRole.id) })
        discordRoles.removeAll(roleRequirements.filter { !it.value }.map { Snowflake(it.key.discordRole.id) }.toSet())

        var lastRoles = 0
        while (lastRoles != discordRoles.size) {
            lastRoles = discordRoles.size
            discordRoles = applyRoleGroups(member.guild, discordRoles)
        }

        return discordRoles.map { id -> member.guild.getRole(id) }
    }

    fun calculateRoleRequirements(member: Member): Map<RoleRequirementModel, Boolean> {
        val roleRequirements =
            RoleRequirementConnection[member.guild.id.value.toLong()].allRoleRequirements ?: emptyList()

        return roleRequirements.associateWith {
            checkRoleRequirement(it, member)
        }
    }

    fun checkRoleRequirement(roleRequirement: RoleRequirementModel, member: Member): Boolean {
        if(!roleRequirement.checkExtraData(roleRequirement.extraData)) return false

        val discordServer = DiscordServerConnection.findServerById(member.guild.id.value.toLong())
            ?: return false

        val discordUser = DiscordUserConnection.getLinkedById(member.id.value.toLong())
            ?: return false

        val uuid = discordUser.minecraftId
            ?: return false

        val profiles = HypixelApiConnection().getSkyblockProfiles(uuid) ?: return false

        val profileMembers = profiles.profiles.mapNotNull { it.members.firstOrNull { member -> member.uuid == uuid } }
            .filterIsInstance<CurrentMember>()

        return when (roleRequirement.requirementType) {
            RoleRequirementType.SkyblockLevel -> {
                return roleRequirement.compare(
                    profileMembers.maxOf {
                        it.leveling.level
                    }
                )
            }

            RoleRequirementType.CatacombsLevel -> {
                return roleRequirement.compare(
                    profileMembers.maxOf { it.dungeons?.catacombsLevel ?: 0 }
                )
            }

            RoleRequirementType.FarmingLevel -> {
                return roleRequirement.compare(
                    profileMembers.maxOf { it.playerData.nonCosmeticExperience?.get(KnownSkill.Farming) ?: 0.0 }.toInt()
                )
            }

            RoleRequirementType.MiningLevel -> {
                return roleRequirement.compare(
                    profileMembers.maxOf { it.playerData.nonCosmeticExperience?.get(KnownSkill.Mining) ?: 0.0 }.toInt()
                )
            }

            RoleRequirementType.CombatLevel -> {
                return roleRequirement.compare(
                    profileMembers.maxOf { it.playerData.nonCosmeticExperience?.get(KnownSkill.Combat) ?: 0.0 }.toInt()
                )
            }

            RoleRequirementType.FishingLevel -> {
                return roleRequirement.compare(
                    profileMembers.maxOf { it.playerData.nonCosmeticExperience?.get(KnownSkill.Fishing) ?: 0.0 }.toInt()
                )
            }

            RoleRequirementType.SkillAverage -> {
                return roleRequirement.compare(
                    profileMembers.maxOf { it.playerData.skillAverage }.toInt()
                )
            }

            RoleRequirementType.HighestSkill -> {
                return roleRequirement.compare(
                    profileMembers.maxOf {
                        it.playerData.nonCosmeticExperience?.maxOf { skill ->
                            skill.key.calculateLevel(
                                skill.value
                            )
                        } ?: 0
                    }
                )
            }

            //TODO check for carry type
            RoleRequirementType.CurrentScore -> {
                return roleRequirement.compare(
                    DiscordServerConnection.getScores(discordServer, discordUser.id)?.filter {
                        it.scoreType == ScoreType.Default
                    }?.sumOf { it.scoreAmount ?: 0 }?.toInt() ?: 0
                )
            }

            //TODO check for carry type
            RoleRequirementType.AlltimeScore -> {
                return roleRequirement.compare(
                    DiscordServerConnection.getScores(discordServer, discordUser.id)?.filter {
                        it.scoreType == ScoreType.Alltime
                    }?.sumOf { it.scoreAmount ?: 0 }?.toInt() ?: 0
                )
            }

            RoleRequirementType.TotalCarries -> {
                //TODO implement
                false
            }

            RoleRequirementType.TotalCarriesInTimeFrame -> {
                //TODO implement
                false
            }

            //TODO maybe also add money earned?
            RoleRequirementType.MoneySpent -> {
                return roleRequirement.compare(
                    DiscordServerConnection.getTotalAmountOfMoneySpent(
                        discordServer.id,
                        userId = discordUser.id
                    )?.toInt() ?: 0
                )
            }

            RoleRequirementType.MoneySpentInTimeFrame -> {
                val duration = roleRequirement.extraData?.let(Duration::parse)
                    ?: return false

                return roleRequirement.compare(
                    DiscordServerConnection.getTotalAmountOfMoneySpent(
                        discordServer.id,
                        userId = discordUser.id,
                        since = Clock.System.now().minus(duration).toJavaInstant()
                    )?.toInt() ?: 0
                )
            }

            RoleRequirementType.HypixelRank -> {
                //TODO complete once guild mapping is complete in the hypixel wrapper
                return false
            }

            RoleRequirementType.GuildMembership -> {
                //TODO complete once guild mapping is complete in the hypixel wrapper
                return false
            }

            RoleRequirementType.GuildRank -> {
                //TODO complete once guild mapping is complete in the hypixel wrapper
                return false
            }
        }
    }

    fun applyRoleGroups(server: GuildBehavior, roles: Set<Snowflake>): MutableSet<Snowflake> {
        var mutableRoles = roles.toMutableSet()
        val roleGroups = DiscordRoleGroupConnection[server.id.value.toLong()].all ?: emptyList()

        var lastRoles = 0
        while (lastRoles != mutableRoles.size) {
            lastRoles = mutableRoles.size

            mutableRoles = applyRoleGroups(mutableRoles, roleGroups)
        }

        return mutableRoles
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
        if (removeRoleGroups.isEmpty()) {
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