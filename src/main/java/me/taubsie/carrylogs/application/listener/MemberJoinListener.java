package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ProfileModerationService;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;

public class MemberJoinListener implements ServerMemberJoinListener {
    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent serverMemberJoinEvent) {
        if(serverMemberJoinEvent.getUser().isBot()
                || ProfileModerationService.getInstance().isOverwritten(serverMemberJoinEvent.getUser().getId())
                || ProfileModerationService.getInstance().isVerified(serverMemberJoinEvent.getUser(),
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