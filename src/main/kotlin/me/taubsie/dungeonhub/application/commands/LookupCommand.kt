package me.taubsie.dungeonhub.application.commands

import dev.kord.core.behavior.UserBehavior
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.publicUserCommand
import me.taubsie.dungeonhub.application.connection.FlaggingConnection
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.misc.FlagResponse
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.addEmbed
import me.taubsie.dungeonhub.application.service.color

@LoadExtension
class LookupCommand : Extension() {
    override val name = "lookup-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "lookup"
            description = "Lookup a player or discord user."
            allowInDms = true

            publicSubCommand(::LookupPlayerArguments) {
                name = "player"
                description = "Lookup a minecraft user."

                action {
                    respond {
                        val uuid = MojangConnection.getInstance().getUUIDByName(arguments.ign)

                        val discordId = DiscordUserConnection.getInstance().findUserByUuid(uuid)
                            .orElse(null)?.id

                        val flagResponses: List<FlagResponse> =
                            FlaggingConnection.getInstance().isFlagged(uuid, discordId)
                                .stream()
                                .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
                                .filter { flagResponse: FlagResponse ->
                                    ((flagResponse.uuid != null && flagResponse.uuid.flagged)
                                            || (flagResponse.discord != null && flagResponse.discord.flagged))
                                }
                                .toList()

                        if (flagResponses.isEmpty()) {
                            addEmbed {
                                color(EmbedColor.POSITIVE)
                                description = "The given user (${arguments.ign})${
                                    if (discordId != null)
                                        " and the discord user they're linked to (<@$discordId>) are"
                                    else
                                        " is"
                                } not flagged"
                            }
                            return@respond
                        }

                        addEmbed {
                            color(EmbedColor.NEGATIVE)
                            description =
                                "The given user (${arguments.ign}) **is flagged**${
                                    if (discordId != null)
                                        ", or the user they're linked to (<@$discordId>) **is flagged**"
                                    else
                                        ""
                                }.\n## **It is likely not safe to interact with them.**\n\n${
                                    ApplicationService.formatFlagDetails(
                                        flagResponses
                                    )
                                }"
                        }
                    }
                }
            }

            publicSubCommand(::LookupUserArguments) {
                name = "user"
                description = "Lookup a discord user."

                action {
                    respond {
                        respondToLookupUser(arguments.user)()
                    }
                }
            }
        }

        publicUserCommand {
            name = "Lookup User"

            action {
                respond {
                    respondToLookupUser(targetUsers.first())()
                }
            }
        }
    }

    private fun respondToLookupUser(target: UserBehavior): suspend FollowupMessageCreateBuilder.() -> Unit = {
        val uuid = DiscordUserConnection.getInstance().getLinkedById(target.id.value.toLong())
            .orElse(null)?.minecraftId

        val ign = uuid?.let { MojangConnection.getInstance().getNameByUUID(uuid) }

        val flagResponses: List<FlagResponse> =
            FlaggingConnection.getInstance().isFlagged(uuid, target.id.value.toLong())
                .stream()
                .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
                .filter { flagResponse: FlagResponse ->
                    ((flagResponse.uuid != null && flagResponse.uuid.flagged)
                            || (flagResponse.discord != null && flagResponse.discord.flagged))
                }
                .toList()

        if (flagResponses.isNotEmpty()) {
            addEmbed {
                color(EmbedColor.NEGATIVE)
                description =
                    "The given user (${target.mention}) **is flagged**${
                        if (ign != null)
                            ", or the user they're linked to ($ign) **is flagged**"
                        else
                            ""
                    }.\n## **It is likely not safe to interact with them.**\n\n${
                        ApplicationService.formatFlagDetails(
                            flagResponses
                        )
                    }"
            }
        } else {
            addEmbed {
                color(EmbedColor.POSITIVE)
                description = "The given user (${target.mention})${
                    if (ign != null)
                        " and the minecraft user they're linked to ($ign) are"
                    else
                        " is"
                } not flagged"
            }
        }
    }

    inner class LookupUserArguments : Arguments() {
        val user by user {
            name = "user"
            description = "The discord user to lookup."
        }
    }

    inner class LookupPlayerArguments : Arguments() {
        val ign by string {
            name = "ign"
            description = "The IGN of the player."
            minLength = 2
        }
    }
}