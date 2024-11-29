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
import dev.kordex.core.utils.getLocale
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.enums.KnownStaticResource
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.connection.DungeonHubConnection
import net.dungeonhub.i18n.Translations.Command.Cdn
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
            name = Cdn.name
            description = Cdn.description
            allowInDms = true
            check {
                failIfNot("You aren't allowed to use this command.") {
                    allowedUsers.contains(event.interaction.user.id.value.toLong())
                }
            }

            ephemeralSubCommand(::AddArguments) {
                name = Cdn.Add.name
                description = Cdn.Add.description

                action {
                    respond {
                        val attachmentRequest = Request.Builder().url(arguments.attachment.url.toHttpUrl()).build()

                        val attachmentData: ByteArray =
                            DungeonHubConnection.executeRawRequest(attachmentRequest)?.result
                                ?: throw CommandExecutionException("Couldn't read file data.")

                        val fileUrl = if (arguments.name != null) {
                            ContentConnection.uploadFile(attachmentData, arguments.name!!)
                        } else {
                            ContentConnection.uploadFile(attachmentData)
                        }

                        val embedBuilder: EmbedBuilder
                        if (fileUrl != null) {
                            val url = ContentConnection.getCdnUrl(fileUrl).toString()

                            embedBuilder = ApplicationService.embed
                            embedBuilder.title = Cdn.Add.Response.title.translateLocale(event.getLocale())
                            embedBuilder.color = EmbedColor.Positive.color
                            embedBuilder.image = url
                            embedBuilder.footer {
                                text = ApplicationService.unstableFooter
                            }
                            embedBuilder.timestamp = null
                            embedBuilder.field(Cdn.Add.Response.Fields.url.translateLocale(event.getLocale())) { url }

                            components {
                                linkButton {
                                    label = Cdn.Add.Response.Buttons.Open.label
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
                name = Cdn.Static.name
                description = Cdn.Static.description

                action {
                    respond {
                        val url = ContentConnection.getStaticUrl(arguments.resource.path).toString()

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Positive.color
                        embed.title = Cdn.Static.Response.title.translateLocale(event.getLocale())
                        embed.field(
                            Cdn.Static.Response.Fields.name.translateLocale(event.getLocale()),
                            true
                        ) { arguments.resource.loadDisplayName() }
                        embed.field(
                            Cdn.Static.Response.Fields.fileName.translateLocale(event.getLocale()),
                            true
                        ) { arguments.resource.getName() }
                        embed.field(
                            Cdn.Static.Response.Fields.fullUrl.translateLocale(event.getLocale()),
                            false
                        ) { url }
                        embed.image = url

                        embeds = mutableListOf(embed)

                        components {
                            linkButton {
                                label = Cdn.Static.Response.Buttons.Open.label
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
            name = Cdn.Add.Arguments.File.name
            description = Cdn.Add.Arguments.File.description
        }

        val name by optionalString {
            name = Cdn.Add.Arguments.Name.name
            description = Cdn.Add.Arguments.Name.description
        }
    }

    inner class StaticArguments : Arguments() {
        val resource by enumChoice<KnownStaticResource> {
            name = Cdn.Static.Arguments.File.name
            description = Cdn.Static.Arguments.File.description
            typeName = Cdn.Static.Arguments.File.typeName
        }
    }
}