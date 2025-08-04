package net.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kordex.core.annotations.AlwaysPublicResponse
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.pagination.pages.Page
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasPermission
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.connection.copy
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.client.DungeonHubClient
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.connection.WarningConnection
import net.dungeonhub.enums.WarningType
import net.dungeonhub.i18n.Translations.Command.Warn
import net.dungeonhub.i18n.Translations.Command.Warns
import net.dungeonhub.model.warning.WarningCreationModel
import net.dungeonhub.model.warning.WarningEvidenceCreationModel
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit

@OptIn(AlwaysPublicResponse::class)
@LoadExtension
class WarningSystem : Extension() {
    private val logger = LoggerFactory.getLogger(WarningSystem::class.java)
    override val name = "warning-system"

    override suspend fun setup() {
        ephemeralSlashCommand(::WarnsArguments) {
            name = Warns.name
            description = Warns.description
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
                noPermissionEmbed.color = EmbedColor.Negative.color
                noPermissionEmbed.description =
                    "You don't have the permission to see the warns of other people, so you're seeing your own."

                val warns = WarningConnection[guild!!.id.value.toLong()].authenticated()
                    .getActiveWarns(target.id.value.toLong())
                    ?: throw CommandExecutionException("Couldn't load active warns of the given user.")

                if (warns.isEmpty()) {
                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.Information.color
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
                                embed.color = EmbedColor.Default.color
                                embed.description = description
                                copy(embed)
                            }

                            title = "Warns of user ${target.tag}"
                        }
                    )

