package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

@CommandParameters(name = "carry-difficulty", description = "Set up the carry difficulties for this server.")
public class CarryDifficultyCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        throw new UnknownCommandException();
    }

    public static SlashCommandOption getCarryDifficultyOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-difficulty")
                .setDescription("The identifier of the carry difficulty")
                .setRequired(true)
                .setMaxLength(30)
                .setAutocompletable(true)
                .build();
    }
}