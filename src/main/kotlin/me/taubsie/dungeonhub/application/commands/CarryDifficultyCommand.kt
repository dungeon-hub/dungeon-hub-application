package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getLocale
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.i18n.Translations.Command.CarryDifficulty
import net.dungeonhub.i18n.Translations.CommonArguments
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
            name = CarryDifficulty.name
            description = CarryDifficulty.description
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryDifficultyCreateArguments) {
                name = CarryDifficulty.Create.name
                description = CarryDifficulty.Create.description

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
                        embed.title = CarryDifficulty.Create.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyArguments) {
                name = CarryDifficulty.Delete.name
                description = CarryDifficulty.Delete.description

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
                        embed.title = CarryDifficulty.Delete.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyArguments) {
                name = CarryDifficulty.Get.name
                description = CarryDifficulty.Get.description

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
                name = CarryDifficulty.Edit.name
                description = CarryDifficulty.Edit.description

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
                                    "carry-difficulty",
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
                        embed.title = CarryDifficulty.Edit.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryDifficultyResetArguments) {
                name = CarryDifficulty.Reset.name
                description = CarryDifficulty.Reset.description

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
                                    "carry-difficulty",
                                    "That carry difficulty doesn't exist"
                                )

                        if (!arguments.thumbnailUrl && !arguments.bulkAmount && !arguments.bulkPrice && !arguments.priceName) {
                            throw CommandExecutionWarning("Please provide something you want to reset.")
                        }

                        val updateModel = carryDifficulty.getUpdateModel()

                        if (arguments.thumbnailUrl) {
                            updateModel.thumbnailUrl = null
                        }

                        if (arguments.bulkAmount) {
                            updateModel.bulkAmount = null
                        }

                        if (arguments.bulkPrice) {
                            updateModel.bulkPrice = null
                        }

                        if (arguments.priceName) {
                            updateModel.priceName = null
                        }

                        val updatedCarryDifficulty =
                            CarryDifficultyConnection[carryTier].updateCarryDifficulty(carryTier.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry difficulty.")

                        val embed = ApplicationService.getCarryDifficultyEmbed(updatedCarryDifficulty)
                        embed.title = CarryDifficulty.Reset.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    inner class CarryDifficultyCreateArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = CommonArguments.CarryTier.name
            description = CommonArguments.CarryTier.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val identifier by string {
            name = CommonArguments.identifier
            description = CommonArguments.CarryDifficulty.description
        }

        val displayName by string {
            name = CommonArguments.displayName
            description = CarryDifficulty.Create.Arguments.DisplayName.description
            maxLength = 30
        }

        val price by int {
            name = CarryDifficulty.Create.Arguments.Price.name
            description = CarryDifficulty.Create.Arguments.Price.description
            minValue = 0
        }

        val score by int {
            name = CarryDifficulty.Create.Arguments.Score.name
            description = CarryDifficulty.Create.Arguments.Score.description
            minValue = 0
        }
    }

    inner class CarryDifficultyArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = CommonArguments.CarryTier.name
            description = CommonArguments.CarryTier.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = CommonArguments.CarryDifficulty.name
            description = CommonArguments.CarryDifficulty.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }
    }

    inner class CarryDifficultyEditArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = CommonArguments.CarryTier.name
            description = CommonArguments.CarryTier.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = CommonArguments.CarryDifficulty.name
            description = CommonArguments.CarryDifficulty.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        val displayName by optionalString {
            name = CommonArguments.displayName
            description = CarryDifficulty.Edit.Arguments.DisplayName.description
        }

        val price by optionalInt {
            name = CarryDifficulty.Edit.Arguments.Price.name
            description = CarryDifficulty.Edit.Arguments.Price.description
            minValue = 0
        }

        val score by optionalInt {
            name = CarryDifficulty.Edit.Arguments.Score.name
            description = CarryDifficulty.Edit.Arguments.Score.description
            minValue = 0
            maxValue = 500
        }

        val bulkAmount by optionalInt {
            name = CarryDifficulty.Edit.Arguments.BulkAmount.name
            description = CarryDifficulty.Edit.Arguments.BulkAmount.description
            minValue = 1
            maxValue = 500
        }

        val bulkPrice by optionalInt {
            name = CarryDifficulty.Edit.Arguments.BulkPrice.name
            description = CarryDifficulty.Edit.Arguments.BulkPrice.description
            minValue = 1
        }

        val thumbnailUrl by optionalString {
            name = CarryDifficulty.Edit.Arguments.ThumbnailUrl.name
            description = CarryDifficulty.Edit.Arguments.ThumbnailUrl.description
        }

        val priceName by optionalString {
            name = CarryDifficulty.Edit.Arguments.PriceName.name
            description = CarryDifficulty.Edit.Arguments.PriceName.description
        }
    }

    inner class CarryDifficultyResetArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = CommonArguments.CarryTier.name
            description = CommonArguments.CarryTier.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = CommonArguments.CarryDifficulty.name
            description = CommonArguments.CarryDifficulty.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        val thumbnailUrl by boolean {
            name = CarryDifficulty.Reset.Arguments.ThumbnailUrl.name
            description = CarryDifficulty.Reset.Arguments.ThumbnailUrl.description
        }

        val bulkPrice by boolean {
            name = CarryDifficulty.Reset.Arguments.BulkPrice.name
            description = CarryDifficulty.Reset.Arguments.BulkPrice.description
        }

        val bulkAmount by boolean {
            name = CarryDifficulty.Reset.Arguments.BulkAmount.name
            description = CarryDifficulty.Reset.Arguments.BulkAmount.description
        }

        val priceName by boolean {
            name = CarryDifficulty.Reset.Arguments.PriceName.name
            description = CarryDifficulty.Reset.Arguments.PriceName.description
        }
    }
}