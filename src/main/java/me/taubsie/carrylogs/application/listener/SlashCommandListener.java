package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.exceptions.*;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ApplicationClassLoaderService;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.awt.*;
import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class SlashCommandListener implements SlashCommandCreateListener
{
    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent) {
        Optional<Command> command = ApplicationClassLoaderService.getInstance().getCommand(
                slashCommandCreateEvent.getSlashCommandInteraction().getCommandName(),
                slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElse(null)
        );

        try {
            if(command.isEmpty()) {
                throw new UnknownCommandException();
            }

            command.get().execute(slashCommandCreateEvent);
        }
        catch (CommandExecutionException commandExecutionException)
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .addEmbed(ApplicationService.getInstance()
                            .getEmbed()
                            .setTitle("Error")
                            .setDescription(commandExecutionException.getMessage())
                            .setColor(new Color(255, 0, 0 /*TODO color*/)))
                    .respond();
        }
    }
}