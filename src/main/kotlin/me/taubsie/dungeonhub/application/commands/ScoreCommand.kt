package me.taubsie.dungeonhub.application.commands

import dev.kord.common.asJavaLocale
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalUser
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.i18n.toKey
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.DiscordServerConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.model.discord_server.DiscordServerModel

@LoadExtension
class ScoreCommand : Extension() {
    override val name = "score-command"

    override suspend fun setup() {
        ephemeralSlashCommand(::ScoreArguments) {
            name = "score".toKey()
            description = "Use this to see the score of yourself or another user.".toKey()
            allowInDms = false

            action {
                respond {
                    val userToCheck = arguments.user ?: event.interaction.user

                    val scores = DiscordServerConnection.getScores(
                        DiscordServerModel(guild!!.id.value.toLong()),
                        userToCheck.id.value.toLong()
                    ) ?: listOf()

                    val carryCount =
                        DiscordUserConnection.getCarryCount(userToCheck.id.value.toLong(), guild!!.id.value.toLong())

                    embeds = mutableListOf(
                        ApplicationService.getScoreCountMessage(
                            userToCheck,
                            event.interaction.user,
                            guild,
                            scores,
                            carryCount,
                            locale = event.interaction.locale?.asJavaLocale()
                        )
                    )
                }
            }
        }
    }

    inner class ScoreArguments : Arguments() {
        val user by optionalUser {
            name = "user".toKey()
            description = "The user to check the carries for.".toKey()
        }
    }
}