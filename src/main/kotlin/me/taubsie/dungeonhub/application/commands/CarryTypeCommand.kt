package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeCreationModel
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeUpdateModel
import java.util.*

@LoadExtension
class CarryTypeCommand : Extension() {
    override val name = "carry-type-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "carry-type"
            description = "Set up the carry types for this server."
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryTypeCreateArguments) {
                name = "create"
                description = "Create a new carry type"

                action {
                    respond {
                        val identifier = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(identifier).isPresent
                        ) {
                            throw InvalidOptionException("identifier", "That carry type already exists!")
                        }

                        val creationModel = CarryTypeCreationModel(identifier, arguments.displayName)

                        if (arguments.logChannel != null) {
                            creationModel.setLogChannel(arguments.logChannel!!.id.value.toLong())
                        }

                        if (arguments.leaderboardChannel != null) {
                            creationModel.setLeaderboardChannel(arguments.leaderboardChannel!!.id.value.toLong())
                        }

                        if (arguments.eventActive != null) {
                            creationModel.setEventActive(arguments.eventActive)
                        }

                        val carryTypeModel =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .addNewCarryType(creationModel)
                                .orElse(null)

                        if (carryTypeModel == null) {
                            //TODO custom class?
                            throw CommandExecutionException("Couldn't add that carry type.")
                        }

                        val embed = ApplicationService.getCarryTypeEmbed(carryTypeModel)
                        embed.title = "Carry Type created"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeArguments) {
                name = "delete"
                description = "Delete a carry type"

                action {
                    respond {
                        val identifier = arguments.carryType

                        val carryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(identifier)

                        if (carryType.isEmpty) {
                            //TODO custom class
                            throw CommandExecutionException("That carry type doesn't exists!")
                        }

                        val deletedCarryType =
                            CarryTypeConnection.getInstance(
                                carryType.get().server.id
                            )
                                .deleteCarryType(carryType.get())
                                .orElse(null)

                        if (deletedCarryType == null) {
                            //TODO custom class
                            throw CommandExecutionException("Carry type couldn't be deleted!")
                        }

                        val embed = ApplicationService.getCarryTypeEmbed(deletedCarryType)
                        embed.title = "Deleted Carry Type"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeArguments) {
                name = "get"
                description = "Get information about a carry type"

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(arguments.carryType)
                                .orElse(null)

                        if (carryType == null) {
                            //TODO custom exception class
                            throw CommandExecutionException("Carry type not found.")
                        }

                        val embed = ApplicationService.getCarryTypeEmbed(carryType)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeEditArguments) {
                name = "edit"
                description = "Edit a carry type"

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(arguments.carryType)
                                .orElse(null)

                        if (carryType == null) {
                            //TODO custom class
                            throw CommandExecutionException("That carry type doesn't exists!")
                        }

                        if (arguments.displayName == null && arguments.logChannel == null && arguments.leaderboardChannel == null && arguments.eventActive == null) {
                            //TODO custom class
                            throw CommandExecutionException("Please provide something you want to edit.")
                        }

                        val updateModel = CarryTypeUpdateModel()

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
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .updateCarryType(carryType.id, updateModel)
                                .orElse(null)

                        if (updatedCarryType == null) {
                            //TODO custom class
                            throw CommandExecutionException("Couldn't update carry type.")
                        }

                        val embed = ApplicationService.getCarryTypeEmbed(updatedCarryType)
                        embed.title = "Updated Carry Type"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTypeResetArguments) {
                name = "reset"
                description = "Reset properties of a carry type"

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(arguments.carryType)
                                .orElse(null)

                        if (carryType == null) {
                            //TODO custom class
                            throw CommandExecutionException("That carry type doesn't exists!")
                        }

                        if (!arguments.logChannel && !arguments.leaderboardChannel) {
                            //TODO custom class
                            throw CommandExecutionException("Please provide something you want to reset.")
                        }

                        val updateModel = CarryTypeUpdateModel()

                        if (arguments.logChannel) {
                            updateModel.logChannel = -1L
                        }

                        if (arguments.leaderboardChannel) {
                            updateModel.leaderboardChannel = -1L
                        }

                        val updatedCarryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .updateCarryType(carryType.id, updateModel)
                                .orElse(null)

                        if (updatedCarryType == null) {
                            //TODO custom class
                            throw CommandExecutionException("Couldn't update carry type.")
                        }

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
            name = "identifier"
            description = "The identifier of the carry type."
            maxLength = 30
        }

        val displayName by string {
            name = "display-name"
            description = "The display name of the carry type"
            maxLength = 30
        }

        val logChannel by optionalChannel {
            name = "log-channel"
            description = "Set the channel that will be used for logging"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val leaderboardChannel by optionalChannel {
            name = "leaderboard-channel"
            description = "Set the channel that will be used to show a static leaderboard"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val eventActive by optionalBoolean {
            name = "event-active"
            description = "Set if there if an active event for score"
        }
    }

    inner class CarryTypeArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }
    }

    inner class CarryTypeEditArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val displayName by optionalString {
            name = "display-name"
            description = "Set the display name"
            maxLength = 30
        }

        val logChannel by optionalChannel {
            name = "log-channel"
            description = "Set the channel that will be used for logging"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val leaderboardChannel by optionalChannel {
            name = "leaderboard-channel"
            description = "Set the channel that will be used to show a static leaderboard"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val eventActive by optionalBoolean {
            name = "event-active"
            description = "Set if there if an active event for score"
        }
    }

    inner class CarryTypeResetArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val logChannel by boolean {
            name = "log-channel"
            description = "Set if the log channel should be reset"
        }

        val leaderboardChannel by boolean {
            name = "leaderboard-channel"
            description = "Set if the leaderboard channel should be reset"
        }
    }
}