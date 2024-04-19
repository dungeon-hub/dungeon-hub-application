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
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Service class for managing nicknames.
 *
 * <p>The {@code NicknameService} class provides a singleton instance through the {@link #getInstance()} method.
 * It ensures that only one instance of the service is created and returned.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>
 * {@code
 * NicknameService nicknameService = NicknameService.getInstance();
 * }
 * </pre>
 */
public class NicknameService {
    private static NicknameService instance;

    /**
     * Retrieves the singleton instance of the {@code NicknameService}.
     *
     * <p>The {@code getInstance} method ensures that only one instance of the {@code NicknameService} is created
     * and returned. If the instance does not exist, a new one is created and returned.</p>
     *
     * @return the singleton instance of the {@code NicknameService}
     */
    public static @NotNull NicknameService getInstance() {
        return Objects.requireNonNullElseGet(instance, () -> {
            instance = new NicknameService();
            return instance;
        });
    }

    /**
     * Validates a Discord role using the provided map of Discord role models.
     *
     * <p>The {@code validateRole} method returns a {@link Predicate} that can be used to check if a given {@link Role} is valid
     * based on the information stored in the provided map of Discord role models. The validation checks include ensuring that
     * the role is present in the map, the associated DiscordRoleModel has a non-null name schema, and the name schema is not blank.</p>
     *
     * @param discordRoles the map of Discord role models to validate roles against
     * @return a {@link Predicate} that can be used to validate roles
     * @throws NullPointerException if the specified map of Discord role models is `null`
     */
    @Contract(pure = true, value = "_ -> new")
    private static @NotNull Predicate<Role> validateRole(@NotNull Map<Long, DiscordRoleModel> discordRoles) {
        return (role) -> {
            DiscordRoleModel obj = discordRoles.get(role.getId());
            return Objects.nonNull(obj) && Objects.nonNull(obj.getNameSchema()) && !obj.getNameSchema().isBlank();
        };
    }

    /**
     * Retrieves the Discord role model for the first valid role in the provided list based on the server's role models.
     *
     * <p>The {@code getRoleModel} method takes a Discord server and a list of roles as input, retrieves the associated role models
     * for the server, and finds the first valid role in the list using the {@link #validateRole(Map)} predicate. If a valid role is
     * found, the corresponding DiscordRoleModel is returned; otherwise, a {@link NoNameSchemaException} is thrown.</p>
     *
     * @param server the Discord server for which to retrieve role models and validate roles
     * @param roles  the list of roles to be validated and for which to find the corresponding role model
     * @return the DiscordRoleModel of the first valid role in the list
     * @throws NoNameSchemaException if no valid role with a non-blank name schema is found
     * @throws NullPointerException  if the server or roles are `null`
     */
    @Contract(pure = true)
    private static @NotNull DiscordRoleModel getRoleModel(@NotNull Server server, @NotNull List<Role> roles) {
        Map<Long, DiscordRoleModel> discordRoles = getRoleModels(server);
        Function<Role, DiscordRoleModel> toModel = role -> discordRoles.get(role.getId());
        Optional<Role> roleOptional = roles.parallelStream().filter(validateRole(discordRoles)).findFirst();
        return roleOptional.map(toModel).orElseThrow(NoNameSchemaException::new);
    }

    /**
     * Retrieves the Discord role models for the specified server and converts them into a map.
     *
     * <p>The {@code getRoleModels} method retrieves the Discord role models associated with the provided server
     * using the {@link DiscordRoleConnection} and converts them into a map using the role IDs as keys.</p>
     *
     * @param server the Discord server for which to retrieve role models
     * @return a map of Discord role models with role IDs as keys
     * @throws NullPointerException if the specified server is `null`
     */
    @Contract(pure = true)
    private static Map<Long, DiscordRoleModel> getRoleModels(@NotNull Server server) {
        DiscordRoleConnection connection = DiscordRoleConnection.getInstance(server.getId());
        List<DiscordRoleModel> allRoles = connection.getAllRoles().orElse(Collections.emptyList());
        return allRoles.stream().collect(toMap());
    }

    /**
     * Collects Discord role models into a map using role IDs as keys.
     *
     * <p>The {@code toMap} method returns a {@link Collector} that can be used to collect a stream of
     * {@link DiscordRoleModel} instances into a map using their role IDs as keys.</p>
     *
     * @return a {@link Collector} that collects Discord role models into a map
     */
    @Contract(pure = true, value = "-> new")
    private static @NotNull Collector<DiscordRoleModel, ?, Map<Long, DiscordRoleModel>> toMap() {
        return Collectors.toMap(DiscordRoleModel::getId, discordRoleModel -> discordRoleModel);
    }

    public UUID linkToIgn(String ign, @NotNull User user) throws CommandExecutionException {
        UUID uuid = MojangConnection.getInstance().getUUIDByName(ign);

        Optional<String> hypixelName = HypixelConnection.getInstance().getHypixelLinkedDiscord(uuid);
        String username = user.getDiscriminator().equals("0") ? user.getName() : user.getDiscriminatedName();

        if (hypixelName.isEmpty()) {
            throw new InvalidOptionException("ign", "Please add the correct discord-account (`" + user.getName() + "`) to your hypixel social menu.\n" + "To learn more about how to do this, use `/help verification`.");
        }

        if (!hypixelName.get().equalsIgnoreCase(username)) {
            throw new HypixelLinkedToOtherException(ign, hypixelName.get(), user.getName());
        }

        DiscordUserUpdateModel updateModel = new DiscordUserUpdateModel(uuid);

        DiscordUserModel userModel = DiscordUserConnection.getInstance().updateUser(user.getId(), updateModel).orElseThrow(() -> new CommandExecutionException("Couldn't update your user data."));

        return userModel.getMinecraftId();
    }

    /**
     * Updates the nickname of the specified user on all mutual servers.
     *
     * <p>The {@code updateNickname} method without the server parameter iterates over all mutual servers of the user and calls
     * the {@link #updateNickname(User, Server, List)} method for each server.</p>
     *
     * @param user the Discord user for whom to update the nickname on all mutual servers
     * @throws NoNameSchemaException if no valid role with a non-blank name schema is found while updating the nickname
     * @throws NullPointerException  if the user is `null`
     */
    public void updateNickname(@NotNull User user, @Nullable Map<Long, List<Role>> roles) {
        user.getMutualServers().forEach(server -> {
            List<Role> serverRoles = roles != null ? roles.getOrDefault(server.getId(), null) : null;

            try {
                updateNickname(user, server, serverRoles);
            }
            catch (NoNameSchemaException ignored) {
                //ignored, just don't set a username
            }
        });
    }

    /**
     * Updates the nickname of the specified user on the given server.
     *
     * <p>The {@code updateNickname} method with the server parameter retrieves the Discord user model from the connection,
     * validates the user's link status, and then calls the {@link #updateNickname(User, DiscordUserModel, Server, List)} method.</p>
     *
     * @param user   the Discord user for whom to update the nickname
     * @param server the Discord server where the nickname should be updated
     * @throws NoNameSchemaException if no valid role with a non-blank name schema is found while updating the nickname
     * @throws NotLinkedException    if the user is not linked to a Minecraft account
     * @throws NullPointerException  if the user or server is `null`
     */
    public void updateNickname(@NotNull User user, Server server, @Nullable List<Role> serverRoles) throws NoNameSchemaException, NotLinkedException {
        DiscordUserModel discordUserModel = DiscordUserConnection.getInstance().getById(user.getId()).filter(discordUserModel1 -> discordUserModel1.getMinecraftId() != null).orElseThrow(NotLinkedException::new);
        updateNickname(user, discordUserModel, server, serverRoles);
    }

    /**
     * Updates the nickname of the specified user on the given server based on the Discord user model.
     *
     * <p>The {@code updateNickname} method takes a Discord user, a Discord user model, and a server as input. It retrieves
     * the roles of the user on the server, sorts them by position in descending order, and then determines the appropriate
     * Discord role model using the {@link #getRoleModel(Server, List)} method. Finally, it updates the user's nickname on
     * the server by loading the username from the role's name schema and the provided Discord user model.</p>
     *
     * @param user             the Discord user for whom to update the nickname
     * @param discordUserModel the Discord user model providing additional information
     * @param server           the Discord server where the nickname should be updated
     * @throws NoNameSchemaException if no valid role with a non-blank name schema is found while determining the role model
     */
    public void updateNickname(@NotNull User user, @NotNull DiscordUserModel discordUserModel, @NotNull Server server, @Nullable List<Role> serverRoles) throws NoNameSchemaException {
        List<Role> roles = new ArrayList<>(serverRoles != null ? serverRoles : server.getRoles(user));
        roles.sort(Comparator.comparingInt(Role::getPosition).reversed());

        DiscordRoleModel role = getRoleModel(server, roles);

        String nickname = loadUsername(role.getNameSchema(), new PlayerInformation(user, discordUserModel));

        if (nickname.isBlank()) {
            return;
        }

        server.updateNickname(user, nickname);
    }

    public String loadUsername(String nameSchema, @NotNull PlayerInformation playerInformation) {
        Map<String, Supplier<String>> replacements = playerInformation.getReplacements();

        String regex = "(\\{[^}]+})";
        StringBuilder usernameBuilder = new StringBuilder();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(nameSchema);

        while (matcher.find()) {
            String argument = matcher.group(1);

            Supplier<String> repString = replacements.get(argument.substring(1, argument.length() - 1));
            if (repString != null) {
                matcher.appendReplacement(usernameBuilder, repString.get());
            }
        }
        matcher.appendTail(usernameBuilder);

        return usernameBuilder.toString().strip();
    }
}