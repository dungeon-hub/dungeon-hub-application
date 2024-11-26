package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryTypeConnection
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
            name = "carry-type".toKey()
            description = "Set up the carry types for this server.".toKey()
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryTypeCreateArguments) {
                name = "create".toKey()
                description = "Create a new carry type".toKey()

                action {
                    respond {
                        val identifier = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(identifier) != null) {
                            throw InvalidOptionException("identifier", "That carry type already exists!")
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
                            CarryTypeConnection[guild!!.id.value.toLong()].addNewCarryType(creationModel)
                                ?: throw CommandExecutionWarning("Couldn't add that carry type.")

                        val embed = ApplicationService.getCarryTypeEmbed(carryTypeModel)
                        embed.title = "Carry Type created"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeArguments) {
                name = "delete".toKey()
                description = "Delete a carry type".toKey()

                action {
                    respond {
                        val identifier = arguments.carryType

                        val carryType = CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(identifier)
                            ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val deletedCarryType = CarryTypeConnection[carryType.server.id].deleteCarryType(carryType)
                            ?: throw CommandExecutionWarning("Carry type couldn't be deleted!")

                        val embed = ApplicationService.getCarryTypeEmbed(deletedCarryType)
                        embed.title = "Deleted Carry Type"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeArguments) {
                name = "get".toKey()
                description = "Get information about a carry type".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("Carry type not found.")

                        val embed = ApplicationService.getCarryTypeEmbed(carryType)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeEditArguments) {
                name = "edit".toKey()
                description = "Edit a carry type".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
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
                            CarryTypeConnection[guild!!.id.value.toLong()].updateCarryType(carryType.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry type.")

                        val embed = ApplicationService.getCarryTypeEmbed(updatedCarryType)
                        embed.title = "Updated Carry Type"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeResetArguments) {
                name = "reset".toKey()
                description = "Reset properties of a carry type".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
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
                            CarryTypeConnection[guild!!.id.value.toLong()].updateCarryType(carryType.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry type.")

                        val embed = ApplicationService.getCarryTypeEmbed(updatedCarryType)
                        embed.title = "Updated Carry Type with reset values"
                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    inner class CarryTypeCreateArguments : Arguments() {
        val identifier by string {
            name = "identifier".toKey()
            description = "The identifier of the carry type.".toKey()
            maxLength = 30
        }

        val displayName by string {
            name = "display-name".toKey()
            description = "The display name of the carry type".toKey()
            maxLength = 30
        }

        val logChannel by optionalChannel {
            name = "log-channel".toKey()
            description = "Set the channel that will be used for logging".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val leaderboardChannel by optionalChannel {
            name = "leaderboard-channel".toKey()
            description = "Set the channel that will be used to show a static leaderboard".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val eventActive by optionalBoolean {
            name = "event-active".toKey()
            description = "Set if there if an active event for score".toKey()
        }
    }

    inner class CarryTypeArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }
    }

    inner class CarryTypeEditArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val displayName by optionalString {
            name = "display-name".toKey()
            description = "Set the display name".toKey()
            maxLength = 30
        }

        val logChannel by optionalChannel {
            name = "log-channel".toKey()
            description = "Set the channel that will be used for logging".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val leaderboardChannel by optionalChannel {
            name = "leaderboard-channel".toKey()
            description = "Set the channel that will be used to show a static leaderboard".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val eventActive by optionalBoolean {
            name = "event-active".toKey()
            description = "Set if there if an active event for score".toKey()
        }
    }

    inner class CarryTypeResetArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val logChannel by boolean {
            name = "log-channel".toKey()
            description = "Set if the log channel should be reset".toKey()
        }

        val leaderboardChannel by boolean {
            name = "leaderboard-channel".toKey()
            description = "Set if the leaderboard channel should be reset".toKey()
        }
    }
}