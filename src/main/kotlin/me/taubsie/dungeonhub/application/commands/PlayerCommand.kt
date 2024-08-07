package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.linkButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadEmbedException
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundWarning
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import org.slf4j.LoggerFactory

@LoadExtension
class PlayerCommand : Extension() {
    override val name = "player-command"
    private val logger = LoggerFactory.getLogger(PlayerCommand::class.java)

    override suspend fun setup() {
        publicSlashCommand(::PlayerArguments) {
            name = "player"
            description = "Displays the data for the given user user."
            allowInDms = true

            action {
                respond {
                    components {
                        linkButton {
                            label = "SkyCrypt"
                            url = ConfigProperty.SKYCRYPT_API_URL.toString() + "stats/" + arguments.ign
                        }
                    }

                    try {
                        embeds = mutableListOf(
                            ApplicationService.getPlayerDataEmbed(
                                arguments.ign,
                                user.id.value.toLong()
                            )
                        )
                    } catch (failedToLoadEmbedException: FailedToLoadEmbedException) {
                        val embed = failedToLoadEmbedException.embed
                        embed.color = EmbedColor.NEGATIVE.color
                        embeds = mutableListOf(
                            embed
                        )
                    } catch (playerNotFoundWarning: PlayerNotFoundWarning) {
                        embeds = mutableListOf(
                            ApplicationService.getErrorEmbed(playerNotFoundWarning)
                        )
                    } catch (commandExecutionException: CommandExecutionException) {
                        embeds = mutableListOf(
                            ApplicationService.getErrorEmbed(commandExecutionException)
                        )

                        logger.error(null, commandExecutionException)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    inner class PlayerArguments : Arguments() {
        val ign by string {
            name = "ign"
            description = "The IGN of the player."
            minLength = 2
        }
    }
}