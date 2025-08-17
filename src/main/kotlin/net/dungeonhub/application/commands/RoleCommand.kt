package net.dungeonhub.application.commands

import dev.kord.common.asJavaLocale
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.Member
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.checks.hasPermission
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.converters.impl.optionalEnumChoice
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.dungeonhub.application.connection.copy
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.CommandExecutionWarning
import net.dungeonhub.application.exceptions.NoNameSchemaWarning
import net.dungeonhub.application.exceptions.NoOptionFoundException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.*
import net.dungeonhub.application.service.ApplicationService.toEmbed
import net.dungeonhub.connection.DiscordRoleConnection
import net.dungeonhub.connection.RoleRequirementConnection
import net.dungeonhub.enums.RoleAction
import net.dungeonhub.enums.RoleRequirementComparison
import net.dungeonhub.enums.RoleRequirementType
import net.dungeonhub.i18n.Translations.Command.Role
import net.dungeonhub.model.discord_role.DiscordRoleCreationModel
import net.dungeonhub.model.discord_role.DiscordRoleUpdateModel
import net.dungeonhub.model.role_requirement.RoleRequirementCreationModel

@LoadExtension
class RoleCommand : Extension() {
    override val name = "role-command"
    private lateinit var scheduler: Scheduler

    override suspend fun setup() {
        scheduler = Scheduler()

        publicSlashCommand {
            name = Role.name
            description = Role.description
            defaultMemberPermissions = Permissions(Permission.ManageRoles)
            allowInDms = false

            publicSubCommand(::RoleArguments) {
                name = "add".toKey()
                description = "Add a role to a user".toKey()

                action {
                    respond {
                        embeds = mutableListOf(addRemove(true, user.asMember(guild!!.id), arguments))
                    }
                }
            }

            publicSubCommand(::RoleArguments) {
                name = "remove".toKey()
                description = "Remove a role from a user".toKey()

                action {
                    respond {
                        embeds = mutableListOf(addRemove(false, user.asMember(guild!!.id), arguments))
                    }
                }
            }

            publicSubCommand(::RoleGroupRemoveArguments) {
                name = "remove-group".toKey()
                description = "Remove a role group from a user".toKey()

                action {
                    respond {
                        embeds = mutableListOf(removeRoleGroup(user.asMember(guild!!.id), arguments))
                    }
                }
            }

            group("config".toKey()) {
                description = "Change the settings of a role.".toKey()

                publicSubCommand(::RoleConfigSetArguments) {
                    name = "set".toKey()
                    description = "Set a role config value".toKey()

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        respond {
                            val currentRole = DiscordRoleConnection[guild!!.id.value.toLong()].authenticated()
                                .getById(arguments.role.id.value.toLong())

                            if (arguments.nameSchema == null && arguments.roleAction == null) {
                                if (currentRole == null) {
                                    throw NoOptionFoundException()
                                } else {
                                    embeds = mutableListOf(
                                        ApplicationService.loadEmbedFromDiscordRole(
                                            currentRole,
                                            locale = event.interaction.locale?.asJavaLocale()
                                        )
                                    )
                                }
                                return@respond
                            }

                            val modifiedRole = if (currentRole != null) {
                                DiscordRoleConnection[guild!!.id.value.toLong()].authenticated()
                                    .updateRole(
                                        arguments.role.id.value.toLong(),
                                        DiscordRoleUpdateModel(
                                            arguments.nameSchema,
                                            arguments.roleAction
                                        )
                                    )
                            } else {
                                DiscordRoleConnection[guild!!.id.value.toLong()].authenticated()
                                    .addNewRole(
                                        DiscordRoleCreationModel(
                                            arguments.role.id.value.toLong(),
                                            arguments.nameSchema,
                                            arguments.roleAction ?: RoleAction.None
                                        )
                                    )
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

                publicSubCommand(::RoleConfigResetArguments) {
                    name = "reset".toKey()
                    description = "Reset a role config value".toKey()

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        respond {
                            val currentRole = DiscordRoleConnection[guild!!.id.value.toLong()].authenticated()
                                .getById(arguments.role.id.value.toLong())

                            if (!arguments.resetNameSchema) {
                                if (currentRole == null) {
                                    throw NoOptionFoundException()
                                } else {
                                    embeds = mutableListOf(ApplicationService.loadEmbedFromDiscordRole(currentRole))
                                }
                                return@respond
                            }

                            val modifiedRole = if (currentRole != null) {
                                val updateModel = currentRole.getUpdateModel()

                                if (arguments.resetNameSchema) {
                                    updateModel.nameSchema = null
                                }

                                DiscordRoleConnection[guild!!.id.value.toLong()].authenticated()
                                    .updateRole(arguments.role.id.value.toLong(), updateModel)
                            } else {
                                DiscordRoleConnection[guild!!.id.value.toLong()].authenticated().addNewRole(
                                    DiscordRoleCreationModel(
                                        arguments.role.id.value.toLong()
                                    )
                                )
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
            }

            group(Role.Requirements.name) {
                description = Role.Requirements.description

                publicSubCommand(::RoleRequirementsGetArguments) {
                    name = Role.Requirements.Get.name
                    description = Role.Requirements.Get.description

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        val roleRequirements =
                            RoleRequirementConnection[guild!!.id.value.toLong()].authenticated().allRoleRequirements
                                ?.filter { it.discordRole.id == arguments.role.id.value.toLong() } ?: listOf()

                        if (roleRequirements.isEmpty()) {
                            respond {
                                addEmbed {
                                    color(EmbedColor.Negative)
                                    description = "This role doesn't have any role requirements set up!\n" +
                                            "Check how to create them [in the documentation](https://docs.dungeon-hub.net/role-management.html)."
                                }
                            }
                            return@action
                        }

                        @OptIn(AlwaysPublicResponse::class)
                        respondingPaginator {
                            for (roleRequirement in roleRequirements) {
                                page {
                                    copy(roleRequirement.toEmbed(getLocale()))
                                }
                            }
                        }.send()
                    }
                }

                publicSubCommand {
                    name = Role.Requirements.List.name
                    description = Role.Requirements.List.description

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        val roleRequirements =
                            RoleRequirementConnection[guild!!.id.value.toLong()].authenticated().allRoleRequirements
                                ?: listOf()

                        if (roleRequirements.isEmpty()) {
                            respond {
                                addEmbed {
                                    color(EmbedColor.Negative)
                                    description = "No role requirements are set up!\n" +
                                            "Check how to create them [in the documentation](https://docs.dungeon-hub.net/role-management.html)."
                                }
                            }
                            return@action
                        }

                        @OptIn(AlwaysPublicResponse::class)
                        respondingPaginator {
                            for (roleRequirement in roleRequirements) {
                                page {
                                    copy(roleRequirement.toEmbed(getLocale()))
                                }
                            }
                        }.send()
                    }
                }

                publicSubCommand(::RoleRequirementsAddArguments) {
                    name = Role.Requirements.Add.name
                    description = Role.Requirements.Add.description

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        val creationModel = RoleRequirementCreationModel(
                            arguments.role.id.value.toLong(),
                            arguments.requirementType,
                            arguments.comparison,
                            arguments.count,
                            arguments.extraData
                        )

                        val createdRoleRequirement =
                            RoleRequirementConnection[guild!!.id.value.toLong()].authenticated()
                                .addNewRoleRequirement(creationModel)
                                ?: throw CommandExecutionException("Couldn't add the role requirement.")

                        respond {
                            val embed = createdRoleRequirement.toEmbed(getLocale())
                            embed.title = "Created role requirement #${createdRoleRequirement.id}"
                            embeds = mutableListOf(embed)
                        }
                    }
                }

                publicSubCommand(::RoleRequirementsDeleteArguments) {
                    name = Role.Requirements.Delete.name
                    description = Role.Requirements.Delete.description

                    check {
                        hasPermission(Permission.Administrator)
                    }

                    action {
                        val roleRequirement =
                            RoleRequirementConnection[guild!!.id.value.toLong()].authenticated().getById(arguments.id)
                                ?: throw CommandExecutionWarning("That role requirement wasn't found!")

                        val deletedRoleRequirement =
                            RoleRequirementConnection[guild!!.id.value.toLong()].authenticated()
                                .deleteRoleRequirement(roleRequirement)
                                ?: throw CommandExecutionException("Role requirement couldn't be deleted.")

                        respond {
                            val embed = deletedRoleRequirement.toEmbed(getLocale())
                            embeds = mutableListOf(embed)
                        }
                    }
                }
            }
        }
    }

    override suspend fun unload() {
        scheduler.cancel("Extension shutting down.")
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

        scheduler.launch {
            val member = arguments.user.fetchMember(issuer.guildId)

            val updatedRoles = RolesService.updateRoles(member)

            try {
                NicknameService.updateNickname(member, updatedRoles)
            } catch (_: NoNameSchemaWarning) {
                //ignore this, in that case you just don't apply a nickname
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

        scheduler.launch {
            val member = arguments.target.fetchMember(issuer.guildId)

            val updatedRoles = RolesService.updateRoles(member)

            try {
                NicknameService.updateNickname(member, updatedRoles)
            } catch (_: NoNameSchemaWarning) {
                //ignore this, in that case you just don't apply a nickname
            }
        }

        return embed
    }

    class RoleArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "Select which user to modify the role of.".toKey()
        }

        val role by role {
            name = "role".toKey()
            description = "Select which role you mean.".toKey()
        }
    }

    class RoleGroupRemoveArguments : Arguments() {
        val target by user {
            name = "user".toKey()
            description = "Select which user to remove the role group of.".toKey()
        }

        val role by role {
            name = "role-group".toKey()
            description = "The role group to remove from the given user.".toKey()
        }
    }

    class RoleConfigSetArguments : Arguments() {
        val role by role {
            name = "role".toKey()
            description = "Select which role you want to configure.".toKey()
        }

        val nameSchema by optionalString {
            name = "name-schema".toKey()
            description = "Set the name schema for this username".toKey()
        }

        val roleAction by optionalEnumChoice<RoleAction> {
            name = "role-action".toKey()
            description = "Set when this role should be applied to users, based on if they're linked or not.".toKey()
            typeName = "RoleAction".toKey()
        }
    }

    class RoleConfigResetArguments : Arguments() {
        val role by role {
            name = "role".toKey()
            description = "Select which role you want to configure.".toKey()
        }

        val resetNameSchema by boolean {
            name = "name-schema".toKey()
            description = "Reset the name schema for this role.".toKey()
        }
    }

    class RoleRequirementsGetArguments : Arguments() {
        val role by role {
            name = "role".toKey()
            description = "Select which role you want to get the role requirements for.".toKey()
        }
    }

    class RoleRequirementsAddArguments : Arguments() {
        val role by role {
            name = "role".toKey()
            description = "Select which role you want to add a requirement for.".toKey()
        }

        val requirementType by enumChoice<RoleRequirementType> {
            name = "requirement-type".toKey()
            description = "Select what you want to use as the requirement.".toKey()
            typeName = "RoleRequirementType".toKey()
        }

        val comparison by enumChoice<RoleRequirementComparison> {
            name = "comparison".toKey()
            description = "Select which comparison you want to use for the requirement.".toKey()
            typeName = "RoleRequirementComparison".toKey()
        }

        val count by int {
            name = "count".toKey()
            description = "Select the required amount.".toKey()
        }

        val extraData by optionalString {
            name = "extra-data".toKey()
            description = "Enter some extra data, if applicable.".toKey()
        }
    }

    class RoleRequirementsDeleteArguments : Arguments() {
        val id by long {
            name = "id".toKey()
            description = "The id of the role requirement.".toKey()
        }
    }
}