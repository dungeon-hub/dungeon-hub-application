package me.taubsie.dungeonhub.application.commands

import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import net.dungeonhub.wrapper.kord.createTranscript
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets

@LoadExtension
class TestTranscriptCommand : Extension() {
    override val name = "test-transcript-command"
    private val allowedUsers = listOf(
        356134481452597250L
    )
    private val logger = LoggerFactory.getLogger(TestTranscriptCommand::class.java)

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "test-transcript"
            description = "Tests the transcript feature."
            allowInDms = false
            check {
                failIfNot("You aren't allowed to use this command.") {
                    allowedUsers.contains(event.interaction.user.id.value.toLong())
                }
            }

            action {
                respond {
                    val transcriptChannel = channel.asChannelOfOrNull<GuildMessageChannel>()

                    if (transcriptChannel == null) {
                        val embed = ApplicationService.errorEmbed
                        embed.description = "Please use this in a server text channel."
                        embeds = mutableListOf(embed)
                        return@respond
                    }

                    try {
                        val transcript = transcriptChannel.createTranscript()

                        val url =
                            ContentConnection.getInstance()
                                .uploadFile(transcript.toByteArray(StandardCharsets.UTF_8))
                                .orElse(null)

                        if (!url.isNullOrBlank()) {
                            val embed = ApplicationService.embed
                            embed.description = "Embed > ${ConfigProperty.CDN_URL}$url"
                            embeds = mutableListOf(embed)
                            return@respond
                        }
                    } catch (ioException: IOException) {
                        logger.error(null, ioException)
                    }

                    val embed = ApplicationService.errorEmbed
                    embed.description = "Couldn't generate transcript."
                    embeds = mutableListOf(embed)
                }
            }
        }
    }
}