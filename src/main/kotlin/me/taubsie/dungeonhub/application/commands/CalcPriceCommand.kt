package me.taubsie.dungeonhub.application.commands

import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection

/**
 * Command to calculate the price for some amount of carries.
 * This command can be used in a carry ticket channel to automatically get the price for the current carry tier.
 * If used outside a carry ticket channel, the user has to supply the carry type and carry tier.
 * The command will then calculate the price for the given amount of carries.
 */
@LoadExtension
class CalcPriceCommand : Extension() {
    override val name = "calc-price-command"

    override suspend fun setup() {
        publicSlashCommand(::CalcPriceArguments) {
            name = "calc-price"
            description = "Calculate the price for some amount of carries."
            allowInDms = false

            action {
                respond {
                    var carryTier = channel.asChannelOfOrNull<CategorizableChannel>()
                        ?.categoryId
                        ?.let { id ->
                            DiscordServerConnection.getCarryTierFromCategory(
                                guild!!.id.value.toLong(),
                                id.value.toLong()
                            )
                        }

                    val previousCarryTier = carryTier

                    carryTier = CarryTypeConnection[guild!!.id.value.toLong()]
                        .getByIdentifier(arguments.carryType)
                        ?.let { carryType ->
                            CarryTierConnection[carryType]
                                .getByIdentifier(arguments.carryTier)
                                ?: previousCarryTier
                        }

                    if (carryTier == null) {
                        throw InvalidOptionException(
                            "carry-tier", "Please either use this in a carry ticket or supply a " +
                                    "carry type and carry tier."
                        )
                    }

                    val carryDifficulty = CarryDifficultyConnection[carryTier]
                        .getByIdentifier(arguments.carryDifficulty)
                        ?: throw InvalidOptionException("carry-difficulty")

                    val price = ApplicationService.calculatePrice(carryDifficulty, arguments.amount)
                    val pricePerCarry = ApplicationService.calculatePricePerCarry(carryDifficulty, arguments.amount)

                    if (price < 0) {
                        throw CommandExecutionException("Something went wrong.. The calculated price ($price) is negative?")
                    }

                    val priceText = if (price != 0L) "${ApplicationService.makeNumberReadable(price)} coins" else "Free"
                    val pricePerCarryText =
                        if (price != 0L) "${ApplicationService.makeNumberReadable(pricePerCarry)} coins" else "Free"

                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.Information.color
                    embed.title = "Carry-Price"
                    embed.field("Type", true) {
                        carryTier.displayName + " | " + carryDifficulty.displayName
                    }
                    embed.field("Amount", true) { arguments.amount.toString() }
                    embed.field("Price", true) { priceText }
                    embed.field("Price per Carry", true) { pricePerCarryText }

                    carryDifficulty.thumbnailUrl?.let { embed.thumbnail { url = it } }

                    embeds = mutableListOf(embed)
                }
            }
        }
    }

    inner class CalcPriceArguments : Arguments() {
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

        val amount by long {
            name = "amount"
            description = "The amount of carries you want."
            maxValue = 200
            minValue = 1
        }
    }
}