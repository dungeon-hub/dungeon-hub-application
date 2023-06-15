package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import org.javacord.api.entity.user.User;

import java.util.Map;

/**
 * WIP
 * <p>
 * This class was supposed to be used as an interface above discord users to allow cleaner access to any api.
 * This hasn't been implemented in any parts of the bot yet.
 * TODO implement
 */
public class Carrier
{
    private final long id;

    public Carrier(long id)
    {
        this.id = id;
    }

    public static Carrier fromUser(User user)
    {
        if(user == null) {
            return null;
        }

        return new Carrier(user.getId());
    }

    public Map<String, Long> getScore()
    {
        return DungeonHubConnection.getInstance().countScore(id);
    }

    public long getScore(String type) {
        return DungeonHubConnection.getInstance().getScore(id, type);
    }

    public long setScore(String type, long amount)
    {
        return DungeonHubConnection.getInstance().modifyScore(id, type, amount);
    }
}