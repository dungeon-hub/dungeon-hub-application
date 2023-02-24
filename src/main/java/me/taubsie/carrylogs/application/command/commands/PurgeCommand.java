package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

@CommandParameters(name = "purge",
        description = "Allows you to purge inactive carriers.",
        enabledServers = {693263712626278553L, 1023684107877761196L})
public class PurgeCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        //TODO implement
        throw new UnknownCommandException();
    }
}