package net.dungeonhub.application.commands

import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalUser
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.utils.getLocale
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.i18n.Translations.Command.Score
import net.dungeonhub.model.discord_server.DiscordServerModel
import java.util.*

@LoadExtension
class ScoreCommand : Extension() {
    override val name = "score-command"

    override suspend fun setup() {
        ephemeralSlashCommand(::ScoreArguments) {
            name = Score.name
            description = Score.description
            allowInDms = false

            action {
                respond {
                    val userToCheck = arguments.user ?: event.interaction.user

                    embeds = generateScoreEmbeds(userToCheck, event.interaction.user, guild!!, event.getLocale())
                }
            }
        }
    }

    class ScoreArguments : Arguments() {
        val user by optionalUser {
            name = Score.Arguments.User.name
            description = Score.Arguments.User.description
        }
    }

    companion object {
        suspend fun generateScoreEmbeds(userToCheck: User, issuer: User, guild: GuildBehavior, locale: Locale): MutableList<EmbedBuilder> {
            val scores = DiscordServerConnection.authenticated().getScores(
                DiscordServerModel(guild.id.value.toLong()),
                userToCheck.id.value.toLong()
            ) ?: listOf()

            val carryCount =
                DiscordUserConnection.authenticated().getCarryCount(userToCheck.id.value.toLong(), guild.id.value.toLong())

            return mutableListOf(
                ApplicationService.getScoreCountMessage(
                    userToCheck,
                    issuer,
                    guild,
                    scores,
                    carryCount,
                    locale = locale
                )
            )
        }
    }
}