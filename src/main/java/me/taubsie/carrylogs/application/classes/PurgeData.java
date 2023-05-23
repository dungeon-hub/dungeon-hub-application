package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.enums.RoleConversion;

import java.util.List;

/**
 * This record holds the purge data used for a single user to allow easy querying.
 *
 * @param serverId The server on which the purge took place.
 * @param userId The user to purge.
 * @param rolesToRemove The roles which are set to be removed.
 * @param score The score which the user had.
 * @param purgeType The type of purge.
 * @param purgeThreshold The threshold which the purge had.
 */
public record PurgeData(long serverId, long userId, List<RoleConversion> rolesToRemove, Long score, String purgeType, long purgeThreshold) {
}