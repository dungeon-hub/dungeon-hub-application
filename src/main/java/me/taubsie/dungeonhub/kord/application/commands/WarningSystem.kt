package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.annotations.AlwaysPublicResponse
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.long
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMember
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.pagination.pages.Page
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.dungeon_hub.WarningConnection
import me.taubsie.dungeonhub.common.enums.WarningType
import me.taubsie.dungeonhub.common.model.warning.WarningCreationModel
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.connection.copy
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import org.slf4j.LoggerFactory

@OptIn(AlwaysPublicResponse::class)
@LoadExtension
class WarningSystem : Extension() {
    private val logger = LoggerFactory.getLogger(WarningSystem::class.java)
    override val name = "warning-system"

    override suspend fun setup() {
        ephemeralSlashCommand(::WarnsArguments) {
            name = "warns"
            description = "Lets you see your active warnings."
            allowInDms = false

            action {
                val user = user.asMember(guild!!.id)
                var target = user
                var noPermission = false

                if (arguments.target != null && arguments.target!!.id != user.id) {
                    if (!user.isOwner()
                        && !user.hasPermission(Permission.Administrator)
                        && !user.hasPermission(Permission.ModerateMembers)
                    ) {
                        noPermission = true
                    } else {
                        target = arguments.target!!
                    }
                }

                val noPermissionEmbed = ApplicationService.embed
                noPermissionEmbed.color = EmbedColor.NEGATIVE.color
                noPermissionEmbed.description =
                    "You don't have the permission to see the warns of other people, so you're seeing your own."

                val warns = WarningConnection.getInstance(guild!!.id.value.toLong())
                    .getActiveWarns(target.id.value.toLong())
                    .orElseThrow { CommandExecutionException("Couldn't load active warns of the given user.") }

                if (warns.isEmpty()) {
                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.INFORMATION.color
                    embed.title = "Warns of user ${target.tag}"
                    embed.description = "User hasn't been warned yet!"

                    respond {
                        embeds = if (noPermission) {
                            mutableListOf(noPermissionEmbed, embed)
                        } else {
                            mutableListOf(embed)
                        }
                    }
                    return@action
                }

                respondingPaginator {
                    owner = this@action.user

                    page(
                        Page {
                            val description =
                                "User ${target.tag} has ${warns.count()} active warns.\n\nTo see further details, check the next pages for a list of all of them."

                            if (noPermission) {
                                noPermissionEmbed.description += "\n\n$description"
                                copy(noPermissionEmbed)
                            } else {
                                val embed = ApplicationService.embed
                                embed.color = EmbedColor.DEFAULT.color
                                embed.description = description
                                copy(embed)
                            }

                            title = "Strikes of user ${target.tag}"
                        }
                    )

                    for (warning in warns) {
                        page(
                            Page {
                                val embed = ApplicationService.formatWarn(warning)

                                copy(embed)
                            }
                        )
                    }
                }.send()
            }
        }

        publicSlashCommand {
            name = "warn"
            description = "Manage the warnings of a server member."
            allowInDms = false
            defaultMemberPermissions = Permissions(Permission.ModerateMembers)

            publicSubCommand(::WarnAddArguments) {
                name = "add"
                description = "Add a warning to the given user."

                action {
                    respond {
                        val target = arguments.user

                        val type = try {
                            WarningType.valueOf(arguments.type)
                        } catch (illegalArgumentException: IllegalArgumentException) {
                            val embed = ApplicationService.getErrorEmbed(InvalidOptionException("type"))
                            embeds = mutableListOf(embed)
                            return@respond
                        }

                        val creationModel = WarningCreationModel(
                            target.id.value.toLong(),
                            user.id.value.toLong(),
                            type,
                            arguments.reason,
                            true
                        )

                        val addedWarning = WarningConnection.getInstance(guild!!.id.value.toLong())
                            .addWarning(creationModel)
                            .orElseThrow { CommandExecutionException("Error while trying to add a warning") }

                        val embed = ApplicationService.formatWarn(addedWarning)
                        embeds = mutableListOf(embed)

                        ServerProperty.STRIKES_LOGS_CHANNEL
                            .getValue(guild!!.id.value.toLong())
                            .map {
                                runBlocking {
                                    DiscordConnection.bot!!.kordRef.getChannelOf<GuildMessageChannel>(Snowflake(it))
                                }
                            }
                            .orElse(null)
                            ?.let { channel ->
                                channel.createMessage {
                                    val logEmbed = ApplicationService.formatWarnLog(addedWarning)

                                    this@createMessage.embeds = mutableListOf(logEmbed)
                                }
                            }

                        //TODO request exception
                        target.dm {
                            val dmEmbed = ApplicationService.formatWarnDm(addedWarning)
                            this@dm.embeds = mutableListOf(dmEmbed)
                        }
                    }
                }
            }

            publicSubCommand(::WarnRemoveArguments) {
                name = "remove"
                description = "Remove a given warning."

                action {
                    respond {
                        val removedWarning = WarningConnection.getInstance(guild!!.id.value.toLong())
                            .removeWarning(arguments.id)
                            .orElseThrow { InvalidOptionException("id", "Couldn't find a warning with the given id.") }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.POSITIVE.color
                        embed.description = "Removed warning #${arguments.id} from user <@${removedWarning.user.id}>."

                        ServerProperty.STRIKES_LOGS_CHANNEL
                            .getValue(guild!!.id.value.toLong())
                            .map {
                                runBlocking {
                                    DiscordConnection.bot!!.kordRef.getChannelOf<GuildMessageChannel>(Snowflake(it))
                                }
                            }
                            .orElse(null)
                            ?.let { channel ->
                                channel.createMessage {
                                    val logEmbed = ApplicationService.embed
                                    logEmbed.color = EmbedColor.INFORMATION.color
                                    logEmbed.description = "<@${user.id}> removed warning #${removedWarning.id}."

                                    this@createMessage.embeds = mutableListOf(logEmbed)
                                }
                            }

                        try {
                            //TODO request exception
                            DiscordConnection.bot!!.kordRef.getUser(Snowflake(removedWarning.user.id))
                                ?.dm {
                                    val dmEmbed = ApplicationService.embed
                                    dmEmbed.color = EmbedColor.POSITIVE.color
                                    dmEmbed.description =
                                        "The warning with id ${removedWarning.id} was removed from you!"
                                    this@dm.embeds = mutableListOf(embed)
                                }
                        } catch (exception: Exception) {
                            logger.error(null, exception)
                        }
                    }
                }
            }

            publicSubCommand(::WarnListAllArguments) {
                name = "list-all"
                description = "List all warnings of a user, active or not."

                action {
                    val warns = WarningConnection.getInstance(guild!!.id.value.toLong())
                        .getAllWarns(arguments.user.id.value.toLong())
                        .orElseThrow { CommandExecutionException("Couldn't load all warns of the given user.") }

                    if (warns.isEmpty()) {
                        respond {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.INFORMATION.color
                            embed.title = "Warns of user ${arguments.user.tag}"
                            embed.description = "User has no warns!"

                            embeds = mutableListOf(embed)
                        }
                        return@action
                    }

                    respondingPaginator {
                        owner = this@action.user

                        for (warning in warns) {
                            page(
                                Page {
                                    val embed = ApplicationService.formatWarn(warning)

                                    copy(embed)
                                }
                            )
                        }
                    }.send()
                }
            }
        }
    }

    inner class WarnsArguments : Arguments() {
        val target by optionalMember {
            name = "user"
            description = "The user to get the warns of."
        }
    }

    inner class WarnAddArguments : Arguments() {
        val user by user {
            name = "user"
            description = "The user to warn."
        }

        val type by stringChoice {
            name = "type"
            description = "The type of warning."

            WarningType.entries.forEach { choice(it.name, it.name) }
        }

        val reason by optionalString {
            name = "reason"
            description = "The reason for the warning."
            maxLength = 200
        }
    }

    inner class WarnRemoveArguments : Arguments() {
        val id by long {
            name = "id"
            description = "The id of the warning."
            minValue = 1
        }
    }

    inner class WarnListAllArguments : Arguments() {
        val user by user {
            name = "user"
            description = "The user to see the warnings of."
        }
    }
}