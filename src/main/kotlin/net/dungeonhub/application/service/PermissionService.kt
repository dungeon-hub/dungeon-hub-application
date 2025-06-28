package net.dungeonhub.application.service

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import net.dungeonhub.application.enums.ServerProperty
import java.util.*

/**
 * This class represents a service used to manage the permissions of users within a system.
 *
 * <p>
 * The PermissionService object provides functionality for handling and controlling the permissions
 * associated with different user roles or types. It allows for the management of permissions and
 * their assignment to individual users or groups of users.
 * </p>
 *
 * <p>
 * In this context, a "permission" refers to a specific action or operation that a user is
 * authorized to perform within the system. Permissions are defined by the {@link PermissionType}
 * enumeration, which provides a set of predefined permission types. Each permission type represents
 * a distinct action or set of actions that can be granted or denied to a user.
 * </p>
 *
 * @see Member
 * @see Permission
 */
object PermissionService {
    fun mayManageServices(member: Member): Boolean {
        return ServerProperty.SCORE_MANAGEMENT_ROLE
            .getValue(member.guild.id.value.toLong())
            .map { id ->
                member.roleIds.contains(Snowflake(id))
            }
            .flatMap { bool: Boolean ->
                if (java.lang.Boolean.TRUE == bool) Optional.of(
                    true
                ) else Optional.empty()
            }
            .orElseGet {
                member.permissions?.contains(Permission.Administrator)
            }
    }
}