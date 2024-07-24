package me.taubsie.dungeonhub.application.misc

import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel
import me.taubsie.dungeonhub.common.model.purge_type.PurgeTypeModel

/**
 * This record holds the purge data used for a single user to allow easy querying.
 *
 * @param userId         The user to purge.
 * @param rolesToRemove  The roles which are set to be removed.
 * @param score          The score which the user had.
 * @param purgeType      The type of purge.
 * @param purgeThreshold The threshold which the purge had.
 */
@JvmRecord
data class PurgeData(
    val userId: Long, val rolesToRemove: List<DiscordRoleModel>, val score: Long, val purgeType: PurgeTypeModel,
    val purgeThreshold: Long
) 