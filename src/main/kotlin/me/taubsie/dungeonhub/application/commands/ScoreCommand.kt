package me.taubsie.dungeonhub.application.commands

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalUser
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.common.model.server.DiscordServerModel

@LoadExtension
class ScoreCommand : Extension() {
    override val name = "score-command"

    override suspend fun setup() {
        ephemeralSlashCommand(::ScoreArguments) {
            name = "score"
            description = "Use this to see the score of yourself or another user."
            allowInDms = false

            action {
                respond {
                    val userToCheck = arguments.user ?: event.interaction.user

                    val scores =
                        DiscordServerConnection.getInstance()
                            .getScores(DiscordServerModel(guild!!.id.value.toLong()), userToCheck.id.value.toLong())
                            .orElse(listOf())

                    val carryCount =
                        DiscordUserConnection.getInstance()
                            .getCarryCount(userToCheck.id.value.toLong(), guild!!.id.value.toLong()).orElse(null)

                    embeds = mutableListOf(
                        ApplicationService.getScoreCountMessage(
                            userToCheck,
                            event.interaction.user,
                            guild,
                            scores,
                            carryCount
                        )
                    )
                }
            }
        }
    }

    inner class ScoreArguments : Arguments() {
        val user by optionalUser {
            name = "user"
            description = "The user to check the carries for."
        }
    }
}