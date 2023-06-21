package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.enums.RoleConversion;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.common.config.Nameable;

import java.util.List;
import java.util.Map;

public enum PurgeType implements Nameable {
    DUNGEONS(
            "dungeons",
            (threshold, serverId) -> DungeonHubConnection.getInstance().getPurgeableUsers(threshold, serverId,
                    "dungeons"),
            RoleConversion::getDungeonCarryRoles
    ),
    DUNGEONS_NO_MASTERMODE(
            "dungeons without mastermode",
            (threshold, serverId) -> DungeonHubConnection.getInstance().getPurgeableUsers(threshold, serverId,
                    "dungeons"),
            RoleConversion::getDungeonCarryRolesWithoutMasterMode
    ),
    SLAYER(
            "slayer",
            (threshold, serverId) -> DungeonHubConnection.getInstance().getPurgeableUsers(threshold, serverId,
                    "slayer"),
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

    public Map<Long, Long> getPurgeData(long threshold, long serverId) {
        return purgeData.execute(threshold, serverId);
    }

    public List<RoleConversion> getRolesToRemove() {
        return List.of(rolesToRemove.execute());
    }

    private interface PurgeData {
        Map<Long, Long> execute(long threshold, long serverId);
    }

    private interface RolesToRemove {
        RoleConversion[] execute();
    }
}