package me.taubsie.dungeonhub.application.classes;

import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ServerConnection;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.common.model.score.ScoreUpdateModel;
import me.taubsie.dungeonhub.common.model.server.ServerModel;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * WIP
 * <p>
 * This class was supposed to be used as an interface above discord users to allow cleaner access to any api.
 * This hasn't been implemented in any parts of the bot yet.
 * TODO implement
 */
public class Carrier {
    private final long id;

    public Carrier(long id) {
        this.id = id;
    }

    public static Carrier fromUser(@NotNull User user) {
        return new Carrier(user.getId());
    }

    public List<ScoreModel> getScore(long serverId) {
        return ServerConnection.getInstance()
                .getScores(new ServerModel(serverId), id)
                .orElse(new ArrayList<>());
    }

    public long getScore(CarryTypeModel carryType) {
        return ScoreConnection.getInstance(carryType).getScore(id)
                .map(ScoreModel::getScoreAmount)
                .orElse(0L);
    }

    public List<ScoreModel> setScore(CarryTypeModel carryType, long amount) {
        return ScoreConnection.getInstance(carryType)
                .updateScores(new ScoreUpdateModel(id, amount))
                .orElse(new ArrayList<>());
    }
}