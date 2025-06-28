package net.dungeonhub.application.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getLocale
import net.dungeonhub.application.exceptions.CommandExecutionWarning
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.i18n.Translations.Command.CarryType
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.carry_type.CarryTypeCreationModel
import java.util.*

/**
 * Command to manage carry types.
 * This command allows the user to create, delete, get and edit carry types.
 * The user can also reset the log channel and leaderboard channel of a carry type.
 * The user can also set if an event is active for a carry type.
 * It has the following subcommands:
 * - `create`: Create a new carry type
 * - `delete`: Delete a carry type
 * - `get`: Get information about a carry type
 * - `edit`: Edit a carry type
 * - `reset`: Reset properties of a carry type
 */
@LoadExtension
class CarryTypeCommand : Extension() {
    override val name = "carry-type-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = CarryType.name
            description = CarryType.description
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryTypeCreateArguments) {
                name = CarryType.Create.name
                description = CarryType.Create.description

                action {
                    respond {
                        val identifier = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(identifier) != null
                        ) {
                            throw InvalidOptionException(
                                CommonArguments.identifier.translateLocale(event.getLocale()),
                                "That carry type already exists!"
                            )
                        }

                        val creationModel = CarryTypeCreationModel(identifier, arguments.displayName)

                        if (arguments.logChannel != null) {
                            creationModel.logChannel = arguments.logChannel!!.id.value.toLong()
                        }

                        if (arguments.leaderboardChannel != null) {
                            creationModel.leaderboardChannel = arguments.leaderboardChannel!!.id.value.toLong()
                        }

                        if (arguments.eventActive != null) {
                            creationModel.eventActive = arguments.eventActive
                        }

                        val carryTypeModel =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .addNewCarryType(creationModel)
                                ?: throw CommandExecutionWarning("Couldn't add that carry type.")

                        val embed = ApplicationService.getCarryTypeEmbed(carryTypeModel)
                        embed.title = CarryType.Create.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeArguments) {
                name = CarryType.Delete.name
                description = CarryType.Delete.description

                action {
                    respond {
                        val identifier = arguments.carryType

                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated().getByIdentifier(identifier)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val deletedCarryType =
                            CarryTypeConnection[carryType.server.id].authenticated().deleteCarryType(carryType)
                                ?: throw CommandExecutionWarning("Carry type couldn't be deleted!")

                        val embed = ApplicationService.getCarryTypeEmbed(deletedCarryType)
                        embed.title = CarryType.Delete.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeArguments) {
                name = CarryType.Get.name
                description = CarryType.Get.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("Carry type not found.")

                        val embed = ApplicationService.getCarryTypeEmbed(carryType)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeEditArguments) {
                name = CarryType.Edit.name
                description = CarryType.Edit.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        if (arguments.displayName == null && arguments.logChannel == null && arguments.leaderboardChannel == null && arguments.eventActive == null) {
                            throw CommandExecutionWarning("Please provide something you want to edit.")
                        }

                        val updateModel = carryType.getUpdateModel()

                        if (arguments.displayName != null) {
                            updateModel.displayName = arguments.displayName
                        }

                        if (arguments.logChannel != null) {
                            updateModel.logChannel = arguments.logChannel!!.id.value.toLong()
                        }

                        if (arguments.leaderboardChannel != null) {
                            updateModel.leaderboardChannel = arguments.leaderboardChannel!!.id.value.toLong()
                        }

                        if (arguments.eventActive != null) {
                            updateModel.eventActive = arguments.eventActive
                        }

                        val updatedCarryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .updateCarryType(carryType.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry type.")

                        val embed = ApplicationService.getCarryTypeEmbed(updatedCarryType)
                        embed.title = CarryType.Edit.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeResetArguments) {
                name = CarryType.Reset.name
                description = CarryType.Reset.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        if (!arguments.logChannel && !arguments.leaderboardChannel) {
                            throw CommandExecutionWarning("Please provide something you want to reset.")
                        }

                        val updateModel = carryType.getUpdateModel()

                        if (arguments.logChannel) {
                            updateModel.logChannel = -1L
                        }

                        if (arguments.leaderboardChannel) {
                            updateModel.leaderboardChannel = -1L
                        }

                        val updatedCarryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .updateCarryType(carryType.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry type.")

                        val embed = ApplicationService.getCarryTypeEmbed(updatedCarryType)
                        embed.title = CarryType.Reset.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    class CarryTypeCreateArguments : Arguments() {
        val identifier by string {
            name = CommonArguments.identifier
            description = CommonArguments.CarryType.description
            maxLength = 30
        }

        val displayName by string {
            name = CommonArguments.displayName
            description = CarryType.Create.Arguments.DisplayName.description
            maxLength = 30
        }

        val logChannel by optionalChannel {
            name = CarryType.Create.Arguments.LogChannel.name
            description = CarryType.Create.Arguments.LogChannel.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val leaderboardChannel by optionalChannel {
            name = CarryType.Create.Arguments.LeaderboardChannel.name
            description = CarryType.Create.Arguments.LeaderboardChannel.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val eventActive by optionalBoolean {
            name = CarryType.Create.Arguments.EventActive.name
            description = CarryType.Create.Arguments.EventActive.description
        }
    }

    class CarryTypeArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }
    }

    class CarryTypeEditArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val displayName by optionalString {
            name = CommonArguments.displayName
            description = CarryType.Edit.Arguments.DisplayName.description
            maxLength = 30
        }

        val logChannel by optionalChannel {
            name = CarryType.Edit.Arguments.LogChannel.name
            description = CarryType.Edit.Arguments.LogChannel.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val leaderboardChannel by optionalChannel {
            name = CarryType.Edit.Arguments.LeaderboardChannel.name
            description = CarryType.Edit.Arguments.LeaderboardChannel.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val eventActive by optionalBoolean {
            name = CarryType.Edit.Arguments.EventActive.name
            description = CarryType.Edit.Arguments.EventActive.description
        }
    }

    class CarryTypeResetArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val logChannel by boolean {
            name = CarryType.Reset.Arguments.LogChannel.name
            description = CarryType.Reset.Arguments.LogChannel.description
        }

        val leaderboardChannel by boolean {
            name = CarryType.Reset.Arguments.LeaderboardChannel.name
            description = CarryType.Reset.Arguments.LeaderboardChannel.description
        }
    }
}