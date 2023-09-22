package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import org.javacord.api.event.connection.ReconnectEvent;
import org.javacord.api.event.connection.ResumeEvent;
import org.javacord.api.listener.connection.ReconnectListener;
import org.javacord.api.listener.connection.ResumeListener;

@Listener
public class BotReloadListener implements ReconnectListener, ResumeListener {
    @Override
    public void onReconnect(ReconnectEvent event) {
        DiscordConnection.getInstance().resetBotAppearance();
    }

    @Override
    public void onResume(ResumeEvent event) {
        DiscordConnection.getInstance().resetBotAppearance();
    }
}