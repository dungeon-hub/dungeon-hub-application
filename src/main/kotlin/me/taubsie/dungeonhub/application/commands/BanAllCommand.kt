package me.taubsie.dungeonhub.application.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ApplicationService
import me.taubsie.dungeonhub.application.service.ProfileModerationService

@LoadExtension
class BanAllCommand : Extension() {
    override val name = "ban-all-command"

    override suspend fun setup() {
        publicSlashCommand(::BanAllArguments) {
            name = "ban-all"
            description = "Uses the profile moderation to ban all given users."
            defaultMemberPermissions = Permissions(Permission.BanMembers)
            allowInDms = false

            action {
                respond {
                    val executor = user.asUser()
                    val users = arguments.users.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toList()

                    val errors: MutableList<String> = ArrayList()

                    if (users.isEmpty()) {
                        throw InvalidOptionException(
                            "users",
                            "Please provide a comma-separated list of users to ban."
                        )
                    }

                    for (userId in users) {
                        val user = guild!!.getMemberOrNull(Snowflake(userId.trim()))

                        if (user == null) {
                            errors.add(userId)
                            continue
                        }

                        ProfileModerationService.handleUserBan(guild!!.asGuild(), user, executor, arguments.reason)
                    }

                    if (errors.isEmpty()) {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.POSITIVE.color
                        embed.description =
                            "Successfully banned " + (if (users.size > 1) "all " else "") + users.size + " user" + (if (users.size > 1) "s." else ".")

                        embeds = mutableListOf(embed)
                    } else {
                        val embed = ApplicationService.embed
                        embed.color = EmbedColor.NEGATIVE.color
                        embed.description =
                            "Couldn't ban the following user(s):\n${java.lang.String.join(", ", errors)}"

                        embeds = mutableListOf(embed)
                    }
                }
            }
        }
    }

    inner class BanAllArguments : Arguments() {
        val users by string {
            name = "users"
            description = "Comma-separated list of users to ban."
            minLength = 6
        }

        val reason by string {
            name = "reason"
            description = "Reason for the ban."
            minLength = 2
        }
    }
}