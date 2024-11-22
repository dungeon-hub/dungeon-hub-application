package me.taubsie.dungeonhub.application.commands

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.components.components
import dev.kordex.core.components.linkButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
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
                        embed.color = EmbedColor.Negative.color
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