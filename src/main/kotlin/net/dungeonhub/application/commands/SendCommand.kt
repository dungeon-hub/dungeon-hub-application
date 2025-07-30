package net.dungeonhub.application.commands

import com.google.common.collect.Iterables
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.rest.builder.message.actionRow
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.channel
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import net.dungeonhub.application.enums.CntRequestType
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.enums.ServerProperty
import net.dungeonhub.application.exceptions.CommandExecutionException
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.color
import net.dungeonhub.i18n.Translations.Command.Send

@LoadExtension
class SendCommand : Extension() {
    override val name = "send-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = Send.name
            description = Send.description
            allowInDms = false

            publicSubCommand(::SendLinkMessageArguments) {
                name = "link-message".toKey()
                description = "Sends a message with components that are there to make linking easier.".toKey()
                defaultMemberPermissions = Permissions(Permission.ManageMessages)

                action {
                    respond {
                        val channel = arguments.channel.asChannelOfOrNull<GuildMessageChannel>()
                            ?: throw CommandExecutionException("Channel couldn't be found or isn't a message channel. Please let an administrator know.")

                        channel.createMessage {
                            val embed = ApplicationService.embed
                            embed.color = EmbedColor.Default.color
                            embed.title = "Linking"
                            embed.description =
                                "Please link to your Minecraft account using the buttons below.\nRemember to never give out the email connected to your Microsoft account and to never click any links!\n\nCheck out this video if you're still unsure if messages similar to this are legit: https://youtu.be/WRRIOkM8oe8?t=743&si=oc71yA9h-XJUsGpX"
                            embeds = mutableListOf(embed)

                            actionRow {
                                if (arguments.silent == true) {
                                    addSilentLinkButtons()
                                } else {
                                    addLinkButtons()
                                }
                            }
                        }

                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.Positive.color
                        embed.description = "Trying to send message..."
                        embeds = mutableListOf(embed)
                    }
                }
            }

            publicSubCommand(::SendArguments) {
                name = "cnt-message".toKey()
                description = "Send the CNT message into the given channel.".toKey()
                defaultMemberPermissions = Permissions(Permission.Administrator)

                action {
                    respond {
                        val channel = arguments.channel.asChannelOfOrNull<GuildMessageChannel>()
                            ?: throw CommandExecutionException("Channel couldn't be found or isn't a message channel. Please let an administrator know.")

                        val cntChannel = ServerProperty.CNT_MESSAGES_CHANNEL.getValue(guild!!.id.value.toLong()).orElse(null)
                        val cntInfoChannel = ServerProperty.CNT_INFORMATION_CHANNEL.getValue(guild!!.id.value.toLong()).orElse(null)

                        val embed = ApplicationService.embed
                        embed.color(EmbedColor.Positive)
                        embed.description = "Trying to send message..."
                        embeds = mutableListOf(embed)

                        channel.createMessage {
                            val cntEmbed = ApplicationService.embedWithoutTimestamp
                            cntEmbed.title = "Crafts and Transfers Request"
                            cntEmbed.description = """
                                Requests will be sent to <#${cntChannel ?: channel.id}>.
                                The craft and transfer service is free, although **collateral is NOT allowed**.
                                ${if(cntInfoChannel != null) "Please read <#$cntInfoChannel> before requesting." else "Please see above for more information about requesting."}
                                Click the buttons below depending on the value of your request.
                            """

                            embeds = mutableListOf(cntEmbed)

                            actionRow {
                                interactionButton(ButtonStyle.Primary, "help-rep") {
                                    emoji(ReactionEmoji.Unicode("❔"))
                                    label = "How reputation works"
                                }
                            }

                            Iterables.partition(CntRequestType.entries.asIterable(), 5).forEach { requestTypes ->
                                actionRow {
                                    requestTypes.forEach { requestType ->
                                        interactionButton(ButtonStyle.Secondary, requestType.buttonId) {
                                            label = requestType.description
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    open inner class SendArguments : Arguments() {
        val channel by channel {
            name = "channel".toKey()
            description = "The channel to send the message into.".toKey()
            requiredChannelTypes = mutableSetOf(
                ChannelType.GuildText,
                ChannelType.GuildVoice,
                ChannelType.PublicGuildThread
            )
        }
    }

    inner class SendLinkMessageArguments : SendArguments() {
        val silent by optionalBoolean {
            name = "silent".toKey()
            description = "If the bot should reply silently (using ephemeral messages)".toKey()
        }
    }
}