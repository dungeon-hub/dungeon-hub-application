package me.taubsie.carrylogs.application.classes;

import me.taubsie.dungeonhub.common.CarryTier;
import me.taubsie.dungeonhub.common.CarryType;

import java.util.List;

public class ApplicationCarryTier extends CarryTier {
    public ApplicationCarryTier(String id, String identifier, String displayName, CarryType carryType, List<Long> roles) {
        super(id, identifier, displayName, carryType, roles);
    }
}