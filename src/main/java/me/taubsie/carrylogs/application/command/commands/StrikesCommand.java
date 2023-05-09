package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.dungeonhub.common.StrikeData;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "strikes", description = "See your strikes.")
public class StrikesCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        User userToCheck;
        try {
            userToCheck = getUserOption("user");
        }
        catch(InvalidOptionException invalidOptionException) {
            userToCheck = getUser();
        }

        List<StrikeData> strikeData = ConnectionService.getInstance().loadValidStrikeData(getServer().getId(),
                userToCheck.getId());

        respondEphemeral(ApplicationService.getInstance().formatStrikes(strikeData, userToCheck));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption userOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.USER)
                .setName("user")
                .setDescription("The user to get the strikes of")
                .setRequired(false)
                .build();

        return List.of(userOption);
    }
}