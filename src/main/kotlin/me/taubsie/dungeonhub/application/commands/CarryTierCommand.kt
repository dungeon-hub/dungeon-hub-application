package me.taubsie.dungeonhub.application.commands

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
import dev.kordex.core.i18n.toKey
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionWarning
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
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
            name = "carry-tier".toKey()
            description = "Set up the carry tiers for this server.".toKey()
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryTierCreateArguments) {
                name = "create".toKey()
                description = "Create a new carry tier".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")

                        val identifier: String = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryTierConnection[carryType].getByIdentifier(identifier) != null) {
                            throw InvalidOptionException("identifier", "That carry tier already exists!")
                        }

                        if (arguments.category != null && DiscordServerConnection.getCarryTierFromCategory(
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
                            category = arguments.category!!.id.value.toLong(),
                            priceChannel = arguments.priceChannel!!.id.value.toLong(),
                            descriptiveName = arguments.descriptiveName,
                            thumbnailUrl = arguments.thumbnailUrl,
                            priceTitle = arguments.priceTitle,
                            priceDescription = arguments.priceDescription
                        )

                        val carryTier = CarryTierConnection[carryType].createCarryTier(creationModel)
                            ?: throw CommandExecutionWarning("Couldn't add that carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(carryTier)
                        embed.title = "Carry Tier created"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierArguments) {
                name = "delete".toKey()
                description = "Delete a carry tier".toKey()

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

                        val deletedCarryTier = CarryTierConnection[carryType].deleteCarryTier(carryTier.id)
                            ?: throw CommandExecutionWarning("Couldn't delete the carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(deletedCarryTier)
                        embed.title = "Deleted Carry Tier"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierArguments) {
                name = "get".toKey()
                description = "Get information about a carry tier".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("Carry type not found.")

                        val carryTier = CarryTierConnection[carryType].getByIdentifier(arguments.carryTier)
                            ?: throw InvalidOptionException("carry-tier", "That carry tier doesn't exist!")

                        val embed = ApplicationService.getCarryTierEmbed(carryTier)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierEditArguments) {
                name = "edit".toKey()
                description = "Edit a carry tier".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier = CarryTierConnection[carryType].getByIdentifier(arguments.carryTier)
                            ?: throw InvalidOptionException("carry-tier", "That carry tier doesn't exist")

                        if (arguments.displayName == null && arguments.category == null && arguments.priceChannel == null && arguments.descriptiveName == null && arguments.thumbnailUrl == null && arguments.priceTitle == null) {
                            throw CommandExecutionWarning("Please provide something you want to edit.")
                        }

                        if (arguments.category != null) {
                            val categoryCarryTier = DiscordServerConnection.getCarryTierFromCategory(
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
                            updateModel.category = arguments.category!!.id.value.toLong()
                        }

                        if (arguments.priceChannel != null) {
                            updateModel.priceChannel = arguments.priceChannel!!.id.value.toLong()
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

                        val updatedCarryTier = CarryTierConnection[carryType].updateCarryTier(carryTier.id, updateModel)
                            ?: throw CommandExecutionWarning("Couldn't update carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(updatedCarryTier)
                        embed.title = "Updated Carry Tier"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierResetArguments) {
                name = "reset".toKey()
                description = "Reset a carry tier".toKey()

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection[guild!!.id.value.toLong()].getByIdentifier(arguments.carryType)
                                ?: throw CommandExecutionWarning("That carry type doesn't exists!")

                        val carryTier = CarryTierConnection[carryType].getByIdentifier(arguments.carryTier)
                            ?: throw InvalidOptionException("carry-tier", "Carry tier doesn't exist")

                        if (!arguments.category && !arguments.priceChannel && !arguments.descriptiveName && !arguments.thumbnailUrl && !arguments.priceTitle) {
                            throw CommandExecutionWarning("Please provide something you want to reset.")
                        }

                        val updateModel = carryTier.getUpdateModel()

                        if (arguments.category) {
                            updateModel.category = null
                        }

                        if (arguments.priceChannel) {
                            updateModel.priceChannel = null
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
                            CarryTierConnection[carryType].updateCarryTier(carryTier.id, updateModel)
                                ?: throw CommandExecutionWarning("Couldn't update carry tier.")

                        val embed = ApplicationService.getCarryTierEmbed(updatedCarryTier)
                        embed.title = "Updated Carry Tier with reset values"
                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    inner class CarryTierCreateArguments : Arguments() {
        val carryType by string {
            name = "carry-type".toKey()
            description = "The identifier of the carry type".toKey()
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val identifier by string {
            name = "identifier".toKey()
            description = "The identifier of the carry tier".toKey()
        }

        val displayName by string {
            name = "display-name".toKey()
            description = "The display name of the carry tier".toKey()
            maxLength = 30
        }

        val descriptiveName by optionalString {
            name = "descriptive-name".toKey()
            description = "Set the descriptive name which replaces the display name in some places".toKey()
        }

        val category by optionalChannel {
            name = "category".toKey()
            description = "Set the category of the tickets".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildCategory)
        }

        val priceChannel by optionalChannel {
            name = "price-channel".toKey()
            description = "Set the channel where the price list should appear".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val priceDescription by optionalString {
            name = "price-description".toKey()
            description = "Set the price description which is shown on the top of the price message.".toKey()
        }

        val thumbnailUrl by optionalString {
            name = "thumbnail-url".toKey()
            description = "Set the thumbnail which is used to make some embeds look nicer".toKey()
        }

        val priceTitle by optionalString {
            name = "price-title".toKey()
            description = "Set the title of the price embed".toKey()
        }
    }

    inner class CarryTierArguments : Arguments() {
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
    }

    inner class CarryTierEditArguments : Arguments() {
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

        val displayName by optionalString {
            name = "display-name".toKey()
            description = "Set the display name of the carry tier".toKey()
        }

        val descriptiveName by optionalString {
            name = "descriptive-name".toKey()
            description = "Set the descriptive name which replaces the display name in some places".toKey()
        }

        val category by optionalChannel {
            name = "category".toKey()
            description = "Set the category of the tickets".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildCategory)
        }

        val priceChannel by optionalChannel {
            name = "price-channel".toKey()
            description = "Set the channel where the price list should appear".toKey()
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val thumbnailUrl by optionalString {
            name = "thumbnail-url".toKey()
            description = "Set the thumbnail which is used to make some embeds look nicer".toKey()
        }

        val priceTitle by optionalString {
            name = "price-title".toKey()
            description = "Set the title of the price embed".toKey()
        }
    }

    inner class CarryTierResetArguments : Arguments() {
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

        val descriptiveName by boolean {
            name = "descriptive-name".toKey()
            description = "Reset the descriptive name which replaces the display name in some places".toKey()
        }

        val category by boolean {
            name = "category".toKey()
            description = "Reset the category of the tickets".toKey()
        }

        val priceChannel by boolean {
            name = "price-channel".toKey()
            description = "Reset the channel where the price list should appear".toKey()
        }

        val thumbnailUrl by boolean {
            name = "thumbnail-url".toKey()
            description = "Reset the thumbnail which is used to make some embeds look nicer".toKey()
        }

        val priceTitle by boolean {
            name = "price-title".toKey()
            description = "Reset the title of the price embed".toKey()
        }
    }
}