package me.taubsie.dungeonhub.application.connection;

import lombok.Getter;
import org.javacord.api.DiscordApi;

@Getter
public class DiscordConnection {
    private static DiscordConnection instance;
    private DiscordApi bot;

    /**
     * Returns the current instance of this class.
     *
     * @return the current instance of this class.
     */
    public static DiscordConnection getInstance() {
        if (instance == null) {
            instance = new DiscordConnection();
        }

        return instance;
    }
}