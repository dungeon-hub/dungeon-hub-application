package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleGroupConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import me.taubsie.dungeonhub.common.model.discord_role_group.DiscordRoleGroupModel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.*;
import java.util.stream.Collectors;

public class RolesService {
    private static RolesService instance;

    public static RolesService getInstance() {
        if (instance == null) {
            instance = new RolesService();
        }

        return instance;
    }

    public void updateRoles(User user) {
        user.getMutualServers().forEach(server -> updateRoles(user, server));
    }

    public void updateRoles(User user, Server server) {
        List<Role> newRoles = RolesService.getInstance().calculateRoles(user, server);

        server.updateRoles(user, newRoles);
    }

    public List<Role> calculateRoles(User user, Server server) {
        Map<Long, DiscordRoleModel> serverRoles = DiscordRoleConnection.getInstance(server.getId())
                .getAllRoles()
                .orElse(new ArrayList<>())
                .stream()
                .collect(Collectors.toMap(DiscordRoleModel::getId, discordRoleModel -> discordRoleModel));

        Set<Role> discordRoles = new HashSet<>(server.getRoles(user));

        boolean isVerified = DiscordUserConnection.getInstance().getLinkedById(user.getId()).isPresent();
        List<Role> verifiedRoles = serverRoles.values().stream()
                .filter(DiscordRoleModel::isVerifiedRole)
                .map(DiscordRoleModel::getId)
                .map(server::getRoleById)
                .flatMap(Optional::stream)
                .toList();

        if (isVerified) {
            discordRoles.addAll(verifiedRoles);
        } else {
            verifiedRoles.forEach(discordRoles::remove);
        }

        int lastRoles = 0;
        while (lastRoles != discordRoles.size()) {
            lastRoles = discordRoles.size();
            discordRoles = applyRoleGroups(server, discordRoles);
        }

        return List.copyOf(discordRoles);
    }

    public Set<Role> applyRoleGroups(Server server, Set<Role> roles) {
        List<DiscordRoleGroupModel> roleGroups = DiscordRoleGroupConnection.getInstance(server.getId())
                .getAll()
                .orElse(new ArrayList<>());

        int lastRoles = 0;
        while (lastRoles != roles.size()) {
            lastRoles = roles.size();

            roles = applyRoleGroups(server, roles, roleGroups);
        }

        return roles;
    }

    public Set<Role> applyRoleGroups(Server server, Set<Role> roles, List<DiscordRoleGroupModel> roleGroups) {
        roleGroups.stream()
                .filter(discordRoleGroupModel -> roles.stream().anyMatch(role -> role.getId() == discordRoleGroupModel.getDiscordRole().getId()))
                .map(DiscordRoleGroupModel::getRoleGroup)
                .map(DiscordRoleModel::getId)
                .map(server::getRoleById)
                .flatMap(Optional::stream)
                .forEach(roles::add);

        Map<Long, List<DiscordRoleModel>> roleModels = List.copyOf(roleGroups).stream()
                .collect(Collectors.toMap(
                        discordRoleGroupModel -> discordRoleGroupModel.getRoleGroup().getId(),
                        discordRoleGroupModel -> new ArrayList<>(List.of(discordRoleGroupModel.getDiscordRole())),
                        (o, o2) -> {
                            o.addAll(o2);
                            return o;
                        }
                ));

        int rolesBefore = 0;
        while (rolesBefore != roles.size()) {
            rolesBefore = roles.size();

            for (Map.Entry<Long, List<DiscordRoleModel>> entry : roleModels.entrySet()) {
                if (roles.stream().anyMatch(role -> role.getId() == entry.getKey())
                        && entry.getValue().stream()
                        .allMatch(discordRoleModel -> roles.stream()
                                .noneMatch(role -> role.getId() == discordRoleModel.getId()))) {
                    roles.removeIf(role -> role.getId() == entry.getKey());
                }
            }
        }

        return roles;
    }
}