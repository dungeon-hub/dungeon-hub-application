package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.classes.ServerProperty;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents a service used to manage the permissions of users within a system.
 *
 * <p>
 * The PermissionService class provides functionality for handling and controlling the permissions
 * associated with different user roles or types. It allows for the management of permissions and
 * their assignment to individual users or groups of users.
 * </p>
 *
 * <p>
 * Note that this is a standalone class, and you can access its functionality by using the static
 * instance returned by the {@link #getInstance()} method. If an instance does not exist, the
 * method will create a new one.
 * </p>
 *
 * <p>
 * In this context, a "permission" refers to a specific action or operation that a user is
 * authorized to perform within the system. Permissions are defined by the {@link PermissionType}
 * enumeration, which provides a set of predefined permission types. Each permission type represents
 * a distinct action or set of actions that can be granted or denied to a user.
 * </p>
 *
 * @see User
 * @see PermissionType
 */
public class PermissionService {
    private static PermissionService instance;

    /**
     * This method returns an instance of this class.
     * It is stored in a static field called {@code instance}.
     * <p>
     * Note that if the current instance is null,
     * it'll automatically create a new instance of this class and
     * therefore the returned value will never be {@code null}.
     *
     * @return an instance of this class.
     */
    public static @NotNull PermissionService getInstance() {
        if (instance == null) {
            instance = new PermissionService();
        }

        return instance;
    }

    public boolean mayManageServices(User user, Server server) {
        return ServerProperty.SCORE_MANAGEMENT_ROLE
                .getValue(server.getId())
                .flatMap(server::getRoleById)
                .map(role -> role.hasUser(user))
                .orElseGet(() -> server.getAllowedPermissions(user).contains(PermissionType.ADMINISTRATOR));
    }
}