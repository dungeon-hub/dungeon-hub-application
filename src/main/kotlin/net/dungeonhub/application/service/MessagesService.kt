package net.dungeonhub.application.service

import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.flow.firstOrNull
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel
import net.dungeonhub.model.carry_tier.CarryTierModel
import java.util.stream.Collectors

// TODO either: delete this, or add some other embeds from e.g. StaticMessageService into here
object MessagesService {
    fun getPriceEmbed(carryTier: CarryTierModel): EmbedBuilder? {
        val carryDifficulties = CarryDifficultyConnection[carryTier].authenticated().allCarryDifficulties ?: listOf()

        if (carryDifficulties.isEmpty()) {
            return null
        }

        val title = "## " + carryTier.priceTitle + "\n"
        val priceDescription = carryTier.priceDescription

        val description =
            title + (priceDescription?.let { s: String -> s + "\n\n" } ?: "") + carryDifficulties.stream()
                .map { carryDifficulty: CarryDifficultyModel ->
                    val result = StringBuilder()
                    if (carryDifficulty.bulkAmount != null && carryDifficulty.bulkPrice != null) {
                        result.append("\n")
                    }

                    result.append("**")
                        .append(carryDifficulty.priceName)
                        .append("**: ")

                    val priceText = if (carryDifficulty.price != 0
                    ) ApplicationService.makeNumberReadable(carryDifficulty.price.toLong()) + " coins"
                    else "Free"

                    result.append(priceText)

                    if (carryDifficulty.bulkAmount != null && carryDifficulty.bulkPrice != null) {
                        result.append("\n\\*")
                            .append(ApplicationService.makeNumberReadable(carryDifficulty.bulkPrice!!.toLong()))
                            .append(" per carry if you buy ")
                            .append(carryDifficulty.bulkAmount)
                            .append("+ carries.")
                    }
                    result
                }.collect(Collectors.joining("\n"))

        val embed = ApplicationService.embedWithoutTimestamp
        embed.color = EmbedColor.Default.color
        embed.description = description

        carryTier.thumbnailUrl?.let { embed.thumbnail { this.url = it } }

        return embed
    }

    private fun addPriceFooterToLast(embeds: List<EmbedBuilder>): List<EmbedBuilder> {
        for (embed in embeds) {
            embed.footer { text = "" }
        }

        if (embeds.isNotEmpty()) {
            embeds[embeds.size - 1].footer { text = ApplicationService.priceFooter }
        }

        return embeds
    }

    private suspend fun refreshPriceMessageInChannel(
        textChannel: GuildMessageChannel,
        embeds: List<EmbedBuilder>
    ) {
        if (embeds.isEmpty()) {
            return
        }

        val message = textChannel.messages.firstOrNull { it.author?.isSelf == true }

        if (message == null) {
            textChannel.createMessage { this@createMessage.embeds = embeds.toMutableList() }
        } else {
            message.edit { this@edit.embeds = embeds.toMutableList() }
        }
    }
}