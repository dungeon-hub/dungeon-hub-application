package net.dungeonhub.application.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kordex.core.checks.hasPermission
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dungeonhub.application.enums.EmbedColor
import net.dungeonhub.application.loader.LoadExtension
import net.dungeonhub.application.service.ApplicationService
import net.dungeonhub.application.service.ProfileModerationService
import java.util.stream.Collectors

//TODO remove this, together with profile moderation?
@LoadExtension
class UserScanCommand : Extension() {
    override val name = "user-scan-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "userscan".toKey()
            description = "Scans for users with a bad username.".toKey()
            defaultMemberPermissions = Permissions(Permission.BanMembers)
            allowInDms = false

            check {
                hasPermission(Permission.Administrator)
            }

            publicSubCommand {
                name = "scan".toKey()
                description = "Add this if flagged users shouldn't be banned.".toKey()

                action {
                    respond(execute(false, guild!!.asGuild()))
                }
            }

            publicSubCommand {
                name = "ban".toKey()
                description = "Add this if flagged users should also be banned.".toKey()

                action {
                    respond(execute(true, guild!!.asGuild()))
                }
            }
        }
    }

    suspend fun execute(ban: Boolean, guild: Guild): FollowupMessageCreateBuilder.() -> Unit {
        val result: MutableMap<Member, String> = HashMap()
        val excluded: MutableMap<Member, String> = HashMap()

        for (member in guild.members.toList()) {
            if (!member.isBot) {
                val checkResult = ProfileModerationService.checkUserName(member.globalName ?: member.username)
                if (checkResult != null) {
                    if (ProfileModerationService.isExcluded(member)) {
                        excluded[member] = checkResult
                    } else {
                        result[member] = checkResult
                    }
                }
            }
        }

        if (ban) {
            for ((key, value) in result) {
                ProfileModerationService.handleUserBan(guild, key, value)
            }
        }

        val embed = ApplicationService.embed
        embed.color = EmbedColor.Negative.color
        embed.description = ((if (ban) "Banned" else "Flagged")
                + ":\n" + result.entries
            .stream()
            .map { userStringEntry: Map.Entry<Member, String> -> userStringEntry.key.mention + " | " + userStringEntry.value }
            .collect(Collectors.joining("\n"))
                + "\n\nExcluded:\n" +
                excluded.entries
                    .stream()
                    .map { userStringEntry: Map.Entry<Member, String> -> userStringEntry.key.mention + " | " + userStringEntry.value }
                    .collect(Collectors.joining("\n")))

        return {
            embeds = mutableListOf(embed)

            runBlocking {
                launch {
                    components {
                        ephemeralButton {
                            style = ButtonStyle.Danger
                            id = "show_flagged_banned"
                            label = "Show ${(if ((ban)) "banned" else "flagged")} users".toKey()

                            action {
                                respond {
                                    content = getContent(result.keys)
                                    embeds = mutableListOf(getEmbed(result.keys))
                                }
                            }
                        }

                        ephemeralButton {
                            style = ButtonStyle.Secondary
                            id = "show_excluded"
                            label = "Show excluded users".toKey()

                            action {
                                respond {
                                    content = getContent(excluded.keys)
                                    embeds = mutableListOf(getEmbed(excluded.keys))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getContent(users: Set<User>): String {
        return users.stream().map { obj: User -> obj.id.toString() }.collect(Collectors.joining(","))
    }

    private fun getEmbed(users: Set<Member>): EmbedBuilder {
        val embed = ApplicationService.embed
        embed.description = users.stream().map { user: User ->
            user.mention + " | " + user.effectiveName
        }.collect(Collectors.joining("\n"))

        return embed
    }
}