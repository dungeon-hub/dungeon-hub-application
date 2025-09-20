package net.dungeonhub.application.commands

import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.i18n.toKey
import net.dungeonhub.application.config.ConfigProperty
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.wrapper.kord.createTranscript
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets

@LoadExtension
class TestTranscriptCommand : Extension() {
    override val name = "test-transcript-command"
    private val allowedUsers = listOf(
        356134481452597250,
        574048571364605992
    )
    private val logger = LoggerFactory.getLogger(TestTranscriptCommand::class.java)

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "test-transcript".toKey()
            description = "Tests the transcript feature.".toKey()
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

                        val url = ContentConnection.authenticated().uploadFile(transcript.toByteArray(StandardCharsets.UTF_8))

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