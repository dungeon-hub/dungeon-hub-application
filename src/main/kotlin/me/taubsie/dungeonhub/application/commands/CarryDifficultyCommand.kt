package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection

/**
 * Command to manage carry difficulties.
 * This command allows you to create, delete, get, edit and reset information of a carry difficulty.
 * The command is only available for users with the Administrator permission and is not available in DMs.
 */
@LoadExtension
class CarryDifficultyCommand : Extension() {
    override val name = "carry-difficulty-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "carry-difficulty"
            description = "Set up the carry difficulties for this server."
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryDifficultyCreateArguments) {
                name = "create"
                description = "Create a new carry difficulty"

                //TODO implement
                action {
                    throw InvalidSubCommandException()
                }
            }

            publicSubCommand(::CarryDifficultyArguments) {
                name = "delete"
                description = "Delete a carry difficulty"

                //TODO implement
                action {
                    throw InvalidSubCommandException()
                }
            }

            publicSubCommand(::CarryDifficultyArguments) {
                name = "get"
                description = "Get information about a carry difficulty"

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier = CarryTierConnection[carryType]
                            .getByIdentifier(arguments.carryTier)
                            ?: throw CommandExecutionWarning("That carry tier doesn't exists!")

                        val carryDifficulty =
                            CarryDifficultyConnection[carryTier].getByIdentifier(arguments.carryDifficulty)
                                ?: throw InvalidOptionException(
                                    "carry-difficulty",
                                    "That carry difficulty doesn't exist!"
                                )

                        val embed = ApplicationService.getCarryDifficultyEmbed(carryDifficulty)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyEditArguments) {
                name = "edit"
                description = "Edit a carry difficulty"

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier = CarryTierConnection[carryType].getByIdentifier(arguments.carryTier)
                            ?: throw InvalidOptionException("carry-tier", "That carry tier doesn't exist")

                        val carryDifficulty =
                            CarryDifficultyConnection[carryTier].getByIdentifier(arguments.carryDifficulty)
                                ?: throw InvalidOptionException(
                                    "carry.difficulty",
                                    "That carry difficulty doesn't exist"
                                )

                        if (arguments.displayName == null && arguments.price == null && arguments.score == null && arguments.bulkAmount == null && arguments.bulkPrice == null && arguments.thumbnailUrl == null && arguments.priceName == null) {
                            throw CommandExecutionWarning("Please provide something you want to edit.")
                        }

                        val updateModel = carryDifficulty.getUpdateModel()

                        if (arguments.displayName != null) {
                            updateModel.displayName = arguments.displayName
                        }

                        if (arguments.price != null) {
                            updateModel.price = arguments.price
                        }

                        if (arguments.score != null) {
                            updateModel.score = arguments.score
                        }

                        if (arguments.bulkAmount != null) {
                            updateModel.bulkAmount = arguments.bulkAmount
                        }

                        if (arguments.bulkPrice != null) {
                            updateModel.bulkPrice = arguments.bulkPrice
                        }

                        if (arguments.thumbnailUrl != null) {
                            updateModel.thumbnailUrl = arguments.thumbnailUrl
                        }

                        if (arguments.priceName != null) {
                            updateModel.priceName = arguments.priceName
                        }

                        val updatedCarryDifficulty = CarryDifficultyConnection[carryTier]
                            .updateCarryDifficulty(carryDifficulty.id, updateModel)
                            ?: throw CommandExecutionWarning("Couldn't update carry difficulty.")

                        val embed = ApplicationService.getCarryDifficultyEmbed(updatedCarryDifficulty)
                        embed.title = "Updated Carry Difficulty"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyResetArguments) {
                name = "reset"
                description = "Reset a carry difficulty"

                //TODO implement
                action {
                    throw InvalidSubCommandException()
                }
            }
        }
    }

    inner class CarryDifficultyCreateArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier"
            description = "The identifier of the carry tier"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val identifier by string {
            name = "identifier"
            description = "The identifier of the carry difficulty"
        }

        val displayName by string {
            name = "display-name"
            description = "The display name of the carry difficulty"
            maxLength = 30
        }

        //TODO add optional arguments
    }

    inner class CarryDifficultyArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier"
            description = "The identifier of the carry tier"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = "carry-difficulty"
            description = "The identifier of the carry difficulty"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }
    }

    inner class CarryDifficultyEditArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier"
            description = "The identifier of the carry tier"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = "carry-difficulty"
            description = "The identifier of the carry difficulty"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        val displayName by optionalString {
            name = "display-name"
            description = "Set the display name of the carry difficulty"
        }

        val price by optionalInt {
            name = "price"
            description = "Set the price per carry"
            minValue = 0
        }

        val score by optionalInt {
            name = "score"
            description = "Set the score gained per carry"
            minValue = 0
            maxValue = 500
        }

        val bulkAmount by optionalInt {
            name = "bulk-amount"
            description = "Set the amount after which the carries use the bulk price."
            minValue = 1
            maxValue = 500
        }

        val bulkPrice by optionalInt {
            name = "bulk-price"
            description = "Set price for bulk carries. Needs to have bulk-price set to be used."
            minValue = 1
        }

        val thumbnailUrl by optionalString {
            name = "thumbnail-url"
            description = "Set the url for the thumbnail. This only acts as an override for the carry tier."
        }

        val priceName by optionalString {
            name = "price-name"
            description = "Set this if this carry difficulty should have a different name in the price embed."
        }
    }

    inner class CarryDifficultyResetArguments : Arguments() {
        val carryType by string {
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier"
            description = "The identifier of the carry tier"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = "carry-difficulty"
            description = "The identifier of the carry difficulty"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        //TODO add options to reset command
    }
}