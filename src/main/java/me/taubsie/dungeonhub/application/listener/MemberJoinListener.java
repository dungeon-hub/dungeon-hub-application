package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.service.ProfileModerationService;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;

@Listener
public class MemberJoinListener implements ServerMemberJoinListener {
    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent serverMemberJoinEvent) {
        if(ProfileModerationService.getInstance().isExcluded(serverMemberJoinEvent.getUser(),
                serverMemberJoinEvent.getServer())) {
            return;
        }

        String result = ProfileModerationService.getInstance().checkUserName(serverMemberJoinEvent.getUser().getName());

        if(result != null) {
            ProfileModerationService.getInstance().handleUserBan(serverMemberJoinEvent.getServer(),
                    serverMemberJoinEvent.getUser(), result);
        }
    }
}