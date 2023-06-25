package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ProfileModerationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@CommandParameters(name = "userscan",
        description = "Scans for users with a bad username.",
        enabledForPermissions = {PermissionType.BAN_MEMBERS})
public class UserScanCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();

        if (!server.hasPermission(slashCommandCreateEvent.getSlashCommandInteraction().getUser(),
                PermissionType.ADMINISTRATOR)) {
            throw new MissingPermissionException();
        }

        boolean ban = slashCommandCreateEvent.getSlashCommandInteraction().getOptionByName("ban").isPresent();

        Map<User, String> result = new HashMap<>();
        Map<User, String> excluded = new HashMap<>();

        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() -> {
            for (User user : server.getMembers()) {
                if (!user.isBot()) {
                    String checkResult = ProfileModerationService.getInstance().checkUserName(user.getName());
                    if (checkResult != null) {
                        if (ProfileModerationService.getInstance().isExcluded(user, server)) {
                            excluded.put(user, checkResult);
                        } else {
                            result.put(user, checkResult);
                        }
                    }
                }
            }

            if (ban) {
                for (Map.Entry<User, String> entries : result.entrySet()) {
                    ProfileModerationService.getInstance().handleUserBan(server, entries.getKey(), entries.getValue());
                }
            }

            return ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setDescription((ban ? "Banned" : "Flagged") + ":\n" + result.entrySet()
                            .stream()
                            .map(userStringEntry ->
                                    userStringEntry.getKey().getMentionTag() + " - " + userStringEntry.getValue())
                            .collect(Collectors.joining("\n")) + "\n\nExcluded:\n" +
                            excluded.entrySet()
                                    .stream()
                                    .map(userStringEntry ->
                                            userStringEntry.getKey().getMentionTag() + " - " + userStringEntry.getValue()).collect(Collectors.joining("\n")));
        }));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption banCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("ban")
                .setDescription("Add this if flagged users should also be banned.")
                .build();

        SlashCommandOption scanCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("scan")
                .setDescription("Add this if flagged users shouldn't be banned.")
                .build();

        return Arrays.asList(banCommand, scanCommand);
    }
}