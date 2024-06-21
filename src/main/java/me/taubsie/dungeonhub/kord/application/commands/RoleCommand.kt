package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
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
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.NoNameSchemaException
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
}