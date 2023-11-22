package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.server.ServerUpdater;
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
        ServerUpdater serverUpdater = server.createUpdater();

        updateRoles(user, server, serverUpdater);

        serverUpdater.update();
    }

    public void updateRoles(User user, Server server, ServerUpdater serverUpdater) {
        List<Role> newRoles = RolesService.getInstance().calculateRoles(user, server);

        serverUpdater.removeAllRolesFromUser(user).addRolesToUser(user, newRoles);
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
        while(lastRoles != discordRoles.size()) {
            lastRoles = discordRoles.size();
            discordRoles = applyRoleGroup(serverRoles, server, discordRoles);
        }

        return List.copyOf(discordRoles);
    }

    //TODO also remove empty role groups
    public Set<Role> applyRoleGroup(Map<Long, DiscordRoleModel> serverRoles, Server server, Set<Role> roles) {
        List.copyOf(roles).stream()
                .map(DiscordEntity::getId)
                .filter(serverRoles::containsKey)
                .map(serverRoles::get)
                .map(DiscordRoleModel::getRoleGroup)
                .filter(Objects::nonNull)
                .map(server::getRoleById)
                .flatMap(Optional::stream)
                .forEach(roles::add);

        return roles;
    }
}