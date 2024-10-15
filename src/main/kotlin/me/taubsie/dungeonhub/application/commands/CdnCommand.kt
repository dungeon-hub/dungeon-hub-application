package me.taubsie.dungeonhub.application.commands

import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.enumChoice
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.attachment
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.components.components
import dev.kordex.core.components.linkButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.KnownStaticResource
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * A command to access the CDN using the slash command /cdn.
 * This command is only available to a few users: @see [allowedUsers]
 * The command has two subcommands:
 * - `add`: Add a file to the CDN.
 * - `static`: Get a static file from the CDN.
 */
@LoadExtension
class CdnCommand : Extension() {
    override val name = "cdn-command"
    private val allowedUsers = listOf(
        356134481452597250L,
        531094512819240960L,
        564353701003657216L,
        574048571364605992L,
        1116284449190064220L,
        795048346955677748L,
        884589309037011015L,
        346292488837005334L
    )

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "cdn"
            description = "Manage the CDN"
            allowInDms = true
            check {
                failIfNot("You aren't allowed to use this command.") {
                    allowedUsers.contains(event.interaction.user.id.value.toLong())
                }
            }

            ephemeralSubCommand(::AddArguments) {
                name = "add"
                description = "Add a file to the CDN."

                action {
                    respond {
                        val attachmentRequest = Request.Builder().url(arguments.attachment.url.toHttpUrl()).build()

                        val attachmentData: ByteArray =
                            DungeonHubConnection.getInstance()
                                .executeRawRequest(attachmentRequest)
                                .orElseThrow { CommandExecutionException("Couldn't read file data.") }

                        val fileUrl = if (arguments.name != null) {
                            ContentConnection.getInstance()
                                .uploadFile(attachmentData, arguments.name)
                        } else {
                            ContentConnection.getInstance()
                                .uploadFile(attachmentData)
                        }

                        val embedBuilder: EmbedBuilder
                        if (fileUrl != null && fileUrl.isPresent) {
                            val url =
                                ContentConnection.getInstance()
                                    .getCdnUrl(fileUrl.get()).toString()

                            embedBuilder = ApplicationService.embed
                            embedBuilder.title = "File added."
                            embedBuilder.color = EmbedColor.Positive.color
                            embedBuilder.image = url
                            embedBuilder.footer {
                                text = ApplicationService.unstableFooter
                            }
                            embedBuilder.timestamp = null
                            embedBuilder.field("URL") { url }

                            components {
                                linkButton {
                                    label = "Click to open"
                                    this.url = url
                                }
                            }
                        } else {
                            embedBuilder = ApplicationService.errorEmbed
                            embedBuilder.description = "Couldn't upload media file."
                        }

                        embeds = mutableListOf(embedBuilder)
                    }
                }
            }

            ephemeralSubCommand(::StaticArguments) {
                name = "static"
                description = "Show the list of static files of the CDN."

                action {
                    respond {
                        val url =
                            ContentConnection.getInstance()
                                .getStaticUrl(arguments.resource.path).toString()

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Positive.color
                        embed.title = "Static resource"
                        embed.field("Name", true) { arguments.resource.loadDisplayName() }
                        embed.field("File name", true) { arguments.resource.getName() }
                        embed.field("Full URL", false) { url }
                        embed.image = url

                        embeds = mutableListOf(embed)

                        components {
                            linkButton {
                                label = "Open"
                                this.url = url
                            }
                        }
                    }
                }
            }
        }
    }

    inner class AddArguments : Arguments() {
        val attachment by attachment {
            name = "file"
            description = "The file to add."
        }

        val name by optionalString {
            name = "name"
            description = "The name of the file."
        }
    }

    inner class StaticArguments : Arguments() {
        val resource by enumChoice<KnownStaticResource> {
            name = "file"
            description = "The static file to get."
            typeName = "KnownStaticResource"
        }
    }
}