package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.getChannelOfOrNull
import dev.kord.core.entity.Guild
import dev.kord.core.entity.channel.GuildChannel
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.core.supplier.RestEntitySupplier
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.getCarryAmountOrNull
import me.taubsie.dungeonhub.application.connection.dungeon_hub.getTotalAmountOfMoneySpent
import me.taubsie.dungeonhub.application.connection.getGuildOrNull
import me.taubsie.dungeonhub.application.loader.OnStart
import me.taubsie.dungeonhub.application.loader.StartupListener
import org.slf4j.LoggerFactory
import java.sql.Time
import java.time.ZonedDateTime
import java.util.*
import kotlin.concurrent.thread

@OnStart
object ServerStatsService : StartupListener {
    private val logger = LoggerFactory.getLogger(ServerStatsService::class.java)
    private var timer: Timer? = null
    private val serverStatChannels = listOf(
        //DH Testing
        1023684107877761196L to listOf(
            1273562134948876380L to "{member_count} members",
            1272308210102964254L to "{linked_users} linked Users",
            1272323673457557616L to "{spent_money} coins spent",
            1280463884003704874L to "{carry_count_monthly} carries last 30 days"
        ),
        //Dungeon Hub
        693263712626278553L to listOf(
            1273562347797090377L to "{member_count} members",
            1272331486154194984L to "{linked_users} linked Users",
            1272331662772142081L to "{spent_money} coins spent",
            1280470213745184810L to "{carry_count_monthly} carries last 30 days"
        )
    )

    override suspend fun postStart() {
        resetTimer()
    }


    private fun resetTimer() {
        if (timer != null) {
            timer!!.cancel()
        }

        timer = Timer()

        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                logger.debug("Server stat channels reloaded!")

                thread {
                    runBlocking {
                        launch {
                            loadServerStatChannels()
                        }
                    }
                }
            }
        }, Time(System.currentTimeMillis() + 1000 * 60), 1000L * 60 * 15)
    }

    private suspend fun loadServerStatChannels() {
        serverStatChannels.forEach { server ->
            val supplier: RestEntitySupplier = DiscordConnection.bot!!.kordRef.with(EntitySupplyStrategy.rest)

            supplier.getGuildOrNull(Snowflake(server.first), true)
                ?.let {
                    updateStatChannels(it, server.second)
                }
        }
    }

    private suspend fun updateStatChannels(guild: Guild, channels: List<Pair<Long, String>>) {
        val linkedUsers = DiscordUserConnection.getInstance().countLinkedUsers().orElse(0)
        val spentMoney = try {
            ApplicationService.makeNumberReadable(
                DiscordServerConnection.getInstance().getTotalAmountOfMoneySpent(guild.id.value.toLong()), 3
            )
        } catch (ex: Exception) {
            0
        }
        val monthlyCarries = DiscordServerConnection.getInstance().getCarryAmountOrNull(
            guild.id.value.toLong(),
            ZonedDateTime.now().minusDays(30).toInstant()
        ) ?: 0

        channels.forEach { channel ->
            guild.getChannelOfOrNull<GuildChannel>(Snowflake(channel.first))
                ?.let { guildChannel ->
                    if (channel.second.contains("{member_count}") && guild.approximateMemberCount == null) {
                        return@let
                    }

                    val newName = channel.second
                        .replace("{linked_users}", linkedUsers.toString())
                        .replace("{spent_money}", spentMoney.toString())
                        .replace("{member_count}", guild.approximateMemberCount.toString())
                        .replace("{carry_count_monthly}", monthlyCarries.toString())

                    guildChannel.asChannelOfOrNull<TextChannel>()?.let { textChannel ->
                        textChannel.edit {
                            name = newName
                        }
                    }

                    guildChannel.asChannelOfOrNull<VoiceChannel>()?.let { voiceChannel ->
                        voiceChannel.edit {
                            name = newName
                        }
                    }
                }
        }
    }
}