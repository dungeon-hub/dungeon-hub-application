package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.annotations.AlwaysPublicResponse
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
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
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection
import me.taubsie.dungeonhub.application.connection.copy
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.WarningConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.ServerProperty
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.common.enums.WarningType
import me.taubsie.dungeonhub.common.model.warning.WarningCreationModel
import me.taubsie.dungeonhub.common.model.warning.WarningEvidenceCreationModel
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
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

                val warns =
                    WarningConnection.getInstance(guild!!.id.value.toLong())
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

                        val addedWarning =
                            WarningConnection.getInstance(guild!!.id.value.toLong())
                                .addWarning(creationModel)
                                .orElseThrow { CommandExecutionException("Error while trying to add a warning") }

                        val actionDescription = ApplicationService.applyWarningActions(
                            addedWarning.warningActionModel,
                            target.asMember(guild!!.id)
                        )

                        val activeWarnings = WarningConnection.getInstance(guild!!.id.value.toLong())
                            .getActiveWarns(target.id.value.toLong())
                            .orElse(listOf())

                        val embed = ApplicationService.formatWarn(addedWarning.warningModel)
                        embed.description =
                            "That user now has ${activeWarnings.count()} active warnings, out of which **${activeWarnings.count { it.warningType == WarningType.Serious || it.warningType == WarningType.Major }}** are severe.${
                                if (actionDescription != null) {
                                    "\nThe user got punished with the following actions:\n$actionDescription"
                                } else {
                                    ""
                                }
                            }${
                                if (addedWarning.warningModel.warningType == WarningType.Strike) {
                                    "\n\n_Please note that strikes expire after 3 months._"
                                } else {
                                    ""
                                }
                            }"
                        embeds = mutableListOf(embed)

                        getChannelProperty(addedWarning.warningModel.warningType)
                            .getValue(guild!!.id.value.toLong())
                            .map {
                                runBlocking {
                                    DiscordConnection.bot!!.kordRef.getChannelOf<GuildMessageChannel>(Snowflake(it))
                                }
                            }
                            .orElse(null)
                            ?.let { channel ->
                                channel.createMessage {
                                    val logEmbed = ApplicationService.formatWarnLog(addedWarning.warningModel)

                                    if (actionDescription != null) {
                                        logEmbed.description =
                                            "The following actions were applied to the user:\n$actionDescription"
                                    }

                                    this@createMessage.embeds = mutableListOf(logEmbed)
                                }
                            }

                        //TODO request exception
                        target.dm {
                            val dmEmbed = ApplicationService.formatWarnDm(addedWarning.warningModel)
                            if (actionDescription != null) {
                                dmEmbed.description =
                                    "You currently have ${activeWarnings.count()} active warnings, due to which you were punished with the following:\n$actionDescription"
                            } else {
                                dmEmbed.description = "You currently have ${activeWarnings.count()} active warnings."
                            }

                            if (addedWarning.warningModel.warningType == WarningType.Strike) {
                                dmEmbed.description += "\n\n*_Please note that strikes expire after 3 months._\n_If you want a related punishment removed **after the strikes have expired**, please contact server staff through the support._"
                            }

                            this@dm.embeds = mutableListOf(dmEmbed)
                        }
                    }
                }
            }

            //TODO what about reactivate?
            publicSubCommand(::WarnRemoveArguments) {
                name = "deactivate"
                description = "Deactivate a given warning."

                action {
                    respond {
                        val removedWarning =
                            WarningConnection.getInstance(guild!!.id.value.toLong())
                                .deactivateWarning(arguments.id)
                                .orElseThrow {
                                    InvalidOptionException(
                                        "id",
                                        "Couldn't find a warning with the given id."
                                    )
                                }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.POSITIVE.color
                        embed.description =
                            "Deactivated warning #${arguments.id} from user <@${removedWarning.user.id}>."

                        getChannelProperty(removedWarning.warningType)
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
                                    logEmbed.description = "<@${user.id}> deactivated warning #${removedWarning.id}."

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
                    val warns =
                        WarningConnection.getInstance(guild!!.id.value.toLong())
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

            publicSubCommand(::WarnAddEvidenceArguments) {
                name = "add-evidence"
                description = "Adds evidence to a warning."

                action {
                    respond {
                        val evidence: String = if (arguments.attachment != null) {
                            val attachmentRequest =
                                Request.Builder().url(arguments.attachment!!.url.toHttpUrl()).build()

                            val attachmentData =
                                DungeonHubConnection.getInstance()
                                    .executeRawRequest(attachmentRequest)
                                    .orElseThrow { CommandExecutionException("Couldn't read file data.") }

                            val uri =
                                ContentConnection.getInstance()
                                    .uploadFile(attachmentData)
                                    .orElseThrow { CommandExecutionException("Couldn't upload file data to the cdn.") }

                            ContentConnection.getInstance()
                                .getCdnUrl(uri).toString()
                        } else if (arguments.text != null) {
                            arguments.text!!
                        } else {
                            throw CommandExecutionException("Please either add an attachment or a text as evidence.")
                        }

                        val creationModel = WarningEvidenceCreationModel(evidence, user.id.value.toLong())

                        val warning =
                            WarningConnection.getInstance(guild!!.id.value.toLong())
                                .addEvidence(arguments.id, creationModel)
                                .orElseThrow { CommandExecutionException("Failed to add evidence to that warning. Did you enter a correct id?") }

                        if (arguments.attachment != null && arguments.text != null) {
                            val embed = ApplicationService.embedWithoutTimestamp
                            embed.color = EmbedColor.NEGATIVE.color
                            embed.description =
                                "Please only provide either an attachment or a text. The given text wasn't added as evidence: ```\n${arguments.text}\n```"
                            embed.footer = null

                            embeds = mutableListOf(embed, ApplicationService.formatWarn(warning))
                        } else {
                            embeds = mutableListOf(ApplicationService.formatWarn(warning))
                        }

                        getChannelProperty(warning.warningType)
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
                                    logEmbed.description =
                                        "<@${user.id}> added an evidence to warning #${warning.id}:\n\n$evidence"

                                    this@createMessage.embeds = mutableListOf(logEmbed)
                                }
                            }
                    }
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
            name = "severity"
            description = "The severity of the warning."

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

    inner class WarnAddEvidenceArguments : Arguments() {
        val id by long {
            name = "id"
            description = "The id of the warning."
            minValue = 1
        }

        val attachment by optionalAttachment {
            name = "attachment"
            description = "Add an attachment (image or similar) as evidence."
        }

        val text by optionalString {
            name = "text"
            description = "Add a text (or link) as evidence."
        }
    }

    private fun getChannelProperty(warningType: WarningType): ServerProperty {
        return when (warningType) {
            WarningType.Serious, WarningType.Major, WarningType.Minor -> ServerProperty.MODERATION_LOGS_CHANNEL
            WarningType.Strike -> ServerProperty.STRIKES_LOGS_CHANNEL
        }
    }
}