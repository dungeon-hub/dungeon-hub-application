package me.taubsie.carrylogs.application.command.commands;

import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

public class CarryDifficultyCommand {
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