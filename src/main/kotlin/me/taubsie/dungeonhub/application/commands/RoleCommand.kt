package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.Member
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.checks.hasPermission
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.exceptions.NoNameSchemaWarning
import me.taubsie.dungeonhub.application.exceptions.NoOptionFoundException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.NicknameService
import me.taubsie.dungeonhub.application.service.RolesService
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleCreationModel
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleUpdateModel
import kotlin.concurrent.thread

@LoadExtension
class RoleCommand : Extension() {
    override val name = "role-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "role"
            description = "Manage a user's roles."
            defaultMemberPermissions = Permissions(Permission.ManageRoles)
            allowInDms = false

            publicSubCommand(::RoleArguments) {
                name = "add"
                description = "Add a role to a user"

                action {
                    respond {
                        embeds = mutableListOf(addRemove(true, user.asMember(guild!!.id), arguments))
                    }
                }
            }

            publicSubCommand(::RoleArguments) {
                name = "remove"
                description = "Remove a role from a user"

                action {
                    respond {
                        embeds = mutableListOf(addRemove(false, user.asMember(guild!!.id), arguments))
                    }
                }
            }

            publicSubCommand(::RoleGroupRemoveArguments) {
                name = "remove-group"
                description = "Remove a role group from a user"

                action {
                    respond {
                        embeds = mutableListOf(removeRoleGroup(user.asMember(guild!!.id), arguments))
                    }
                }
            }

            group("config") {
                description = "Change the settings of a role."

                publicSubCommand(::RoleConfigSetArguments) {
                    name = "set"
                    description = "Set a role config value"

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        respond {
                            val currentRole =
                                DiscordRoleConnection.getInstance(
                                    guild!!.id.value.toLong()
                                )
                                    .getById(arguments.role.id.value.toLong())
                                    .orElse(null)

                            if (arguments.nameSchema == null && arguments.verifiedRole == null) {
                                if (currentRole == null) {
                                    throw NoOptionFoundException()
                                } else {
                                    embeds = mutableListOf(ApplicationService.loadEmbedFromDiscordRole(currentRole))
                                }
                                return@respond
                            }

                            val modifiedRole = if (currentRole != null) {
                                DiscordRoleConnection.getInstance(
                                    guild!!.id.value.toLong()
                                )
                                    .updateRole(
                                        arguments.role.id.value.toLong(),
                                        DiscordRoleUpdateModel(
                                            arguments.nameSchema,
                                            arguments.verifiedRole
                                        )
                                    )
                                    .orElse(null)
                            } else {
                                DiscordRoleConnection.getInstance(
                                    guild!!.id.value.toLong()
                                )
                                    .addNewRole(
                                        DiscordRoleCreationModel(
                                            arguments.role.id.value.toLong(),
                                            arguments.nameSchema,
                                            arguments.verifiedRole ?: false
                                        )
                                    )
                                    .orElse(null)
                            }

                            if (modifiedRole == null) {
                                val embed = ApplicationService.embed
                                embed.color = EmbedColor.Negative.color
                                embed.description = "Couldn't modify the given role."
                                embeds = mutableListOf(embed)

                                return@respond
                            }

                            val embed = ApplicationService.loadEmbedFromDiscordRole(modifiedRole)
                            embed.color = EmbedColor.Positive.color
                            embed.title = "Modified role"
                            embeds = mutableListOf(embed)
                        }
                    }
                }

