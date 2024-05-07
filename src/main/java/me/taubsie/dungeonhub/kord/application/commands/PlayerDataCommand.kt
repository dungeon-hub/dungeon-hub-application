package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.linkButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundException
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.FailedToLoadEmbedException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import org.slf4j.LoggerFactory

@LoadExtension
class PlayerDataCommand : Extension() {
    override val name = "playerdata-command"
    private val logger = LoggerFactory.getLogger(PlayerDataCommand::class.java)

    override suspend fun setup() {
        publicSlashCommand(::PlayerDataArguments) {
            name = "playerdata"
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
                    } catch (playerNotFoundException: PlayerNotFoundException) {
                        embeds = mutableListOf(
                            ApplicationService.getErrorEmbed(playerNotFoundException)
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

    inner class PlayerDataArguments : Arguments() {
        val ign by string {
            name = "ign"
            description = "The IGN of the player."
            minLength = 2
        }
    }
}