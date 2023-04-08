package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ProfileModerationService;
import org.javacord.api.event.user.UserChangeNameEvent;
import org.javacord.api.listener.user.UserChangeNameListener;

@Listener
public class MemberProfileChangeListener implements UserChangeNameListener {
    @Override
    public void onUserChangeName(UserChangeNameEvent userChangeNameEvent) {
        if(ProfileModerationService.getInstance().isExcluded(userChangeNameEvent.getUser())) {
            return;
        }

        String result = ProfileModerationService.getInstance().checkUserName(userChangeNameEvent.getNewName());

        if(result != null) {
            userChangeNameEvent.getUser()
                    .getMutualServers()
                    .forEach(server -> ProfileModerationService.getInstance().handleUserBan(server, userChangeNameEvent.getUser(), result));
        }
    }
}