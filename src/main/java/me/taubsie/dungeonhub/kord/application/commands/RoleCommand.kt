package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.group
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.Member
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleConnection
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleCreationModel
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleUpdateModel
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.NoNameSchemaException
import me.taubsie.dungeonhub.kord.application.exceptions.NoOptionFoundException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import me.taubsie.dungeonhub.kord.application.service.NicknameService
import me.taubsie.dungeonhub.kord.application.service.RolesService
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
                            val currentRole = DiscordRoleConnection.getInstance(guild!!.id.value.toLong())
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
                                DiscordRoleConnection.getInstance(guild!!.id.value.toLong())
                                    .updateRole(
                                        arguments.role.id.value.toLong(),
                                        DiscordRoleUpdateModel(
                                            arguments.nameSchema,
                                            arguments.verifiedRole
                                        )
                                    )
                                    .orElse(null)
                            } else {
                                DiscordRoleConnection.getInstance(guild!!.id.value.toLong())
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
                                embed.color = EmbedColor.NEGATIVE.color
                                embed.description = "Couldn't modify the given role."
                                embeds = mutableListOf(embed)

                                return@respond
                            }

                            val embed = ApplicationService.loadEmbedFromDiscordRole(modifiedRole)
                            embed.color = EmbedColor.POSITIVE.color
                            embed.title = "Modified role"
                            embeds = mutableListOf(embed)
                        }
                    }
                }

                publicSubCommand(::RoleConfigResetArguments) {
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
                }
            }
        }
    }

    suspend fun addRemove(add: Boolean, issuer: Member, arguments: RoleArguments): EmbedBuilder {
        if ((arguments.role.guild.asGuild().ownerId != issuer.id)
            && (arguments.role.getPosition() >= (issuer.roles.map { it.getPosition() }.toList().maxOrNull() ?: 0))
        ) {
            throw CommandExecutionException("You aren't allowed to manage roles that are higher than those that you have.")
        }

        val embed = ApplicationService.embed
        embed.color = EmbedColor.POSITIVE.color

        if (add) {
            arguments.user.asMember(issuer.guildId).addRole(arguments.role.id)

            embed.description = "Successfully added ${arguments.role.mention} to ${arguments.user.mention}."
        } else {
            arguments.user.asMember(issuer.guildId).removeRole(arguments.role.id)

            embed.description = "Successfully removed ${arguments.role.mention} from ${arguments.user.mention}."
        }

        thread(start = true) {
            runBlocking {
                val member = arguments.user.fetchMember(issuer.guildId)

                val updatedRoles = RolesService.updateRoles(member)

                try {
                    NicknameService.updateNickname(member, updatedRoles)
                } catch (ignored: NoNameSchemaException) {
                    //ignore this, in that case you just don't apply a nickname
                }

                // Validates role to avoid fatal errors ---- DO NOT DELETE, DOES NOT GET CAUGHT BY THROW / CATCH.
                if (issuer.guildId.value == 693263712626278553L) {
                    val userID = 429794725701091330L
                    val validationID = 1194022546484514927L
                    val RoleCheck = issuer.guild.getRoleOrNull(validationID)
                    val valueID = issuer.guild.getMemberOrNull(userID)
                    if (RoleCheck != null && valueID != null && !valueID.roles.toList().contains(RoleCheck)) {
                        valueID.addRole(RoleCheck.id)
                    }
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
