package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.enums.RoleConversion;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.common.config.Nameable;

import java.util.List;
import java.util.Map;

public enum PurgeType implements Nameable {
    DUNGEONS(
            "dungeons",
            threshold -> DungeonHubConnection.getInstance().getPurgeableUsers(threshold, "dungeons"),
            RoleConversion::getDungeonCarryRoles
            ),
    SLAYER(
            "slayer",
            threshold -> DungeonHubConnection.getInstance().getPurgeableUsers(threshold, "slayer"),
            RoleConversion::getSlayerCarryRoles
            );

    private final String displayName;
    private final PurgeData purgeData;
    private final RolesToRemove rolesToRemove;

    PurgeType(String displayName, PurgeData purgeData, RolesToRemove rolesToRemove) {
        this.displayName = displayName;
        this.purgeData = purgeData;
        this.rolesToRemove = rolesToRemove;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public Map<Long, Long> getPurgeData(long threshold) {
        return purgeData.execute(threshold);
    }

    public List<RoleConversion> getRolesToRemove() {
        return List.of(rolesToRemove.execute());
    }

    private interface PurgeData {
        Map<Long, Long> execute(long threshold);
    }

    private interface RolesToRemove {
        RoleConversion[] execute();
    }
}