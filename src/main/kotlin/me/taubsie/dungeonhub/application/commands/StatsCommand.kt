package me.taubsie.dungeonhub.application.commands

import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.getCarryAmountOrNull
import me.taubsie.dungeonhub.application.connection.dungeon_hub.getTotalAmountOfMoneySpentOrNull
import me.taubsie.dungeonhub.application.connection.getGuildOrNull
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.addEmbed
import me.taubsie.dungeonhub.application.service.color
import java.time.ZonedDateTime

@LoadExtension
class StatsCommand : Extension() {
    override val name = "stats-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "stats"
            description = "Shows some stats about this server."
            allowInDms = false

            action {
                respond {
                    val guild = DiscordConnection.bot!!.kordRef
                        .with(EntitySupplyStrategy.rest)
                        .getGuildOrNull(guild!!.id, true)!!

                    val memberCount = guild.approximateMemberCount ?: 0
                    val spentMoney = ApplicationService.makeNumberReadable(
                        DiscordServerConnection.getInstance().getTotalAmountOfMoneySpentOrNull(guild.id.value.toLong())
                            ?: 0, 3
                    )
                    val spentMoneyMonthly = ApplicationService.makeNumberReadable(
                        DiscordServerConnection.getInstance().getTotalAmountOfMoneySpentOrNull(
                            guild.id.value.toLong(),
                            since = ZonedDateTime.now().minusDays(30).toInstant()
                        ) ?: 0, 3
                    )
                    val totalCarries =
                        DiscordServerConnection.getInstance().getCarryAmountOrNull(guild.id.value.toLong()) ?: 0
                    val monthlyCarries = DiscordServerConnection.getInstance().getCarryAmountOrNull(
                        guild.id.value.toLong(),
                        ZonedDateTime.now().minusDays(30).toInstant()
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