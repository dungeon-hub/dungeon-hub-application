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
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTierConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierCreationModel
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierUpdateModel
import java.util.*

@LoadExtension
class CarryTierCommand : Extension() {
    override val name = "carry-tier-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "carry-tier"
            description = "Set up the carry tiers for this server."
            defaultMemberPermissions = Permissions(Permission.Administrator)
            allowInDms = false

            publicSubCommand(::CarryTierCreateArguments) {
                name = "create"
                description = "Create a new carry tier"

                action {
                    respond {
                        val carryType =
                            CarryTypeConnection.getInstance(
                                guild!!.id.value.toLong()
                            )
                                .getByIdentifier(arguments.carryType)
                                .orElse(null)

                        if (carryType == null) {
                            throw InvalidOptionException("carry-type", "Carry Type couldn't be found.")
                        }

                        val identifier: String = arguments.identifier
                            .trim()
                            .lowercase(Locale.getDefault())
                            .replace(" ", "_")

                        if (CarryTierConnection.getInstance(
                                carryType
                            ).getByIdentifier(identifier).isPresent
                        ) {
                            throw InvalidOptionException("identifier", "That carry tier already exists!")
                        }

                        val creationModel = CarryTierCreationModel()
                        creationModel.identifier = identifier
                        creationModel.displayName = arguments.displayName

                        if (arguments.descriptiveName != null) {
                            creationModel.descriptiveName = arguments.descriptiveName
                        }

                        if (arguments.category != null && DiscordServerConnection.getInstance()
                                .getCarryTierFromCategory(
                                    guild!!.id.value.toLong(),
                                    arguments.category!!.id.value.toLong()
                                ).isEmpty
                        ) {
                            creationModel.category = arguments.category!!.id.value.toLong()
                        }

                        if (arguments.priceChannel != null) {
                            creationModel.priceChannel = arguments.priceChannel!!.id.value.toLong()
                        }

                        if (arguments.thumbnailUrl != null) {
                            creationModel.thumbnailUrl = arguments.thumbnailUrl
                        }

                        if (arguments.priceTitle != null) {
                            creationModel.priceTitle = arguments.priceTitle
                        }

                        if (arguments.priceDescription != null) {
                            creationModel.priceDescription = arguments.priceDescription
                        }

                        val carryTier =
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .createCarryTier(creationModel)
                                .orElse(null)

                        if (carryTier == null) {
                            //TODO custom class?
                            throw CommandExecutionException("Couldn't add that carry tier.")
                        }

                        val embed = ApplicationService.getCarryTierEmbed(carryTier)
                        embed.title = "Carry Tier created"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierArguments) {
                name = "delete"
                description = "Delete a carry tier"

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

                        val carryTier =
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .getByIdentifier(arguments.carryTier)
                                .orElse(null)

                        if (carryTier == null) {
                            throw InvalidOptionException("carry-tier")
                        }

                        if (carryTier.carryType != carryType) {
                            //TODO custom class
                            throw CommandExecutionException("Well this is weird.. Something doesn't really add up!")
                        }

                        val deletedCarryTier =
                            DungeonHubConnection.getInstance()
                                .removeCarryTier(carryTier)
                                .orElse(null)

                        if (deletedCarryTier == null) {
                            //TODO custom class
                            throw CommandExecutionException()
                        }

                        val embed = ApplicationService.getCarryTierEmbed(deletedCarryTier)
                        embed.title = "Deleted Carry Tier"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierArguments) {
                name = "get"
                description = "Get information about a carry tier"

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

                        val carryTier =
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .getByIdentifier(arguments.carryTier)
                                .orElse(null)

                        if (carryTier == null) {
                            throw InvalidOptionException("carry-tier", "That carry tier doesn't exist!")
                        }

                        val embed = ApplicationService.getCarryTierEmbed(carryTier)
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierEditArguments) {
                name = "edit"
                description = "Edit a carry tier"

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

                        val carryTier =
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .getByIdentifier(arguments.carryTier)
                                .orElse(null)

                        if (carryTier == null) {
                            throw InvalidOptionException("carry-tier", "That carry tier doesn't exist")
                        }

                        if (arguments.displayName == null && arguments.category == null && arguments.priceChannel == null && arguments.descriptiveName == null && arguments.thumbnailUrl == null && arguments.priceTitle == null) {
                            //TODO custom class
                            throw CommandExecutionException("Please provide something you want to edit.")
                        }

                        if (arguments.category != null) {
                            val categoryCarryTier =
                                DiscordServerConnection.getInstance()
                                    .getCarryTierFromCategory(
                                        guild!!.id.value.toLong(),
                                        arguments.category!!.id.value.toLong()
                                    )
                                    .orElse(null)

                            if (categoryCarryTier != null && categoryCarryTier != carryTier) {
                                val embed = ApplicationService.getErrorEmbed(
                                    ApplicationService.getCarryTierEmbed(categoryCarryTier)
                                )
                                embed.title = "Carry Tier for that category is already present!"
                                embeds = mutableListOf(embed)
                                return@respond
                            }
                        }

                        val updateModel = CarryTierUpdateModel.fromCarryTier(carryTier)

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

                        val updatedCarryTier =
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .updateCarryTier(carryTier.id, updateModel)
                                .orElse(null)

                        if (updatedCarryTier == null) {
                            //TODO custom class
                            throw CommandExecutionException("Couldn't update carry tier.")
                        }

                        val embed = ApplicationService.getCarryTierEmbed(updatedCarryTier)
                        embed.title = "Updated Carry Tier"
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::CarryTierResetArguments) {
                name = "reset"
                description = "Reset a carry tier"

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

                        val carryTier =
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .getByIdentifier(arguments.carryTier)
                                .orElse(null)

                        if (carryTier == null) {
                            throw InvalidOptionException("carry-tier", "Carry tier doesn't exist")
                        }

                        if (!arguments.category && !arguments.priceChannel && !arguments.descriptiveName && !arguments.thumbnailUrl && !arguments.priceTitle) {
                            //TODO custom class
                            throw CommandExecutionException("Please provide something you want to reset.")
                        }

                        val updateModel = CarryTierUpdateModel.fromCarryTier(carryTier)

                        if (arguments.category) {
                            updateModel.category = -1L
                        }

                        if (arguments.priceChannel) {
                            updateModel.priceChannel = -1L
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
                            CarryTierConnection.getInstance(
                                carryType
                            )
                                .updateCarryTier(carryTier.id, updateModel)
                                .orElse(null)

                        if (updatedCarryTier == null) {
                            //TODO custom class
                            throw CommandExecutionException("Couldn't update carry tier.")
                        }

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
            name = "carry-type"
            description = "The identifier of the carry type"
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val identifier by string {
            name = "identifier"
            description = "The identifier of the carry tier"
        }

        val displayName by string {
            name = "display-name"
            description = "The display name of the carry tier"
            maxLength = 30
        }

        val descriptiveName by optionalString {
            name = "descriptive-name"
            description = "Set the descriptive name which replaces the display name in some places"
        }

        val category by optionalChannel {
            name = "category"
            description = "Set the category of the tickets"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildCategory)
        }

        val priceChannel by optionalChannel {
            name = "price-channel"
            description = "Set the channel where the price list should appear"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val priceDescription by optionalString {
            name = "price-description"
            description = "Set the price description which is shown on the top of the price message."
        }

        val thumbnailUrl by optionalString {
            name = "thumbnail-url"
            description = "Set the thumbnail which is used to make some embeds look nicer"
        }

        val priceTitle by optionalString {
            name = "price-title"
            description = "Set the title of the price embed"
        }
    }

    inner class CarryTierArguments : Arguments() {
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
    }

    inner class CarryTierEditArguments : Arguments() {
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

        val displayName by optionalString {
            name = "display-name"
            description = "Set the display name of the carry tier"
        }

        val descriptiveName by optionalString {
            name = "descriptive-name"
            description = "Set the descriptive name which replaces the display name in some places"
        }

        val category by optionalChannel {
            name = "category"
            description = "Set the category of the tickets"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildCategory)
        }

        val priceChannel by optionalChannel {
            name = "price-channel"
            description = "Set the channel where the price list should appear"
            requiredChannelTypes = mutableSetOf(ChannelType.GuildText)
        }

        val thumbnailUrl by optionalString {
            name = "thumbnail-url"
            description = "Set the thumbnail which is used to make some embeds look nicer"
        }

        val priceTitle by optionalString {
            name = "price-title"
            description = "Set the title of the price embed"
        }
    }

    inner class CarryTierResetArguments : Arguments() {
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

        val descriptiveName by boolean {
            name = "descriptive-name"
            description = "Reset the descriptive name which replaces the display name in some places"
        }

        val category by boolean {
            name = "category"
            description = "Reset the category of the tickets"
        }

        val priceChannel by boolean {
            name = "price-channel"
            description = "Reset the channel where the price list should appear"
        }

        val thumbnailUrl by boolean {
            name = "thumbnail-url"
            description = "Reset the thumbnail which is used to make some embeds look nicer"
        }

        val priceTitle by boolean {
            name = "price-title"
            description = "Reset the title of the price embed"
        }
    }
}