                //TODO finish implementation
                /*publicSubCommand(::RoleConfigResetArguments) {
                    name = "reset"
                    description = "Reset a role config value"

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        respond {
                            //TODO finish implementation
                            throw CommandExecutionException("Command isn't implemented yet.")
                        }
                    }
                }*/
            }
        }
    }

    suspend fun addRemove(add: Boolean, issuer: Member, arguments: RoleArguments): EmbedBuilder {
        if ((arguments.role.guild.asGuild().ownerId != issuer.id)
            && (arguments.role.getPosition() >= (issuer.roles.map { it.getPosition() }.toList().maxOrNull() ?: 0))
        ) {
            throw CommandExecutionException("You aren't allowed to manage roles that are higher than those that you have.")
        }

        val target = arguments.user.asMember(issuer.guildId)

        val hasRole = target.roleIds.contains(arguments.role.id)

        val embed = ApplicationService.embed
        embed.color = EmbedColor.Positive.color

        if (add) {
            target.addRole(arguments.role.id)

            if (!hasRole) {
                embed.description = "Successfully added ${arguments.role.mention} to ${arguments.user.mention}."
            } else {
                embed.description =
                    "The user ${arguments.user.mention} already had the role ${arguments.role.mention}, but I tried to add it anyway."
                embed.color = EmbedColor.Negative.color
            }
        } else {
            target.removeRole(arguments.role.id)

            if (hasRole) {
                embed.description = "Successfully removed ${arguments.role.mention} from ${arguments.user.mention}."
            } else {
                embed.description =
                    "The user ${arguments.user.mention} didn't have the role ${arguments.role.mention}, but I tried to remove it anyway."
                embed.color = EmbedColor.Negative.color
            }
        }

        thread(start = true) {
            runBlocking {
                val member = arguments.user.fetchMember(issuer.guildId)

                val updatedRoles = RolesService.updateRoles(member)

                try {
                    NicknameService.updateNickname(member, updatedRoles)
                } catch (ignored: NoNameSchemaWarning) {
                    //ignore this, in that case you just don't apply a nickname
                }
            }
        }

        return embed
    }

    suspend fun removeRoleGroup(issuer: Member, arguments: RoleGroupRemoveArguments): EmbedBuilder {
        val guild = arguments.role.guild.asGuild()

        val target = arguments.target.asMember(issuer.guildId)

        val highestIssuerRole = issuer.roles.map { it.getPosition() }.toList().maxOrNull() ?: 0
        val highestTargetRole = target.roles.map { it.getPosition() }.toList().maxOrNull() ?: 0

        /*
        fail if issuer:
        - is not owner
        - is not using command on himself / is not target
        - is not above role (or highest role of target)
        */
        if ((guild.ownerId != issuer.id)
            && issuer.id != target.id
            && ((arguments.role.getPosition() >= highestIssuerRole)
                    || (highestTargetRole >= highestIssuerRole))
        ) {
            throw CommandExecutionWarning("You aren't allowed to manage roles that are higher than those that you have.")
        }

        RolesService.removeRoleGroup(target, arguments.role.id.value.toLong())

        val embed = ApplicationService.embed
        embed.color = EmbedColor.Positive.color
        embed.description =
            "Successfully removed the user ${arguments.target.mention} from the role-group ${arguments.role.mention}."

        thread(start = true) {
            runBlocking {
                val member = arguments.target.fetchMember(issuer.guildId)

                val updatedRoles = RolesService.updateRoles(member)

                try {
                    NicknameService.updateNickname(member, updatedRoles)
                } catch (ignored: NoNameSchemaWarning) {
                    //ignore this, in that case you just don't apply a nickname
                }
            }
        }

        return embed
    }

    inner class RoleArguments : Arguments() {
        val user by user {
            name = "user"
            description = "Select which user to modify the role of."
        }

        val role by role {
            name = "role"
            description = "Select which role you mean."
        }
    }

    inner class RoleGroupRemoveArguments : Arguments() {
        val target by user {
            name = "user"
            description = "Select which user to remove the role group of."
        }

        val role by role {
            name = "role-group"
            description = "The role group to remove from the given user."
        }
    }

    inner class RoleConfigSetArguments : Arguments() {
        val role by role {
            name = "role"
            description = "Select which role you want to configure."
        }

        val nameSchema by optionalString {
            name = "name-schema"
            description = "Set the name schema for this username"
        }

        val verifiedRole by optionalBoolean {
            name = "verified-role"
            description = "Set if the role should automatically be granted to everyone who is linked."
        }
    }

    inner class RoleConfigResetArguments : Arguments() {
        val role by role {
            name = "role"
            description = "Select which role you want to configure."
        }
    }
}