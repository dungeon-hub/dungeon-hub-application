package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.exceptions.NoNameSchemaException;
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@CommandParameters(name = "load-ign", description = "Loads the IGN of all users on the server.", enabledForUsers =
        {356134481452597250L})
public class LoadIgnCommand extends Command {
    private static final Map<Long, String> fetchingUsers = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Long, UUID> users = Collections.synchronizedMap(new HashMap<>());
    private static Long serverId;

    static {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchingWave();

                loadingWave();
            }
        }, new Time(System.currentTimeMillis() + 500L), 3000L);
    }

    public static synchronized void fetchingWave() {
        if (serverId == null) {
            return;
        }

        Optional<Server> server = DiscordConnection.getInstance().getBot().getServerById(serverId);

        if (server.isEmpty()) {
            return;
        }

        List<Map.Entry<Long, String>> currentWave = fetchingUsers.entrySet().stream().limit(10).toList();

        currentWave.forEach(entry -> {
            try {
                users.put(entry.getKey(), MojangConnection.getInstance().getUUIDByName(entry.getValue()));
            }
            catch (PlayerNotFoundException playerNotFoundException) {
                //ignored since we can just ignore players we didn't find
            }
        });

        currentWave.stream().map(Map.Entry::getKey).forEach(fetchingUsers::remove);
    }

    public static synchronized void loadingWave() {
        List<Map.Entry<Long, UUID>> currentWave = users.entrySet().stream().limit(5).toList();

        currentWave.forEach(entry -> {
            Optional<String> hypixelName = HypixelConnection.getInstance().getHypixelLinkedDiscord(entry.getValue());

            if (hypixelName.isEmpty()) {
                return;
            }

            User user = DiscordConnection.getInstance()
                    .getBot()
                    .getUserById(entry.getKey()).join();

            String username = user.getDiscriminator().equals("0") ? user.getName() : user.getDiscriminatedName();

            if (!hypixelName.get().equalsIgnoreCase(username)) {
                return;
            }

            DiscordUserUpdateModel updateModel = new DiscordUserUpdateModel(entry.getValue());

            DiscordUserConnection.getInstance().updateUser(user.getId(), updateModel);

            RolesService.getInstance().updateRoles(user);

            try {
                NicknameService.getInstance().updateNickname(user);
            }
            catch (NoNameSchemaException ignored) {
                //this just means there shouldn't be a nickname, then just ignore that
            }
        });

        currentWave.stream().map(Map.Entry::getKey).forEach(users::remove);
    }

    @Override
    public long[] getEnabledServers() {
        return new long[]{1023684107877761196L, 693263712626278553L};
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption progressOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("progress")
                .setDescription("Shows the progress.")
                .build();

        SlashCommandOption startOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("start")
                .setDescription("Starts the loading.")
                .build();

        return List.of(progressOption, startOption);
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        boolean show = slashCommandCreateEvent.getSlashCommandInteraction()
                .getOptionByIndex(0)
                .map(SlashCommandInteractionOption::getName)
                .map(s -> s.equalsIgnoreCase("progress"))
                .orElse(false);

        if (show) {
            respond(ApplicationService.getInstance()
                    .getEmbed()
                    .setDescription("Progress: " + fetchingUsers.size() + " users to fetch and " + users.size() + " users to load left."));
            return;
        }

        CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();

        respondLaterEphemeral(completableFuture);

        Server server = getServer();

        serverId = server.getId();

        Map<Long, DiscordUserModel> discordUsers = DiscordUserConnection.getInstance()
                .getAll()
                .orElse(new ArrayList<>())
                .stream().collect(Collectors.toMap(DiscordUserModel::getId, discordUserModel -> discordUserModel));

        Map<Long, String> usersToFetch = server.getMembers().parallelStream()
                .filter(user -> !user.isBot())
                .filter(user -> !discordUsers.containsKey(user.getId()) || discordUsers.get(user.getId()).getMinecraftId() == null)
                .collect(Collectors.toMap(
                        DiscordEntity::getId,
                        o -> o.getDisplayName(server)
                                .replaceAll("❮(\\S*)❯", "")
                                .replaceAll("\\[(\\S*)]", "")
                                .replace("★", "")
                                .replace("✦", "")
                                .replace("✶", "")
                                .replace("✽", "")
                                .replace("❊", "")
                                .replace("✯", "")
                                .replace("✩", "")
                                .strip()
                ));

        System.out.println("★");

        fetchingUsers.putAll(usersToFetch);

        String description = fetchingUsers.entrySet().stream()
                .map(entry -> "<@" + entry.getKey() + ">: `" + entry.getValue() + "`")
                .collect(Collectors.joining("\n"));

        if (description.length() >= 4000) {
            description = "The list of igns to fetch would be too long.\n"
                    + ContentConnection.getInstance()
                    .uploadFile(description.getBytes(StandardCharsets.UTF_8))
                    .map(s -> "https://cdn.dungeon-hub.net/" + s)
                    .orElse("The full list has been logged, contact administrators for more information.");
        }

        completableFuture.complete(
                DelayedResponse.fromEmbed(
                        ApplicationService.getInstance()
                                .getEmbed()
                                .setDescription(description))
        );
    }
}