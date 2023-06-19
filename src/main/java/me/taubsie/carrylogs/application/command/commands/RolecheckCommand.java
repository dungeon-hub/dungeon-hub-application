package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.List;

//TODO implement
@CommandParameters(name = "rolecheck", description = "See for which roles you have all requirements.")
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