package me.taubsie.dungeonhub.kord.application.commands

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.loader.LoadExtension
import me.taubsie.dungeonhub.kord.application.service.ApplicationService
import me.taubsie.dungeonhub.kord.application.service.ProfileModerationService
import java.util.stream.Collectors

@LoadExtension
class UserScanCommand : Extension() {
    override val name = "user-scan-command"

    override suspend fun setup() {
        publicSlashCommand {
            name = "userscan"
            description = "Scans for users with a bad username."
            defaultMemberPermissions = Permissions(Permission.BanMembers)
            allowInDms = false

            check {
                hasPermission(Permission.Administrator)
            }

            publicSubCommand {
                name = "scan"
                description = "Add this if flagged users shouldn't be banned."

                action {
                    respond(execute(false, guild!!.asGuild()))
                }
            }

            publicSubCommand {
                name = "ban"
                description = "Add this if flagged users should also be banned."

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
                val checkResult = ProfileModerationService.checkUserName(member.globalName)
                if (checkResult != null) {
                    if (ProfileModerationService.isExcluded(member, guild)) {
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
        embed.color = EmbedColor.NEGATIVE.color
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
                            label = "Show " + (if ((ban)) "banned" else "flagged") + " users"

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
                            label = "Show excluded users"

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