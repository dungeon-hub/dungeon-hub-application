package me.taubsie.carrylogs.application.classes;

import me.taubsie.dungeonhub.common.CarryTier;
import me.taubsie.dungeonhub.common.CarryType;

public class ApplicationCarryTier extends CarryTier {
    public ApplicationCarryTier(long id, String identifier, String displayName, CarryType carryType) {
        super(id, identifier, displayName, carryType);
    }
}