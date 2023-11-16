package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.exceptions.UnknownCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.List;

//TODO implement
@CommandParameters(name = "role-check", description = "See for which roles you have all requirements.")
public class RolecheckCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        throw new UnknownCommandException();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        return List.of(ignOption);
    }
}