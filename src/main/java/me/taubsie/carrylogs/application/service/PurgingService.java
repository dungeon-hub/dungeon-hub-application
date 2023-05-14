package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.classes.PurgeData;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.RoleConversion;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryRole;
import me.taubsie.dungeonhub.common.OnStart;
import me.taubsie.dungeonhub.common.ProgramOrigin;
import me.taubsie.dungeonhub.common.StartupListener;
import org.javacord.api.entity.DiscordEntity;
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
    private static PurgingService instance;
    private final List<PurgeData> purgeDataList = new ArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(PurgingService.class);

    public static PurgingService getInstance() {
        if(instance == null) {
            instance = new PurgingService();
        }

        return instance;
    }

    //TODO probably increase time to prevent it getting stuck and to have too many open threads.
    //TODO also try limiting the amount of requests of the same purge type and maybe also to the same user

    @Override
    public void onStart(ProgramOrigin programOrigin) {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                purgeWave();
            }
        }, new Time(System.currentTimeMillis() + 500L), 3000L);
    }

    /**
     * This method is private to prevent it from being run from outside this service.
     * That is done so that the amount of threads created is limited, to prevent the server this is currently hosted on from reaching the vm's thread limit.
     */
    private void purgeWave() {
        List<PurgeData> currentWave = purgeDataList.stream().limit(5).toList();

        currentWave.forEach(purgeData -> {
            Optional<Server> server = BotStarter.getInstance().getBot().getServerById(purgeData.serverId());
            User user = BotStarter.getInstance().getBot().getUserById(purgeData.userId()).join();

            if(server.isEmpty()) {
                logger.error("Server isn't a valid server for purging anymore!");
                return;
            }

            List<String> rolesRemoved = new ArrayList<>();

            for(RoleConversion carryRole : purgeData.rolesToRemove()) {
                Optional<Role> role = server
                        .map(DiscordEntity::getId)
                        .flatMap(serverId -> carryRole.getServerProperty().getValue(serverId))
                        .flatMap(s -> server.get().getRoleById(s));

                String roleName = carryRole.getCarryRole().name();

                if(role.isEmpty()) {
                    logger.error("Role {} not found on server {}.", roleName, server.get().getId());
                    return;
                }

                if(role.get().hasUser(user)) {
                    if(rolesRemoved.isEmpty()) {
                        role.get().removeUser(user, "Purge of type \"" + purgeData.purgeType() + "\" with threshold " + purgeData.purgeThreshold() + ".");
                    } else {
                        role.get().removeUser(user);
                    }

                    rolesRemoved.add(role.get().getName());
                }
            }

            List<CarryRole> roleList =
                    RoleConversion.getCarryRoles(user.getRoles(server.get()), server.get().getId()).stream().map(RoleConversion::getCarryRole).toList();
            ConnectionService.getInstance().addRoles(user.getId(), roleList);

            if(!rolesRemoved.isEmpty()) {
                try {
                    user.openPrivateChannel()
                            .thenAccept(privateChannel -> privateChannel.sendMessage(ApplicationService.getInstance()
                                    .getEmbed()
                                    .setColor(EmbedColor.NEGATIVE.getColor())
                                    .setDescription("Your " + purgeData.purgeType() + "-carry roles on `" + server.get().getName()
                                            + "` were removed since you only reached " + purgeData.score() + "/"
                                            + purgeData.purgeThreshold() + " score.")
                                    .addField("Roles removed", String.join(System.lineSeparator(), rolesRemoved))
                                    .setTitle("Inactivity Purge")));
                }
                catch(CompletionException completionException) {
                    logger.error("Unable to DM carrier about purge.", completionException);
                }
            }
        });

        purgeDataList.removeAll(currentWave);
    }

    public void addPurgeData(PurgeData purgeData) {
        purgeDataList.add(purgeData);
    }
}