package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.classes.PurgeData;
import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.loader.OnStart;
import me.taubsie.dungeonhub.application.loader.StartupListener;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import me.taubsie.dungeonhub.common.model.purge_type.PurgeTypeModel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.util.*;
import java.util.concurrent.CompletionException;

@OnStart
public class PurgingService implements StartupListener {
    private static final Logger logger = LoggerFactory.getLogger(PurgingService.class);
    private static PurgingService instance;
    private final List<PurgeData> purgeDataList = new ArrayList<>();
    private final List<Long> purgeEnabled = new ArrayList<>();

    public static PurgingService getInstance() {
        return Objects.requireNonNullElseGet(instance, () -> {
            instance = new PurgingService();
            return instance;
        });
    }

    //TODO probably increase time to prevent it getting stuck and to have too many open threads.
    //TODO also try limiting the amount of requests of the same purge type and maybe also to the same user

    @Override
    public void onStart() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                purgeWave();
            }
        }, new Time(System.currentTimeMillis() + 500L), 3000L);
    }

    public void enablePurge(Long serverId) {
        if (!purgeEnabled.contains(serverId)) {
            purgeEnabled.add(serverId);
        }
    }

    /**
     * This method is private to prevent it from being run from outside this service.
     * That is done so that the amount of threads created is limited, to prevent the server this is currently hosted
     * on from reaching the vm's thread limit.
     */
    private void purgeWave() {
        List<PurgeData> currentWave = purgeDataList.stream()
                .filter(purgeData -> purgeEnabled.contains(purgeData.purgeType().getCarryType().getServer().getId()))
                .limit(5)
                .toList();

        purgeEnabled.removeIf(aLong -> purgeDataList.stream()
                .noneMatch(purgeData -> purgeData.purgeType().getCarryType().getServer().getId() == aLong));

        currentWave.forEach(purgeData -> {
            Optional<Server> server = DiscordConnection.getInstance().getBot().getServerById(purgeData.purgeType().getCarryType().getServer().getId());
            User user = DiscordConnection.getInstance().getBot().getUserById(purgeData.userId()).join();

            if (server.isEmpty()) {
                logger.error("Server isn't a valid server for purging anymore!");
                return;
            }

            List<String> rolesRemoved = removeRoles(purgeData.rolesToRemove(), server.get(), user,
                    purgeData.purgeType(), purgeData.purgeThreshold());

            if (!rolesRemoved.isEmpty()) {
                try {
                    user.openPrivateChannel()
                            .thenAccept(privateChannel -> privateChannel.sendMessage(ApplicationService.getInstance()
                                    .getEmbed()
                                    .setColor(EmbedColor.NEGATIVE.getColor())
                                    .setDescription("Your " + purgeData.purgeType().getDisplayName() + "-carry roles on `" + server.get().getName()
                                            + "` were removed since you only reached " + purgeData.score() + "/"
                                            + purgeData.purgeThreshold() + " score.")
                                    .addField("Roles removed", String.join(System.lineSeparator(), rolesRemoved))
                                    .setTitle("Inactivity Purge")));
                }
                catch (CompletionException completionException) {
                    logger.error("Unable to DM carrier about purge.", completionException);
                }
            }
        });

        purgeDataList.removeAll(currentWave);
    }

    private List<String> removeRoles(List<DiscordRoleModel> rolesToRemove, Server server, User user, PurgeTypeModel purgeType,
                                     long purgeThreshold) {
        List<String> rolesRemoved = new ArrayList<>();

        for (DiscordRoleModel discordRole : rolesToRemove) {
            Optional<Role> role = server.getRoleById(discordRole.getId());

            if (role.isEmpty()) {
                logger.error("Role {} not found on server {}.", discordRole.getId(), server.getId());
                continue;
            }

            if (role.get().hasUser(user)) {
                if (rolesRemoved.isEmpty()) {
                    role.get().removeUser(user,
                            "Purge of type \"" + purgeType.getDisplayName() + "\" with threshold " + purgeThreshold + ".");
                } else {
                    role.get().removeUser(user);
                }

                rolesRemoved.add(role.get().getName());
            }
        }

        return rolesRemoved;
    }

    public void addPurgeData(PurgeData purgeData) {
        purgeDataList.add(purgeData);
    }
}