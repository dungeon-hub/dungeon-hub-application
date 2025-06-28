package net.dungeonhub.application.commands

import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.long
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getLocale
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.InvalidOptionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.AutoCompletionService
import net.dungeonhub.auth.AuthenticationConnection
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.CarryTypeConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.i18n.Translations

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
            name = Translations.Command.CalcPrice.name
            description = Translations.Command.CalcPrice.description
            allowInDms = false

            action {
                respond {
                    var carryTier = channel.asChannelOfOrNull<CategorizableChannel>()
                        ?.categoryId
                        ?.let { id ->
                            DiscordServerConnection.authenticated(AuthenticationConnection).getCarryTierFromCategory(
                                guild!!.id.value.toLong(),
                                id.value.toLong()
                            )
                        }

                    val previousCarryTier = carryTier

                    carryTier = CarryTypeConnection[guild!!.id.value.toLong()].authenticated()
                        .getByIdentifier(arguments.carryType)
                        ?.let { carryType ->
                            CarryTierConnection[carryType].authenticated()
                                .getByIdentifier(arguments.carryTier)
                                ?: previousCarryTier
                        }

                    if (carryTier == null) {
                        throw InvalidOptionException(
                            "carry-tier", "Please either use this in a carry ticket or supply a " +
                                    "carry type and carry tier."
                        )
                    }

                    val carryDifficulty = CarryDifficultyConnection[carryTier].authenticated()
                        .getByIdentifier(arguments.carryDifficulty)
                        ?: throw InvalidOptionException("carry-difficulty")

                    val price = ApplicationService.calculatePrice(carryDifficulty, arguments.amount)
                    val pricePerCarry = ApplicationService.calculatePricePerCarry(carryDifficulty, arguments.amount)

                    if (price < 0) {
                        throw CommandExecutionException("Something went wrong.. The calculated price ($price) is negative?")
                    }

                    val priceText = if (price != 0L) "${ApplicationService.makeNumberReadable(price)} (${
                        ApplicationService.makeNumberReadable(pricePerCarry)
                    }) coins" else "Free"

                    val embed = ApplicationService.embed
                    embed.color = EmbedColor.Information.color
                    embed.title =
                        Translations.Command.CalcPrice.Response.title.withLocale(event.getLocale()).translate()
                    embed.field(
                        Translations.Command.CalcPrice.Response.Fields.type.translateLocale(event.getLocale()),
                        true
                    ) {
                        carryTier.displayName + " | " + carryDifficulty.displayName
                    }
                    embed.field(
                        Translations.Command.CalcPrice.Response.Fields.amount.translateLocale(event.getLocale()),
                        true
                    ) { arguments.amount.toString() }
                    embed.field(
                        Translations.Command.CalcPrice.Response.Fields.price.translateLocale(event.getLocale()),
                        true
                    ) { priceText }

                    carryDifficulty.thumbnailUrl?.let { embed.thumbnail { url = it } }

                    embeds = mutableListOf(embed)
                }
            }
        }
    }

    class CalcPriceArguments : Arguments() {
        val carryType by string {
            name = Translations.CommonArguments.CarryType.name
            description = Translations.CommonArguments.CarryType.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryType
        }

        val carryTier by string {
            name = Translations.CommonArguments.CarryTier.name
            description = Translations.CommonArguments.CarryTier.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryTier
        }

        val carryDifficulty by string {
            name = Translations.CommonArguments.CarryDifficulty.name
            description = Translations.CommonArguments.CarryDifficulty.description
            maxLength = 30
            autoCompleteCallback = AutoCompletionService.carryDifficulty
        }

        val amount by long {
            name = Translations.Command.CalcPrice.Arguments.Amount.name
            description = Translations.Command.CalcPrice.Arguments.Amount.description
            maxValue = 200
            minValue = 1
        }
    }
}