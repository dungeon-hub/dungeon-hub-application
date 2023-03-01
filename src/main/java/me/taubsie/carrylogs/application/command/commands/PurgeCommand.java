package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

@CommandParameters(name = "purge",
        description = "Allows you to purge inactive carriers.")
public class PurgeCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        //TODO implement
        throw new UnknownCommandException();
    }
}