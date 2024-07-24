package me.taubsie.dungeonhub.application.listener

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.user.UserUpdateEvent
import me.taubsie.dungeonhub.application.connection.getMutualServers
import me.taubsie.dungeonhub.application.loader.LoadExtension
import me.taubsie.dungeonhub.application.service.ProfileModerationService

@LoadExtension
class UserChangeListener : Extension() {
    override val name = "user-change-listener"

    override suspend fun setup() {
        event<MemberJoinEvent> {
            action {
                if (ProfileModerationService.isExcluded(event.member)) {
                    return@action
                }

                val result = ProfileModerationService.checkUserName(event.member.globalName)
                if (result != null) {
                    ProfileModerationService.handleUserBan(event.getGuild(), event.member, result)
                }
            }
        }

        event<UserUpdateEvent> {
            action {
                event.user.getMutualServers().collect { member ->
                    if (ProfileModerationService.isExcluded(member)) {
                        return@collect
                    }

                    val result = ProfileModerationService.checkUserName(event.user.globalName)

                    if (result != null) {
                        ProfileModerationService.handleUserBan(member.guild.asGuild(), member, result)
                    }
                }
            }
        }
    }
}