package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection
import me.taubsie.dungeonhub.common.model.server.DiscordServerModel
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService

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

                    val scores = DiscordServerConnection.getInstance()
                        .getScores(DiscordServerModel(guild!!.id.value.toLong()), userToCheck.id.value.toLong())
                        .orElse(listOf())

                    embeds = mutableListOf(
                        ApplicationService.getScoreCountMessage(userToCheck, event.interaction.user, guild, scores)
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