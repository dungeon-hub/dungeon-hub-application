package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;

@CommandParameters(name = "calc-price",
                   description = "Calculate the price for some amount of carries.",
                   enabledInDms = true)
public class CalcPriceCommand extends Command
{
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        //TODO implement

        throw new UnknownCommandException();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions()
    {
        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you want.")
                .setLongMaxValue(200)
                .setLongMinValue(0)
                .setRequired(true)
                .build();

        SlashCommandOption typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("The type of the carry you want.")
                .setRequired(false)
                .setAutocompletable(true)
                .build();

        return Arrays.asList(amountOption, typeOption);
    }
}