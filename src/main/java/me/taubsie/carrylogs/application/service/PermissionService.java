package me.taubsie.carrylogs.application.service;

import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Set;

public class PermissionService {
    private static PermissionService instance;

    public static PermissionService getInstance() {
        if(instance == null) {
            instance = new PermissionService();
        }

        return instance;
    }

    public boolean mayDiscardOthers(User user, Server server)
    {
        Set<PermissionType> allowedPermissions = server.getAllowedPermissions(user);

        return allowedPermissions.contains(PermissionType.ADMINISTRATOR)
                || allowedPermissions.contains(PermissionType.MANAGE_SERVER)
                || allowedPermissions.contains(PermissionType.MANAGE_MESSAGES);
    }

    public boolean mayManageScore(User user, Server server)
    {
        Set<PermissionType> allowedPermissions = server.getAllowedPermissions(user);

        return allowedPermissions.contains(PermissionType.ADMINISTRATOR)
                || allowedPermissions.contains(PermissionType.MANAGE_SERVER)
                || allowedPermissions.contains(PermissionType.MANAGE_MESSAGES);
    }
}