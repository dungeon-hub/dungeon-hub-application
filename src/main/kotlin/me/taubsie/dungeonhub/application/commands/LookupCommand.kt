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
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.misc.FlagResponse
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.addEmbed
import me.taubsie.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordUserConnection

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
                        val uuid = MojangConnection.getUUIDByName(arguments.ign)

                        val discordId = DiscordUserConnection.findUserByUuid(uuid)?.id

                        val flagResponses: List<FlagResponse> =
                            FlaggingConnection.isFlagged(uuid, discordId)
                                .stream()
                                .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
                                .filter { flagResponse: FlagResponse ->
                                    ((flagResponse.uuid != null && flagResponse.uuid.flagged)
                                            || (flagResponse.discord != null && flagResponse.discord.flagged))
                                }
                                .toList()

                        if (flagResponses.isEmpty()) {
                            addEmbed {
                                color(EmbedColor.Positive)
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
                            color(EmbedColor.Negative)
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
        val uuid = DiscordUserConnection.getLinkedById(target.id.value.toLong())?.minecraftId

        val ign = uuid?.let { MojangConnection.getNameByUUID(uuid) }

        val flagResponses: List<FlagResponse> =
            FlaggingConnection.isFlagged(uuid, target.id.value.toLong())

        val flagged = flagResponses
            .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
            .filter { flagResponse: FlagResponse ->
                ((flagResponse.uuid != null && flagResponse.uuid.flagged)
                        || (flagResponse.discord != null && flagResponse.discord.flagged))
            }

        val downFlaggedServices = flagResponses.filter {
            it.uuid == null || it.discord == null
        }

        if (downFlaggedServices.isNotEmpty()) {
            addEmbed {
                footer = null
                timestamp = null
                color(EmbedColor.Negative)
                description = "**The following services are currently unreachable, which could cause false negatives**:\n${
                    downFlaggedServices.joinToString(separator = "\n", prefix = "- ") {
                        "${it.name} (${
                            if (it.uuid == null) {
                                if (it.discord == null) "UUID, discord id" else "UUID"
                            } else "discord id"
                        })"
                    }
                }"
            }
        }

        if (flagged.isNotEmpty()) {
            addEmbed {
                color(EmbedColor.Negative)
                description =
                    "The given user (${target.mention}) **is flagged**${
                        if (ign != null)
                            ", or the user they're linked to ($ign) **is flagged**"
                        else
                            ""
                    }.\n## **It is likely not safe to interact with them.**\n\n${
                        ApplicationService.formatFlagDetails(
                            flagged
                        )
                    }"
            }
        } else {
            addEmbed {
                color(EmbedColor.Positive)
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