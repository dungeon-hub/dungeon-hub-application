package me.taubsie.dungeonhub.application.commands

import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.getGuildOrNull
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.addEmbed
import me.taubsie.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import java.time.ZonedDateTime

@LoadExtension
class StatsCommand : Extension() {
    override val name = "stats-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "stats".toKey()
            description = "Shows some stats about this server.".toKey()
            allowInDms = false

            action {
                respond {
                    val guild = DiscordConnection.bot!!.kordRef
                        .with(EntitySupplyStrategy.rest)
                        .getGuildOrNull(guild!!.id, true)!!

                    val memberCount = guild.approximateMemberCount ?: 0
                    val spentMoney = ApplicationService.makeNumberReadable(
                        DiscordServerConnection.getTotalAmountOfMoneySpent(guild.id.value.toLong()) ?: 0,
                        3
                    )
                    val spentMoneyMonthly = ApplicationService.makeNumberReadable(
                        DiscordServerConnection.getTotalAmountOfMoneySpent(
                            guild.id.value.toLong(),
                            since = ZonedDateTime.now().minusDays(30).toInstant()
                        ) ?: 0, 3
                    )
                    val totalCarries =
                        DiscordServerConnection.getCarryAmount(guild.id.value.toLong()) ?: 0
                    val monthlyCarries = DiscordServerConnection.getCarryAmount(
                        guild.id.value.toLong(),
                        since = ZonedDateTime.now().minusDays(30).toInstant()
                    ) ?: 0

                    addEmbed {
                        color(EmbedColor.Default)
                        title = "Stats for server ${guild.name}"

                        field("Member count", true) { memberCount.toString() }
                        field("Total money spent (30 days)", true) { "$spentMoney ($spentMoneyMonthly)" }
                        field("Total amount of carries (30 days)", true) { "$totalCarries ($monthlyCarries)" }
                    }
                }
            }
        }
    }
}