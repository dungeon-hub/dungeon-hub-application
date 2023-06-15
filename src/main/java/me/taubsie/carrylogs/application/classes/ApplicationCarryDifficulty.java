package me.taubsie.carrylogs.application.classes;

import me.taubsie.dungeonhub.common.CarryDifficulty;
import me.taubsie.dungeonhub.common.CarryTier;

public class ApplicationCarryDifficulty extends CarryDifficulty {
    public ApplicationCarryDifficulty(long id, String identifier, String displayName, CarryTier carryTier, int price, int score) {
        super(id, identifier, displayName, carryTier, price, score);
    }

    //TODO implement properly
    @Override
    public String toString() {
        return toJson();
    }
}