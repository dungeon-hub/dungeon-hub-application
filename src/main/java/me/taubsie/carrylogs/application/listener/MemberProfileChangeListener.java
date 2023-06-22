package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ProfileModerationService;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.user.UserChangeNameEvent;
import org.javacord.api.listener.user.UserChangeNameListener;

@Listener
public class MemberProfileChangeListener implements UserChangeNameListener {
    @Override
    public void onUserChangeName(UserChangeNameEvent userChangeNameEvent) {
        for(Server server : userChangeNameEvent.getUser().getMutualServers()) {
            if (ProfileModerationService.getInstance().isExcluded(userChangeNameEvent.getUser(), server)) {
                continue;
            }

            String result = ProfileModerationService.getInstance().checkUserName(userChangeNameEvent.getNewName());

            if (result != null) {
                ProfileModerationService.getInstance().handleUserBan(server, userChangeNameEvent.getUser(), result);
            }
        }
    }
}