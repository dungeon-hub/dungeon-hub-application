package me.taubsie.dungeonhub.application.connection;

import lombok.Getter;
import me.taubsie.dungeonhub.application.loader.ClassLoaderService;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main-class for the application.
 * It automatically loads all listeners and commands and initiates the bot-instance.
 * Even if it doesn't make much of a difference, it is advised to not use {@link #getBot()} too much.
 *
 * @author Taubsie
 * @since 1.0.0
 */
@Getter
public class DiscordConnection {
    private static final Logger logger = LoggerFactory.getLogger(DiscordConnection.class);
    private static final String LINE = "----------------------------------------";

    private static DiscordConnection instance;
    private DiscordApi bot;

    /**
     * The main-method that is executed by the JVM.
     *
     * @param args The command-line parameters passed by the JVM.
     */
    public static void main(String[] args) {
        ClassLoaderService.getInstance().loadStartupListeners();
        ClassLoaderService.getInstance().executeStartup();
    }

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

    /**
     * This resets the activity shown on the bot back to the default.
     */
    private void resetBotActivity() {
        bot.updateActivity(ActivityType.WATCHING,
                bot.getServers().stream().mapToInt(Server::getMemberCount).sum() + " carriers on " + bot.getServers().size() + " servers");
    }

    /**
     * This resets the status of the bot back to the default.
     */
    private void resetBotStatus() {
        bot.updateStatus(UserStatus.ONLINE);
    }

    /**
     * This resets the bot's appearance.
     *
     * @see #resetBotActivity()
     * @see #resetBotStatus()
     */
    public void resetBotAppearance() {
        resetBotActivity();
        resetBotStatus();
    }
}