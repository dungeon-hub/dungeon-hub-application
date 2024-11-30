package me.taubsie.dungeonhub.application.commands

import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getLocale
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.getGuildOrNull
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.addEmbed
import me.taubsie.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.i18n.Translations.Command.Stats
import java.time.ZonedDateTime

@LoadExtension
class StatsCommand : Extension() {
    override val name = "stats-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = Stats.name
            description = Stats.description
            allowInDms = false

            action {
                respond {
                    val guild = DiscordConnection.bot!!.kordRef
                        .with(EntitySupplyStrategy.rest)
                        .getGuildOrNull(guild!!.id, true)!!

                    val locale = event.getLocale()

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
                        title = Stats.Response.title.withNamedPlaceholders(
                            "name" to guild.name
                        ).translateLocale(locale)

                        field(
                            Stats.Response.Fields.MemberCount.name.translateLocale(locale), true
                        ) { memberCount.toString() }
                        field(
                            Stats.Response.Fields.MoneySpent.name.translateLocale(locale), true
                        ) { "$spentMoney ($spentMoneyMonthly)" }
                        field(
                            Stats.Response.Fields.CarryAmount.name.translateLocale(locale), true
                        ) { "$totalCarries ($monthlyCarries)" }
                    }
                }
            }
        }
    }
}