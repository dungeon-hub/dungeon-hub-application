package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ProfileModerationService;
import me.taubsie.carrylogs.application.start.BotStarter;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@CommandParameters(name = "ban-all", description = "Uses the profile moderation to ban all given users.", enabledForPermissions = PermissionType.BAN_MEMBERS)
public class BanAllCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String users = getStringOption("users");
        String reason = getStringOption("reason");

        String[] userArray = users.split(",");
        List<String> errors = new ArrayList<>();

        if(userArray.length <= 1) {
            throw new InvalidOptionException("users", "Please provide a comma-separated list of users to ban.");
        }

        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() -> {
            for(String userId : userArray) {
                userId = userId.strip();

                try {
                    User user = BotStarter.getInstance()
                            .getBot()
                            .getUserById(userId)
                            .join();

                    ProfileModerationService.getInstance().handleUserBan(getServer(), user, reason);
                }
                catch(CompletionException completionException) {
                    errors.add(userId);
                }
            }

            if(errors.isEmpty()) {
                return ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.POSITIVE.getColor())
                        .setDescription("Successfully banned all " + userArray.length + " users.");
            } else {
                return ApplicationService.getInstance()
                        .getEmbed()
                        .setColor(EmbedColor.NEGATIVE.getColor())
                        .setDescription("Couldn't ban the following users:\n" + String.join(", ", errors));
            }
        }));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption usersOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("users")
                .setDescription("Comma-separated list of users to ban.")
                .setRequired(true)
                .build();

        SlashCommandOption reasonOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("reason")
                .setDescription("Reason for the ban.")
                .setRequired(true)
                .setMinLength(2)
                .build();

        return List.of(usersOption, reasonOption);
    }
}