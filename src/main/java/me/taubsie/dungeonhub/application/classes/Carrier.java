package me.taubsie.dungeonhub.application.classes;

import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.common.CarryType;
import me.taubsie.dungeonhub.common.ScoreValue;
import org.javacord.api.entity.user.User;

import java.util.List;

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

    public List<ScoreValue> getScore(long serverId)
    {
        return DungeonHubConnection.getInstance().countScore(serverId, id);
    }

    public long getScore(CarryType carryType) {
        return DungeonHubConnection.getInstance().getScore(id, carryType);
    }

    public long setScore(CarryType carryType, long amount)
    {
        return DungeonHubConnection.getInstance().modifyScore(id, carryType, amount);
    }
}