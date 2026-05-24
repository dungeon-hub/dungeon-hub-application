package net.dungeonhub.application.commands

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.boolean
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getLocale
import net.dungeonhub.application.exceptions.CommandExecutionWarning
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.i18n.Translations.Command.CarryTier
import net.dungeonhub.i18n.Translations.CommonArguments
import net.dungeonhub.model.carry_tier.CarryTierCreationModel
import java.util.*

/**
 * This extension provides the slash commands for the carry tier management.
 * It allows the creation, deletion, editing and resetting of carry tiers.
 * The commands are only available in guilds and require the administrator permission.
 * The commands are:
 * - `/carry-tier create`
 * - `/carry-tier delete`
 * - `/carry-tier get`
 * - `/carry-tier edit`
 * - `/carry-tier reset`
 *
 * These commands are used to create, delete, get, edit and reset carry tiers.
 */
@LoadExtension
class CarryTierCommand : Extension() {
    override val name = "carry-tier-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = CarryTier.name
            description = CarryTier.description
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryTierCreateArguments) {
                name = CarryTier.Create.name
                description = CarryTier.Create.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")

                        val identifier: String = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryTierConnection[carryType].authenticated().getByIdentifier(identifier) != null) {
                            throw InvalidOptionException("identifier", "That carry tier already exists!")
                        }

                        if (arguments.category != null && DiscordServerConnection.authenticated()
                                .getCarryTierFromCategory(
                                    guild!!.id.value.toLong(),
                                    arguments.category!!.id.value.toLong()
                                ) != null
                        ) {
                            throw InvalidOptionException(
                                "category",
                                "That category is already assigned to another carry tier!"
                            )
                        }

                        val creationModel = CarryTierCreationModel(
                            identifier = identifier,
                            displayName = arguments.displayName,
                            category = arguments.category?.id?.value?.toLong(),
                            descriptiveName = arguments.descriptiveName,
                            thumbnailUrl = arguments.thumbnailUrl,
                            priceTitle = arguments.priceTitle,
                            priceDescription = arguments.priceDescription
                        )

                        val carryTier = CarryTierConnection[carryType].authenticated().createCarryTier(creationModel)
                            ?: throw CommandExecutionWarning("Couldn't add that carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(carryTier)
                        embed.title = CarryTier.Create.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierArguments) {
                name = CarryTier.Delete.name
                description = CarryTier.Delete.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier =
                            CarryTierConnection[carryType].authenticated().getByIdentifier(arguments.carryTier)
                                ?: throw InvalidOptionException("carry-tier")

                        if (carryTier.carryType != carryType) {
                            throw CommandExecutionWarning("Well this is weird.. Something doesn't really add up!")
                        }

                        val deletedCarryTier =
                            CarryTierConnection[carryType].authenticated().deleteCarryTier(carryTier.id)
                                ?: throw CommandExecutionWarning("Couldn't delete the carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(deletedCarryTier)
                        embed.title = CarryTier.Delete.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierArguments) {
                name = CarryTier.Get.name
                description = CarryTier.Get.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("Carry type not found.")

                        val carryTier =
                            CarryTierConnection[carryType].authenticated().getByIdentifier(arguments.carryTier)
                                ?: throw InvalidOptionException("carry-tier", "That carry tier doesn't exist!")

                        val embed = ApplicationService.getCarryTierEmbed(carryTier)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierEditArguments) {
                name = CarryTier.Edit.name
                description = CarryTier.Edit.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier =
                            CarryTierConnection[carryType].authenticated().getByIdentifier(arguments.carryTier)
                                ?: throw InvalidOptionException("carry-tier", "That carry tier doesn't exist")

                        if (arguments.displayName == null && arguments.category == null && arguments.descriptiveName == null && arguments.thumbnailUrl == null && arguments.priceTitle == null) {
                            throw CommandExecutionWarning("Please provide something you want to edit.")
                        }

                        if (arguments.category != null) {
                            val categoryCarryTier = DiscordServerConnection.authenticated().getCarryTierFromCategory(
                                guild!!.id.value.toLong(),
                                arguments.category!!.id.value.toLong()
                            )

                            if (categoryCarryTier != null && categoryCarryTier != carryTier) {
                                val embed = ApplicationService.getErrorEmbed(
                                    ApplicationService.getCarryTierEmbed(categoryCarryTier)
                                )
                                embed.title = "Carry Tier for that category is already present!"
                                embeds = mutableListOf(embed)
                                return@respond
                            }
                        }

                        val updateModel = carryTier.getUpdateModel()

                        if (arguments.displayName != null) {
                            updateModel.displayName = arguments.displayName
                        }

                        if (arguments.category != null) {
                            updateModel.category = arguments.category?.id?.value?.toLong()
                        }

                        if (arguments.descriptiveName != null) {
                            updateModel.descriptiveName = arguments.descriptiveName
                        }

                        if (arguments.thumbnailUrl != null) {
                            updateModel.thumbnailUrl = arguments.thumbnailUrl
                        }

                        if (arguments.priceTitle != null) {
                            updateModel.priceTitle = arguments.priceTitle
                        }

                        val updatedCarryTier =
                            CarryTierConnection[carryType].authenticated().updateCarryTier(carryTier.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(updatedCarryTier)
                        embed.title = CarryTier.Edit.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierResetArguments) {
                name = CarryTier.Reset.name
                description = CarryTier.Reset.description

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                                .getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier =
                            CarryTierConnection[carryType].authenticated().getByIdentifier(arguments.carryTier)
                                ?: throw InvalidOptionException("carry-tier", "Carry tier doesn't exist")

                        if (!arguments.category && !arguments.priceChannel && !arguments.descriptiveName && !arguments.thumbnailUrl && !arguments.priceTitle) {
                            throw CommandExecutionWarning("Please provide something you want to reset.")
                        }

                        val updateModel = carryTier.getUpdateModel()

                        if (arguments.category) {
                            updateModel.category = null
                        }

                        if (arguments.descriptiveName) {
                            updateModel.descriptiveName = null
                        }

                        if (arguments.thumbnailUrl) {
                            updateModel.thumbnailUrl = null
                        }

                        if (arguments.priceTitle) {
                            updateModel.priceTitle = null
                        }

                        val updatedCarryTier =
                            CarryTierConnection[carryType].authenticated().updateCarryTier(carryTier.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(updatedCarryTier)
                        embed.title = CarryTier.Reset.Response.title.translateLocale(event.getLocale())
                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    class CarryTierCreateArguments : Arguments() {
        val carryType by string {
            name = CommonArguments.CarryType.name
            description = CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val identifier by string {
            name = CommonArguments.identifier
            description = CommonArguments.CarryTier.description
        }

        val displayName by string {
            name = CommonArguments.displayName
            description = CarryTier.Create.Arguments.DisplayName.description
            maxLength = 30
        }

        val descriptiveName by optionalString {
            name = CarryTier.Create.Arguments.DescriptiveName.name
            description = CarryTier.Create.Arguments.DescriptiveName.description
        }

        val category by optionalChannel {
            name = CarryTier.Create.Arguments.Category.name
            description = CarryTier.Create.Arguments.Category.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildCategory)
        }

        val priceDescription by optionalString {
            name = CarryTier.Create.Arguments.PriceDescription.name
            description = CarryTier.Create.Arguments.PriceDescription.description
        }

        val thumbnailUrl by optionalString {
            name = CarryTier.Create.Arguments.ThumbnailUrl.name
            description = CarryTier.Create.Arguments.ThumbnailUrl.description
        }

        val priceTitle by optionalString {
            name = CarryTier.Create.Arguments.PriceTitle.name
            description = CarryTier.Create.Arguments.PriceTitle.description
        }
    }

    class CarryTierArguments : Arguments() {
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
    }

    class CarryTierEditArguments : Arguments() {
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

        val displayName by optionalString {
            name = CommonArguments.displayName
            description = CarryTier.Edit.Arguments.DisplayName.description
        }

        val descriptiveName by optionalString {
            name = CarryTier.Edit.Arguments.DescriptiveName.name
            description = CarryTier.Edit.Arguments.DescriptiveName.description
        }

        val category by optionalChannel {
            name = CarryTier.Edit.Arguments.Category.name
            description = CarryTier.Edit.Arguments.Category.description
            requiredChannelTypes = mutableSetOf(ChannelType.GuildCategory)
        }

        val thumbnailUrl by optionalString {
            name = CarryTier.Edit.Arguments.ThumbnailUrl.name
            description = CarryTier.Edit.Arguments.ThumbnailUrl.description
        }

        val priceTitle by optionalString {
            name = CarryTier.Edit.Arguments.PriceTitle.name
            description = CarryTier.Edit.Arguments.PriceTitle.description
        }
    }

    class CarryTierResetArguments : Arguments() {
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

        val descriptiveName by boolean {
            name = CarryTier.Reset.Arguments.DescriptiveName.name
            description = CarryTier.Reset.Arguments.DescriptiveName.description
        }

        val category by boolean {
            name = CarryTier.Reset.Arguments.Category.name
            description = CarryTier.Reset.Arguments.Category.description
        }

        val priceChannel by boolean {
            name = CarryTier.Reset.Arguments.PriceChannel.name
            description = CarryTier.Reset.Arguments.PriceChannel.description
        }

        val thumbnailUrl by boolean {
            name = CarryTier.Reset.Arguments.ThumbnailUrl.name
            description = CarryTier.Reset.Arguments.ThumbnailUrl.description
        }

        val priceTitle by boolean {
            name = CarryTier.Reset.Arguments.PriceTitle.name
            description = CarryTier.Reset.Arguments.PriceTitle.description
        }
    }
}