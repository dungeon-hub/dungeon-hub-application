package net.dungeonhub.application.commands

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.components.components
import dev.kordex.core.components.linkButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import net.dungeonhub.application.config.ConfigProperty
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.exceptions.FailedToLoadEmbedException
import net.dungeonhub.application.exceptions.PlayerNotFoundWarning
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.i18n.Translations.Command.Player
import org.slf4j.LoggerFactory

@LoadExtension
class PlayerCommand : Extension() {
    override val name = "player-command"
    private val logger = LoggerFactory.getLogger(PlayerCommand::class.java)

    override suspend fun setup() {
        publicSlashCommand(::PlayerArguments) {
            name = Player.name
            description = Player.description
            allowInDms = true

            action {
                respond {
                    components {
                        linkButton {
                            label = Player.Response.Buttons.Skycrypt.label
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
            name = Player.Arguments.Ign.name
            description = Player.Arguments.Ign.description
            minLength = 2
        }
    }
}