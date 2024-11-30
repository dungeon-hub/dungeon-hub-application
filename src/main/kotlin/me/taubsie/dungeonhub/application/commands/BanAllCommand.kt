package me.taubsie.dungeonhub.application.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ProfileModerationService
import me.taubsie.dungeonhub.application.service.addEmbed
import me.taubsie.dungeonhub.application.service.color
import net.dungeonhub.i18n.Translations

/**
 * Command to ban all users in a comma-separated list.
 * @see ProfileModerationService
 */
@LoadExtension
class BanAllCommand : Extension() {
    override val name = "ban-all-command"

    override suspend fun setup() {
        publicSlashCommand(::BanAllArguments) {
            name = Translations.Command.BanAll.name
            description = Translations.Command.BanAll.description
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
                        val user = try {
                            user.kord.getUser(Snowflake(userId.trim()), EntitySupplyStrategy.cachingRest)
                        } catch (exception: Exception) {
                            null
                        }

                        if (user == null) {
                            errors.add(userId)
                            continue
                        }

                        ProfileModerationService.handleUserBan(guild!!.asGuild(), user, executor, arguments.reason)
                    }

                    if (errors.isEmpty()) {
                        addEmbed {
                            color(EmbedColor.Positive)
                            description =
                                "Successfully banned " + (if (users.size > 1) "all " else "") + users.size + " user" + (if (users.size > 1) "s." else ".")
                        }
                    } else {
                        addEmbed {
                            color(EmbedColor.Negative)
                            description = "Couldn't ban the following user(s):\n${java.lang.String.join(", ", errors)}"
                        }
                    }
                }
            }
        }
    }

    inner class BanAllArguments : Arguments() {
        val users by string {
            name = Translations.Command.BanAll.Arguments.Users.name
            description = Translations.Command.BanAll.Arguments.Users.description
            minLength = 6
        }

        val reason by string {
            name = Translations.Command.BanAll.Arguments.Reason.name
            description = Translations.Command.BanAll.Arguments.Reason.description
            minLength = 2
        }
    }
}