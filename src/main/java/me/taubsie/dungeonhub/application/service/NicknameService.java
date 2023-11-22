package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.classes.PlayerInformation;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordRoleConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.exceptions.*;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.server.ServerUpdater;
import org.javacord.api.entity.user.User;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NicknameService {
    private static NicknameService instance;

    public static NicknameService getInstance() {
        if (instance == null) {
            instance = new NicknameService();
        }

        return instance;
    }

    public UUID linkToIgn(String ign, User user) throws CommandExecutionException {
        UUID uuid = MojangConnection.getInstance().getUUIDByName(ign);

        Optional<String> hypixelName = HypixelConnection.getInstance().getHypixelLinkedDiscord(uuid);
        String username = user.getDiscriminator().equals("0") ? user.getName() : user.getDiscriminatedName();

        if (hypixelName.isEmpty()) {
            throw new InvalidOptionException("ign",
                    "Please add the correct discord-account to your hypixel social menu.");
        }

        if(!hypixelName.get().equalsIgnoreCase(username)) {
            throw new HypixelLinkedToOtherException();
        }

        DiscordUserUpdateModel updateModel = new DiscordUserUpdateModel(uuid);

        DiscordUserModel userModel = DiscordUserConnection.getInstance()
                .updateUser(user.getId(), updateModel)
                .orElseThrow(() -> new CommandExecutionException() {
                    @Override
                    public String getMessage() {
                        return "Couldn't update your user data.";
                    }
                });

        return userModel.getMinecraftId();
    }

    public void updateNickname(User user) {
        user.getMutualServers().forEach(server -> updateNickname(user, server));
    }

    public void updateNickname(User user, Server server) {
        ServerUpdater serverUpdater = server.createUpdater();

        updateNickname(user, server, serverUpdater);

        serverUpdater.update();
    }

    public void updateNickname(User user, Server server, ServerUpdater serverUpdater) throws NoNameSchemaException,
            NotLinkedException {
        DiscordUserModel discordUserModel = DiscordUserConnection.getInstance()
                .getById(user.getId())
                .filter(discordUserModel1 -> discordUserModel1.getMinecraftId() != null)
                .orElseThrow(NotLinkedException::new);

        updateNickname(user, discordUserModel, server, serverUpdater);
    }

    public void updateNickname(User user, DiscordUserModel discordUserModel, Server server,
                               ServerUpdater serverUpdater) throws NoNameSchemaException {
        Map<Long, DiscordRoleModel> discordRoles = DiscordRoleConnection.getInstance(server.getId())
                .getAllRoles()
                .orElse(new ArrayList<>()).stream()
                .collect(Collectors.toMap(DiscordRoleModel::getId, discordRoleModel -> discordRoleModel));

        List<Role> roles = server.getRoles(user);

        DiscordRoleModel role = roles.parallelStream()
                .sorted(Comparator.comparingInt(Role::getPosition).reversed())
                .filter(role1 -> discordRoles.containsKey(role1.getId())
                        && discordRoles.get(role1.getId()).getNameSchema() != null
                        && !discordRoles.get(role1.getId()).getNameSchema().isBlank())
                .findFirst()
                .map(role1 -> discordRoles.get(role1.getId()))
                .orElseThrow(NoNameSchemaException::new);

        String nameSchema = role.getNameSchema();

        String nickname = loadUsernameByPlayerInformation(nameSchema, new PlayerInformation(user, discordUserModel));

        serverUpdater.setNickname(user, nickname);
    }

    public String loadUsernameByPlayerInformation(String nameSchema, PlayerInformation playerInformation) {
        Map<String, Supplier<String>> replacements = playerInformation.getReplacements();

        String regex = "(\\{[^}]+})";
        StringBuilder usernameBuilder = new StringBuilder();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(nameSchema);

        while(matcher.find()) {
            String argument = matcher.group(1);

            Supplier<String> repString = replacements.get(argument.substring(1, argument.length() - 1));
            if (repString != null) {
                matcher.appendReplacement(usernameBuilder, repString.get());
            }
        }
        matcher.appendTail(usernameBuilder);

        return usernameBuilder.toString();
    }
}