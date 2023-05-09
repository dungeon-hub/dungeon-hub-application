package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.enums.RoleConversion;

import java.util.List;

public record PurgeData(long serverId, long userId, List<RoleConversion> rolesToRemove, Long score, String purgeType, long purgeThreshold) {
}