package net.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.json.JsonErrorCode
import dev.kord.rest.request.KtorRequestException
import dev.kordex.core.utils.scheduling.Scheduler
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.OnStart
import net.dungeonhub.application.loader.StartupListener
import net.dungeonhub.connection.CarryDifficultyConnection
import net.dungeonhub.connection.CarryTierConnection
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.model.carry_difficulty.CarryDifficultyModel
import net.dungeonhub.model.carry_tier.CarryTierModel
import org.slf4j.LoggerFactory
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

@OnStart
object MessagesService : StartupListener {
    private val logger = LoggerFactory.getLogger(MessagesService::class.java)
    private const val REFRESH_SECONDS = 60L * 15
    private lateinit var scheduler: Scheduler

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

    suspend fun refreshPriceMessages(serverId: Long) {
        refreshPriceMessages(
            (DiscordServerConnection.authenticated().getAllCarryTiers(serverId) ?: listOf()).stream()
        )
    }

    private suspend fun refreshPriceMessages() {
        val guilds = DiscordConnection.bot?.kordRef?.guilds?.toList() ?: return

        for (serverData in guilds) {
            refreshPriceMessages(serverData.id.value.toLong())
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

        carryTiersPerChannel.forEach { (key: Long?, value: MutableList<CarryTierModel>) ->
            try {
                DiscordConnection.bot?.kordRef?.getChannelOf<GuildMessageChannel>(Snowflake(key!!))?.let {
                    refreshPriceMessageInChannel(
                        it,
                        addPriceFooterToLast(
                            value.mapNotNull { carryTier -> getPriceEmbed(carryTier) }
                        )
                    )
                }
            } catch (requestException: KtorRequestException) {
                // In case we don't have access to the channel anymore, we need to remove it from the carry tier.
                if (requestException.error?.code == JsonErrorCode.MissingAccess) {
                    for (carryTier in value) {
                        logger.error("I can't access the channel <#${carryTier.priceChannel}> anymore. Removing it from the carry tier.")

                        val updateModel = carryTier.getUpdateModel()
                        updateModel.priceChannel = null

                        CarryTierConnection[carryTier.carryType].authenticated().updateCarryTier(
                            carryTier.id,
                            updateModel
                        )
                    }
                }
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

        val message = textChannel.messages.firstOrNull { it.author?.isSelf == true }

        if (message == null) {
            textChannel.createMessage { this@createMessage.embeds = embeds.toMutableList() }
        } else {
            message.edit { this@edit.embeds = embeds.toMutableList() }
        }
    }

    override suspend fun postStart() {
        if (::scheduler.isInitialized) {
            scheduler.cancel("Application was restarted.")
        }

        scheduler = Scheduler()

        val task =
            scheduler.schedule(REFRESH_SECONDS, startNow = false, name = "Price-Messages-Schedule", repeat = true) {
                refreshPriceMessages()
            }

        scheduler.launch {
            delay(15.seconds)
            task.callNow()
            task.start()
        }
    }
}