                    for (warning in warns) {
                        page(
                            Page {
                                val embed = ApplicationService.formatWarn(
                                    warning,
                                    showEvidences = user.hasPermission(Permission.ModerateMembers)
                                )

                                copy(embed)
                            }
                        )
                    }
                }.send()
            }
        }

        publicSlashCommand {
            name = Warn.name
            description = Warn.description
            allowInDms = false
            defaultMemberPermissions = Permissions(Permission.ModerateMembers)

            publicSubCommand(::WarnAddArguments) {
                name = "add".toKey()
                description = "Add a warning to the given user.".toKey()

                action {
                    respond {
                        val target = arguments.user

                        val type = try {
                            WarningType.valueOf(arguments.type)
                        } catch (_: IllegalArgumentException) {
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
                            WarningConnection[guild!!.id.value.toLong()].authenticated().addWarning(creationModel)
                                ?: throw CommandExecutionException("Error while trying to add a warning")

                        val actionDescription = ApplicationService.applyWarningActions(
                            addedWarning.warningActionModel,
                            target.asMember(guild!!.id)
                        )

                        val activeWarnings =
                            WarningConnection[guild!!.id.value.toLong()].authenticated()
                                .getActiveWarns(target.id.value.toLong())
                                ?: listOf()

                        val embed = ApplicationService.formatWarn(addedWarning.warningModel)
                        embed.description =
                            "That user now has ${activeWarnings.count()} active warnings, out of which **${activeWarnings.count { it.warningType == WarningType.Serious || it.warningType == WarningType.Major }}** are severe.${
                                if (actionDescription != null) {
                                    "\nThe user got punished with the following actions:\n$actionDescription"
                                } else {
                                    ""
                                }
                            }${
                                if (addedWarning.warningModel.warningType.expiration != null) {
                                    val expiresAfter = addedWarning.warningModel.warningType.expiration?.get(ChronoUnit.MONTHS)
                                    "\n\n_Please note that a ${addedWarning.warningModel.warningType.name} expires after **$expiresAfter month${if (expiresAfter == 1L) "" else "s"}**._"
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

                            if (addedWarning.warningModel.warningType.expiration != null) {
                                val expiresAfter = addedWarning.warningModel.warningType.expiration?.get(ChronoUnit.MONTHS)
                                dmEmbed.description += "\n\n_Please note that a ${addedWarning.warningModel.warningType.name} expires after **$expiresAfter month${if (expiresAfter == 1L) "" else "s"}**._\n_If you want a related punishment removed **after the strikes have expired**, please contact server staff through the support._"
                            }

                            this@dm.embeds = mutableListOf(dmEmbed)
                        }
                    }
                }
            }

            //TODO what about reactivate?
            publicSubCommand(::WarnRemoveArguments) {
                name = "deactivate".toKey()
                description = "Deactivate a given warning.".toKey()

                action {
                    respond {
                        val removedWarning =
                            WarningConnection[guild!!.id.value.toLong()].authenticated().deactivateWarning(arguments.id)
                                ?: throw InvalidOptionException("id", "Couldn't find a warning with the given id.")

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Positive.color
                        embed.description =
                            "Deactivated warning #${arguments.id} from user <@${removedWarning.user.id}>."
                        embeds = mutableListOf(embed)

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
                                    logEmbed.color = EmbedColor.Information.color
                                    logEmbed.description = "<@${user.id}> deactivated warning #${removedWarning.id}."

                                    this@createMessage.embeds = mutableListOf(logEmbed)
                                }
                            }

                        try {
                            //TODO request exception
                            DiscordConnection.bot!!.kordRef.getUser(Snowflake(removedWarning.user.id))
                                ?.dm {
                                    val dmEmbed = ApplicationService.embed
                                    dmEmbed.color = EmbedColor.Positive.color
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
                name = "list-all".toKey()
                description = "List all warnings of a user, active or not.".toKey()

                action {
                    val warns =
                        WarningConnection[guild!!.id.value.toLong()].authenticated()
                            .getAllWarns(arguments.user.id.value.toLong())
                            ?: throw CommandExecutionException("Couldn't load all warns of the given user.")

                    if (warns.isEmpty()) {
                        respond {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Information.color
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
                name = "add-evidence".toKey()
                description = "Adds evidence to a warning.".toKey()

                action {
                    respond {
                        val evidence: String = if (arguments.attachment != null) {
                            val attachmentRequest =
                                Request.Builder().url(arguments.attachment!!.url.toHttpUrl()).build()

                            val attachmentData = DungeonHubClient().executeRawRequest(attachmentRequest)?.result
                                ?: throw CommandExecutionException("Couldn't read file data.")

                            val uri = ContentConnection.authenticated().uploadFile(attachmentData)
                                ?: throw CommandExecutionException("Couldn't upload file data to the cdn.")

                            ContentConnection.authenticated().getCdnUrl(uri).toString()
                        } else if (arguments.text != null) {
                            arguments.text!!
                        } else {
                            throw CommandExecutionException("Please either add an attachment or a text as evidence.")
                        }

                        val creationModel = WarningEvidenceCreationModel(evidence, user.id.value.toLong())

                        val warning =
                            WarningConnection[guild!!.id.value.toLong()].authenticated()
                                .addEvidence(arguments.id, creationModel)
                                ?: throw CommandExecutionException("Failed to add evidence to that warning. Did you enter a correct id?")

                        if (arguments.attachment != null && arguments.text != null) {
                            val embed = ApplicationService.embedWithoutTimestamp
                            embed.color = EmbedColor.Negative.color
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
                                    logEmbed.color = EmbedColor.Information.color
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

    class WarnsArguments : Arguments() {
        val target by optionalMember {
            name = "user".toKey()
            description = "The user to get the warns of.".toKey()
        }
    }

    class WarnAddArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The user to warn.".toKey()
        }

        val type by stringChoice {
            name = "severity".toKey()
            description = "The severity of the warning.".toKey()

            WarningType.entries.forEach { choice(it.name.toKey(), it.name) }
        }

        val reason by optionalString {
            name = "reason".toKey()
            description = "The reason for the warning.".toKey()
            maxLength = 200
        }
    }

    class WarnRemoveArguments : Arguments() {
        val id by long {
            name = "id".toKey()
            description = "The id of the warning.".toKey()
            minValue = 1
        }
    }

    class WarnListAllArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The user to see the warnings of.".toKey()
        }
    }

    class WarnAddEvidenceArguments : Arguments() {
        val id by long {
            name = "id".toKey()
            description = "The id of the warning.".toKey()
            minValue = 1
        }

        val attachment by optionalAttachment {
            name = "attachment".toKey()
            description = "Add an attachment (image or similar) as evidence.".toKey()
        }

        val text by optionalString {
            name = "text".toKey()
            description = "Add a text (or link) as evidence.".toKey()
        }
    }

    private fun getChannelProperty(warningType: WarningType): ServerProperty {
        return when (warningType) {
            WarningType.Serious, WarningType.Major, WarningType.Minor -> ServerProperty.MODERATION_LOGS_CHANNEL
            WarningType.Strike, WarningType.Warning -> ServerProperty.STRIKES_LOGS_CHANNEL
        }
    }
}