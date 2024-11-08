package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.isSelf
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartupListener
import me.taubsie.dungeonhub.application.service.ServerService.allServers
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel
import net.dungeonhub.model.carry_tier.CarryTierModel
import java.sql.Time
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

@OnStart
object MessagesService : StartupListener {
    private const val REFRESH_PERIOD = 1000L * 60 * 15

    fun getPriceEmbed(carryTier: CarryTierModel): EmbedBuilder? {
        val carryDifficulties = CarryDifficultyConnection[carryTier].allCarryDifficulties ?: listOf()

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

    suspend fun refreshPriceMessages(serverId: Long) {
        refreshPriceMessages(
            (DiscordServerConnection.getAllCarryTiers(serverId) ?: listOf()).stream()
        )
    }

    suspend fun refreshPriceMessages(server: Guild) {
        refreshPriceMessages(
            (DiscordServerConnection.getAllCarryTiers(server.id.value.toLong()) ?: listOf()).stream()
        )
    }

    private suspend fun refreshPriceMessages() {
        for (serverData in allServers) {
            refreshPriceMessages(serverData.id)
        }
    }

    private suspend fun refreshPriceMessages(carryTiers: Stream<CarryTierModel>) {
        val carryTiersPerChannel = carryTiers
            .filter { carryTier: CarryTierModel -> carryTier.priceChannel != null }
            .collect(
                Collectors.toMap(
                    { carryTier: CarryTierModel -> carryTier.priceChannel },
                    { carryTier: CarryTierModel ->
                        mutableListOf(
                            carryTier
                        )
                    },
                    { o: MutableList<CarryTierModel>, o2: List<CarryTierModel>? ->
                        o.addAll(
                            o2!!
                        )
                        o
                    }
                ))

        carryTiersPerChannel
            .forEach { (key: Long?, value: MutableList<CarryTierModel>) ->
                DiscordConnection.bot?.kordRef
                    ?.getChannelOf<GuildMessageChannel>(Snowflake(key!!))
                    ?.let {
                        refreshPriceMessageInChannel(
                            it,
                            addPriceFooterToLast(
                                value.stream()
                                    .map { carryTier -> getPriceEmbed(carryTier) }
                                    .filter { embed -> embed != null }
                                    .map { embed -> embed!! }
                                    .toList()
                            )
                        )
                    }
            }
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

        val message = textChannel.messages.firstOrNull { it.author?.isSelf() == true }

        if (message == null) {
            textChannel.createMessage { this@createMessage.embeds = embeds.toMutableList() }
        } else {
            message.edit { this@edit.embeds = embeds.toMutableList() }
        }
    }

    override suspend fun postStart() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runBlocking {
                    refreshPriceMessages()
                }
            }
        }, Time(System.currentTimeMillis() + 15000), REFRESH_PERIOD)
    }
}