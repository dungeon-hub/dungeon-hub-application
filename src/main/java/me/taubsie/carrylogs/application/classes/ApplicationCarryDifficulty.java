package me.taubsie.carrylogs.application.classes;

import me.taubsie.dungeonhub.common.CarryDifficulty;
import me.taubsie.dungeonhub.common.CarryTier;

public class ApplicationCarryDifficulty extends CarryDifficulty {
    public ApplicationCarryDifficulty(String id, String identifier, String displayName, CarryTier carryTier) {
        super(id, identifier, displayName, carryTier);
    }
}