package me.taubsie.dungeonhub.kord.application.listener

import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.disabledButton
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.linkButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.DmChannel
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundException
import me.taubsie.dungeonhub.kord.application.connection.isDungeonHub
import me.taubsie.dungeonhub.kord.application.exceptions.FailedToLoadEmbedException
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread
import kotlin.time.toKotlinDuration

@LoadExtension
class MessageListener : Extension() {
    override val name = "message-listener"

    override suspend fun setup() {
        event<MessageCreateEvent> {
            action {
                loadSkycryptFromTicket(event)
            }
        }
    }

    suspend fun loadSkycryptFromTicket(event: MessageCreateEvent) {
        if (event.guildId == null
            || event.message.channel is DmChannel
            || event.guildId?.isDungeonHub() != true
        ) {
            return
        }

        if (event.message.channel.getMessagesAfter(Snowflake.min, 5).count() != 1) {
            return
        }

        val firstMessage = event.message.channel.messages.reduce { _, message2 -> message2 }

        if (firstMessage.mentionedUsers.count() != 1) {
            return
        }

        val user = firstMessage.mentionedUsers.first()

        val lines = firstMessage.content.split("\n".toRegex()).dropLastWhile { it.isEmpty() }

        if (lines.size < 2) {
            return
        }

        val ignOptional = DiscordUserConnection.getInstance()
            .getLinkedById(user.id.value.toLong())
            .map { obj -> obj.minecraftId }
            .map { uuid -> MojangConnection.getInstance().getNameByUUID(uuid) }
            .or {
                lines.stream()
                    .filter { s -> s.startsWith("IGN: ") }
                    .findFirst()
            }.or {
                runBlocking {
                    future {
                        Optional.ofNullable(user.asMemberOrNull(event.guildId!!)?.effectiveName)
                    }
                }.join()
            }

        if (ignOptional.isEmpty) {
            return
        }

        val ign = ignOptional.get()
            .replace("IGN: ", "")
            .replace("❮(\\S*)❯".toRegex(), "")
            .replace("❊", "")
            .replace("❉", "")
            .replace("❃", "")
            .replace("✽", "")
            .replace("✸", "")
            .replace("✷", "")
            .replace("✶", "")
            .replace("✧", "")
            .replace("✦", "")
            .replace("☆", "")
            .replace("★", "")
            .trim()

        sendPlayerDataEmbed(ign, event.message.channel)
    }

    //TODO threads threads threads
    private suspend fun sendPlayerDataEmbed(ign: String, channel: MessageChannelBehavior) {
        try {
            channel.createMessage {
                embeds = mutableListOf(ApplicationService.getPlayerDataEmbed(ign, null))

                components {
                    linkButton {
                        label = "SkyCrypt"
                        url = ConfigProperty.SKYCRYPT_API_URL.value + "stats/" + ign
                    }
                }
            }
        } catch (playerNotFoundException: PlayerNotFoundException) {
            //TODO load scammer data from discord?

            channel.createEmbed { ApplicationService.getErrorEmbed(playerNotFoundException) }
        } catch (failedToLoadEmbedException: FailedToLoadEmbedException) {
            channel.createMessage {
                embeds = mutableListOf(failedToLoadEmbedException.embed)

                components(Duration.ofMinutes(5).toKotlinDuration()) {
                    linkButton {
                        label = "SkyCrypt"
                        url = ConfigProperty.SKYCRYPT_API_URL.value + "stats/" + ign
                    }

                    ephemeralButton {
                        style = ButtonStyle.Secondary
                        label = "Reload"
                        id = "reload_playerdata"

                        action {
                            edit {
                                components {
                                    linkButton {
                                        label = "SkyCrypt"
                                        url = ConfigProperty.SKYCRYPT_API_URL.value + "stats/" + ign
                                    }

                                    disabledButton {
                                        style = ButtonStyle.Secondary
                                        label = "Reload"
                                        id = "reload_playerdata"
                                    }
                                }

                                val embed = ApplicationService.embed
                                embed.description = "Loading..."

                                embeds = mutableListOf(embed)

                                thread {
                                    runBlocking {
                                        components {
                                            embeds = mutableListOf(
                                                try {
                                                    ApplicationService.getPlayerDataEmbed(ign, null)
                                                } catch (failedToLoadAgain: FailedToLoadEmbedException) {
                                                    failedToLoadAgain.embed
                                                }
                                            )
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
}