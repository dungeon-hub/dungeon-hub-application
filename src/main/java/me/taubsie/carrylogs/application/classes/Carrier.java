package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.service.ConnectionService;
import org.javacord.api.entity.user.User;

import java.util.Map;

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
        return ConnectionService.getInstance().countScore(id);
    }

    public Long getDungeonScore()
    {
        return ConnectionService.getInstance().getDungeonScore(id);
    }

    public Long getSlayerScore()
    {
        return ConnectionService.getInstance().getSlayerScore(id);
    }

    public Long setScore(String type, long amount)
    {
        return ConnectionService.getInstance().modifyScore(id, type, amount);
    }

    public Long setDungeonScore(long amount)
    {
        return ConnectionService.getInstance().modifyDungeonScore(id, amount);
    }

    public Long setSlayerScore(long amount)
    {
        return ConnectionService.getInstance().modifySlayerScore(id, amount);
    }
}