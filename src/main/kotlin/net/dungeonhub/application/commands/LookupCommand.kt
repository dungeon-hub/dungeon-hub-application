package net.dungeonhub.application.commands

import dev.kord.core.behavior.UserBehavior
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.publicUserCommand
import dev.kordex.core.i18n.toKey
import net.dungeonhub.application.connection.FlaggingConnection
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.misc.FlagResponse
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.addEmbed
import net.dungeonhub.application.service.color
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.i18n.Translations
import net.dungeonhub.i18n.Translations.Command.Lookup
import net.dungeonhub.mojang.connection.MojangConnection

@LoadExtension
class LookupCommand : Extension() {
    override val name = "lookup-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = Lookup.name
            description = Lookup.description
            allowInDms = true

            publicSubCommand(::LookupPlayerArguments) {
                name = Lookup.Player.name
                description = Lookup.Player.description

                action {
                    respond {
                        respondToLookup(null, arguments.ign)()
                    }
                }
            }

            publicSubCommand {
                name = Lookup.Appeal.name
                description = Lookup.Appeal.description

                action {
                    respond {
                        addEmbed {
                            color(EmbedColor.Default)
                            title = "Account Review Request"
                            description = "Please appeal at https://forms.gle/U6reUwtQSwSTvJB47\nPlease make sure your screenshots **are not cropped**!"
                        }
                    }
                }
            }

            publicSubCommand(::LookupUserArguments) {
                name = Lookup.User.name
                description = Lookup.User.description

                action {
                    respond {
                        respondToLookup(arguments.user, null)()
                    }
                }
            }
        }


        publicUserCommand {
            name = Translations.UserCommand.Lookup.name

            action {
                respond {
                    respondToLookup(targetUsers.first(), null)()
                }
            }
        }
    }


    private fun respondToLookup(target: UserBehavior?, ign: String?): suspend FollowupMessageCreateBuilder.() -> Unit =
        respond@{
            if (target == null && ign == null) {
                addEmbed {
                    color(EmbedColor.Negative)
                    description = "No user to lookup supplied!"
                }

                return@respond
            }

            val uuid =
                target?.let { target -> DiscordUserConnection.getLinkedById(target.id.value.toLong())?.minecraftId }
                    ?: ign?.let { ign -> MojangConnection.getUUIDByName(ign) }

            val actualIgn = uuid?.let { MojangConnection.getNameByUUID(uuid) }
            val actualTarget = target?.id?.value?.toLong() ?: uuid?.let { DiscordUserConnection.findUserByUuid(it)?.id }

            val flagResponses = FlaggingConnection.isFlagged(uuid, actualTarget)

            val flagged = flagResponses
                .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
                .filter { flagResponse: FlagResponse ->
                    ((flagResponse.uuid != null && flagResponse.uuid.flagged)
                            || (flagResponse.discord != null && flagResponse.discord.flagged))
                }

            val downedServices = flagResponses.filter {
                (it.uuidGiven && it.uuid == null) ||
                        (it.discordGiven && it.discord == null)
            }

            if (downedServices.isNotEmpty()) {
                addEmbed {
                    footer = null
                    timestamp = null
                    color(EmbedColor.Negative)
                    description =
                        "**The following services are currently unreachable, which could cause false negatives**:\n${
                            downedServices.joinToString(separator = "\n- ", prefix = "- ") {
                                "${it.name} (${
                                    if (it.uuid == null) {
                                        if (it.discord == null) "UUID, discord id" else "UUID"
                                    } else "discord id"
                                })"
                            }
                        }"
                }
            }

            addEmbed {
                footer = null
                timestamp = null
                color(if(flagged.isNotEmpty()) EmbedColor.Negative else EmbedColor.Positive)
                description = formatDescription(flagged.isNotEmpty(), target != null, actualTarget, actualIgn)
            }

            addEmbed {
                color(if (flagged.isNotEmpty()) EmbedColor.Negative else if (downedServices.isNotEmpty()) EmbedColor.Information else EmbedColor.Positive)
                fields = ApplicationService.formatTotalFlagDetails(flagResponses)
                val infoFooter = EmbedBuilder.Footer()
                infoFooter.text = "discord.dungeon-hub.net • Did you know that this is included in /player?"
                footer = infoFooter
            }
        }

    inner class LookupUserArguments : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "The discord user to lookup.".toKey()
        }
    }


    inner class LookupPlayerArguments : Arguments() {
        val ign by string {
            name = "ign".toKey()
            description = "The IGN of the player.".toKey()
            minLength = 2
        }
    }

    fun formatDescription(flagged: Boolean, discordGiven: Boolean, target: Long?, actualIgn: String?): String {
        return if (flagged) {
            (if (discordGiven) {
                "The given user (<@$target>) **is flagged**${
                    if (actualIgn != null)
                        ", or the player they're linked to ($actualIgn) **is flagged**"
                    else
                        ""
                }"
            } else {
                "The given player ($actualIgn) **is flagged**${
                    if (target != null)
                        ", or the user they're linked to (<@$target>) **is flagged**"
                    else
                        ""
                }"
            }) + ".\n## **It is likely not safe to interact with them.**"
        } else {
            (if (discordGiven) {
                "The given user (<@$target>)${
                    if (actualIgn != null)
                        " and the minecraft user they're linked to ($actualIgn) are"
                    else
                        " is"
                }"
            } else {
                "The given player ($actualIgn)${
                    if (target != null)
                        ", or the user they're linked to (<@$target>) are"
                    else
                        " is"
                }"
            }) + " not flagged"
        }
    }
}
