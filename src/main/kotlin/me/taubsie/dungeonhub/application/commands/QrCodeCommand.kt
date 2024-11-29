package me.taubsie.dungeonhub.application.commands

import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.WriterException
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.attachment
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import net.dungeonhub.connection.ContentConnection
import net.dungeonhub.i18n.Translations.Command.QrCode
import java.net.URL
import javax.imageio.ImageIO

@LoadExtension
class QrCodeCommand : Extension() {
    override val name = "qr-code-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = QrCode.name
            description = QrCode.description
            allowInDms = true

            publicSubCommand(::GenerateArguments) {
                name = "generate".toKey()
                description = "Generate a QR code from an URL.".toKey()

                action {
                    respond {
                        try {
                            val image = ApplicationService.generateQRCodeImage(arguments.url)

                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Default.color

                            val cdnLink = (ContentConnection.uploadFile(ApplicationService.readImageData(image))
                                ?.let { ContentConnection.getCdnUrl(it) }
                                ?: throw CommandExecutionException("Couldn't upload QR code data to CDN."))
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
                name = "read".toKey()
                description = "Read the URL from a QR code.".toKey()

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
                                embed.color = EmbedColor.Default.color
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
            name = "qr-code".toKey()
            description = "The QR code to get the content from.".toKey()
        }
    }

    inner class GenerateArguments : Arguments() {
        val url by string {
            name = "url".toKey()
            description = "The url of the QR code.".toKey()
        }
    }
}