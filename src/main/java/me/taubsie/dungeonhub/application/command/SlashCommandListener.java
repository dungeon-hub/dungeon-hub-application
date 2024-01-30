package me.taubsie.dungeonhub.application.command;

import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.UnknownCommandException;
import me.taubsie.dungeonhub.application.listener.Listener;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.util.Optional;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class SlashCommandListener implements SlashCommandCreateListener {
    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent) {
        try {
            Optional<Command> command = ApplicationService.getInstance().getCommand(slashCommandCreateEvent);

            if (command.isEmpty()) {
                throw new UnknownCommandException();
            }

            command.get().execute(slashCommandCreateEvent);
        }
        catch (CommandExecutionException commandExecutionException) {
            ApplicationService.getInstance().respondWithError(slashCommandCreateEvent.getInteraction(), commandExecutionException);
        }
    }
}