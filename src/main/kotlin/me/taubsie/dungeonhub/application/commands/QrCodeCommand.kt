package me.taubsie.dungeonhub.application.commands

import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.WriterException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.attachment
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.rest.builder.message.actionRow
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import java.net.URL
import javax.imageio.ImageIO

@LoadExtension
class QrCodeCommand : Extension() {
    override val name = "qr-code-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "qr-code"
            description = "Work with QR codes."
            allowInDms = true

            publicSubCommand(::GenerateArguments) {
                name = "generate"
                description = "Generate a QR code from an URL."

                action {
                    respond {
                        try {
                            val image = ApplicationService.generateQRCodeImage(arguments.url)

                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.DEFAULT.color

                            val cdnLink =
                                ContentConnection.getInstance()
                                    .uploadFile(
                                        ApplicationService.readImageData(image)
                                    )
                                    .map { s ->
                                        ContentConnection.getInstance()
                                            .getCdnUrl(s)
                                    }
                                    .orElseThrow { CommandExecutionException("Couldn't upload QR code data to CDN.") }
                                    .toString()

                            embed.image = cdnLink
                            embed.field {
                                name = "CDN Link"
                                value = cdnLink
                                inline = false
                            }

                            embeds = mutableListOf(embed)
                        } catch (writerException: WriterException) {
                            embeds = mutableListOf(
                                ApplicationService.errorEmbed
                            )
                        }
                    }
                }
            }

            publicSubCommand(::ReadArguments) {
                name = "read"
                description = "Read the URL from a QR code."

                action {
                    respond {
                        val attachment = arguments.attachment

                        if (attachment.isImage) {
                            try {
                                val result = ApplicationService.readQRCodeImage(
                                    ImageIO.read(URL(attachment.url))
                                )

                                val embed = ApplicationService.embed
                                embed.url = result
                                embed.description = "The given QR code leads to the site:\n$result"
                                embed.color = EmbedColor.DEFAULT.color
                                embeds = mutableListOf(embed)

                                actionRow {
                                    linkButton(result) {
                                        label = "Result"
                                    }
                                }
                            } catch (e: ChecksumException) {
                                embeds = mutableListOf(
                                    ApplicationService.errorEmbed
                                )
                            } catch (e: NotFoundException) {
                                embeds = mutableListOf(
                                    ApplicationService.errorEmbed
                                )
                            } catch (e: FormatException) {
                                embeds = mutableListOf(
                                    ApplicationService.errorEmbed
                                )
                            }
                        } else {
                            val embed = ApplicationService.embed
                            embed.description = "Please input a valid picture."

                            embeds = mutableListOf(
                                ApplicationService.getErrorEmbed(embed)
                            )
                        }
                    }
                }
            }
        }
    }

    inner class ReadArguments : Arguments() {
        val attachment by attachment {
            name = "qr-code"
            description = "The QR code to get the content from."
        }
    }

    inner class GenerateArguments : Arguments() {
        val url by string {
            name = "url"
            description = "The url of the QR code."
        }
    }
}