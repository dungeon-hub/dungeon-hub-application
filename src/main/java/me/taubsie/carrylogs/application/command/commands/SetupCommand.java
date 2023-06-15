package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

@CommandParameters(name = "setup", description = "Shows you how to setup the bot.", enabledInDms = true)
public class SetupCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        //TODO implement
        throw new UnknownCommandException();
    }
}