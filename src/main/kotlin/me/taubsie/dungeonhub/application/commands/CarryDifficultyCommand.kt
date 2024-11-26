package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.model.carry_difficulty.CarryDifficultyCreationModel
import java.util.*

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
            name = "carry-difficulty".toKey()
            description = "Set up the carry difficulties for this server.".toKey()
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryDifficultyCreateArguments) {
                name = "create".toKey()
                description = "Create a new carry difficulty".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier = CarryTierConnection[carryType].getByIdentifier(arguments.carryTier)
                            ?: throw CommandExecutionWarning("That carry tier doesn't exists!")

                        val identifier = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryDifficultyConnection[carryTier].getByIdentifier(identifier) != null) {
                            throw InvalidOptionException("identifier", "That carry difficulty already exists!")
                        }

                        val creationModel = CarryDifficultyCreationModel(
                            identifier = identifier,
                            displayName = arguments.displayName,
                            price = arguments.price,
                            score = arguments.score,
                            bulkAmount = null,
                            bulkPrice = null,
                            thumbnailUrl = null,
                            priceName = null
                        )

                        val carryDifficulty = CarryDifficultyConnection[carryTier].createCarryDifficulty(creationModel)
                            ?: throw CommandExecutionWarning("Couldn't create carry difficulty.")

                        val embed = ApplicationService.getCarryDifficultyEmbed(carryDifficulty)
                        embed.title = "Carry Difficulty created"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyArguments) {
                name = "delete".toKey()
                description = "Delete a carry difficulty".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier = CarryTierConnection[carryType].getByIdentifier(arguments.carryTier)
                            ?: throw InvalidOptionException("carry-tier")

                        if (carryTier.carryType != carryType) {
                            throw CommandExecutionWarning("Well this is weird.. Something doesn't really add up!")
                        }

                        val carryDifficulty =
                            CarryDifficultyConnection[carryTier].getByIdentifier(arguments.carryDifficulty)
                                ?: throw InvalidOptionException("carry-difficulty")

                        if (carryDifficulty.carryTier != carryTier) {
                            throw CommandExecutionWarning("Well this is also weird.. Something doesn't really add up!")
                        }

                        val deletedCarryDifficulty =
                            CarryDifficultyConnection[carryTier].deleteCarryDifficulty(carryDifficulty.id)
                                ?: throw CommandExecutionWarning("Couldn't delete the carry difficulty.")

                        val embed = ApplicationService.getCarryDifficultyEmbed(deletedCarryDifficulty)
                        embed.title = "Deleted Carry Difficulty"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyArguments) {
                name = "get".toKey()
                description = "Get information about a carry difficulty".toKey()

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
                name = "edit".toKey()
                description = "Edit a carry difficulty".toKey()

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
                name = "reset".toKey()
                description = "Reset a carry difficulty".toKey()

                //TODO implement
                action {
                    throw InvalidSubCommandException()
                }
            }
        }
    }

    inner class CarryDifficultyCreateArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier".toKey()
            description = "The identifier of the carry tier".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val identifier by string {
            name = "identifier".toKey()
            description = "The identifier of the carry difficulty".toKey()
        }

        val displayName by string {
            name = "display-name".toKey()
            description = "The display name of the carry difficulty".toKey()
            maxLength = 30
        }

        val price by int {
            name = "price".toKey()
            description = "The price per carry".toKey()
            minValue = 0
        }

        val score by int {
            name = "score".toKey()
            description = "The score gained per carry".toKey()
            minValue = 0
        }
    }

    inner class CarryDifficultyArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier".toKey()
            description = "The identifier of the carry tier".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = "carry-difficulty".toKey()
            description = "The identifier of the carry difficulty".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }
    }

    inner class CarryDifficultyEditArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier".toKey()
            description = "The identifier of the carry tier".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = "carry-difficulty".toKey()
            description = "The identifier of the carry difficulty".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        val displayName by optionalString {
            name = "display-name".toKey()
            description = "Set the display name of the carry difficulty".toKey()
        }

        val price by optionalInt {
            name = "price".toKey()
            description = "Set the price per carry".toKey()
            minValue = 0
        }

        val score by optionalInt {
            name = "score".toKey()
            description = "Set the score gained per carry".toKey()
            minValue = 0
            maxValue = 500
        }

        val bulkAmount by optionalInt {
            name = "bulk-amount".toKey()
            description = "Set the amount after which the carries use the bulk price.".toKey()
            minValue = 1
            maxValue = 500
        }

        val bulkPrice by optionalInt {
            name = "bulk-price".toKey()
            description = "Set price for bulk carries. Needs to have bulk-price set to be used.".toKey()
            minValue = 1
        }

        val thumbnailUrl by optionalString {
            name = "thumbnail-url".toKey()
            description = "Set the url for the thumbnail. This only acts as an override for the carry tier.".toKey()
        }

        val priceName by optionalString {
            name = "price-name".toKey()
            description = "Set this if this carry difficulty should have a different name in the price embed.".toKey()
        }
    }

    inner class CarryDifficultyResetArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = "carry-tier".toKey()
            description = "The identifier of the carry tier".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = "carry-difficulty".toKey()
            description = "The identifier of the carry difficulty".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        //TODO add options to reset command
    }
}