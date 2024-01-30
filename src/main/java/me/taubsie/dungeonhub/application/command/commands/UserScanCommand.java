package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.MissingPermissionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.ProfileModerationService;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.ActionRowBuilder;
import org.javacord.api.entity.message.component.ButtonBuilder;
import org.javacord.api.entity.message.component.ButtonStyle;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.ButtonInteraction;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;

import java.util.*;
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

        InteractionOriginalResponseUpdater updater = slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater().join();

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

        updater.addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.NEGATIVE.getColor())
                        .setDescription((ban ? "Banned" : "Flagged")
                                + ":\n" + result.entrySet()
                                .stream()
                                .map(userStringEntry ->
                                        userStringEntry.getKey().getMentionTag() + " | " + userStringEntry.getValue())
                                .collect(Collectors.joining("\n"))
                                + "\n\nExcluded:\n" +
                                excluded.entrySet()
                                        .stream()
                                        .map(userStringEntry ->
                                                userStringEntry.getKey().getMentionTag() + " | " + userStringEntry.getValue())
                                        .collect(Collectors.joining("\n"))));

                updater.addComponents(new ActionRowBuilder().addComponents(
                        new ButtonBuilder().setStyle(ButtonStyle.DANGER)
                                .setLabel("Show " + ((ban) ? "banned" : "flagged") + " users")
                                .setCustomId("show_flagged_banned")
                                .build(),
                        new ButtonBuilder().setStyle(ButtonStyle.SECONDARY)
                                .setLabel("Show excluded users")
                                .setCustomId("show_excluded")
                                .build()
                ).build());

        Message sentMessage = updater.update().join();

        sentMessage.addButtonClickListener(event -> {
            event.getButtonInteractionWithCustomId("show_flagged_banned").ifPresent(buttonInteraction -> respondWithUsers(buttonInteraction, result.keySet(), server));

            event.getButtonInteractionWithCustomId("show_excluded").ifPresent(buttonInteraction -> respondWithUsers(buttonInteraction, excluded.keySet(), server));
        });
    }

    //TODO test
    //TODO maybe implement more logic to this idk?
    private void respondWithUsers(ButtonInteraction buttonInteraction, Set<User> users, Server server) {
        String content = users.stream().map(DiscordEntity::getIdAsString).collect(Collectors.joining(","));

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setDescription(users.stream().map(user -> user.getMentionTag() + " | " + user.getDisplayName(server)).collect(Collectors.joining("\n")));

        buttonInteraction.createImmediateResponder()
                .setContent(content)
                .addEmbed(embed)
                .setFlags(MessageFlag.EPHEMERAL)
                .respond();
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