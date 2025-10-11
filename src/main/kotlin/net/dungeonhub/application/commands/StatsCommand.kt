package net.dungeonhub.application.commands

import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.getLocale
import net.dungeonhub.application.connection.DiscordConnection
import net.dungeonhub.application.connection.DiscordConnection.uptime
import net.dungeonhub.application.connection.getGuildOrNull
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.i18n.Translations.Command.Stats
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.time.toKotlinDuration

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
                        DiscordServerConnection.authenticated().getTotalAmountOfMoneySpent(guild.id.value.toLong()) ?: 0,
                        3
                    )
                    val spentMoneyMonthly = ApplicationService.makeNumberReadable(
                        DiscordServerConnection.authenticated().getTotalAmountOfMoneySpent(
                            guild.id.value.toLong(),
                            since = ZonedDateTime.now().minusDays(30).toInstant()
                        ) ?: 0, 3
                    )
                    val totalCarries =
                        DiscordServerConnection.authenticated().getCarryAmount(guild.id.value.toLong()) ?: 0
                    val monthlyCarries = DiscordServerConnection.authenticated().getCarryAmount(
                        guild.id.value.toLong(),
                        since = ZonedDateTime.now().minusDays(30).toInstant()
                    ) ?: 0

                    addEmbed {
                        color(EmbedColor.Default)
                        title = Stats.Response.title.withNamedPlaceholders(
                            "name" to guild.name
                        ).translateLocale(locale)

                        field(
                            Stats.Response.Fields.Uptime.name.translateLocale(locale), false
                        ) { Duration.between(uptime, Instant.now()).withNanos(0).toKotlinDuration().toString() }
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