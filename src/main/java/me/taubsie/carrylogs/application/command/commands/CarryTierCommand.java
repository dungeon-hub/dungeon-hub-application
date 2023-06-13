package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "carry-tier", description = "Set up the carry tiers for this server.")
public class CarryTierCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption subCommand = getOptionAtIndex(0);

        switch(subCommand.getName().toLowerCase()) {
            case "create" -> create(subCommand);
            case "delete" -> delete(subCommand);
            case "add" -> add(subCommand);
            case "edit" -> edit(subCommand);
            case "remove" -> remove(subCommand);
            default -> throw new InvalidSubCommandException();
        }
    }

    public void create(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void edit(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void add(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void remove(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption carryTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-type")
                .setDescription("The carry type this belongs to")
                .setRequired(true)
                .build();

        SlashCommandOption identifierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("identifier")
                .setDescription("The identifier of the carry tier")
                .setRequired(true)
                .build();

        SlashCommandOption deleteCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("delete")
                .setDescription("Delete a carry tier")
                .setOptions(List.of(carryTypeOption, identifierOption))
                .build();

        SlashCommandOption createCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("create")
                .setDescription("Create a new carry tier")
                .setOptions(List.of(carryTypeOption, identifierOption))
                .build();

        //TODO add options to edit command
        SlashCommandOption editCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND_GROUP)
                .setName("edit")
                .setDescription("Edit a carry tier")
                .setOptions(List.of(carryTypeOption, identifierOption))
                .build();
        SlashCommandOption addCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND_GROUP)
                .setName("add")
                .setDescription("Add a value to a carry tier")
                .setOptions(List.of(carryTypeOption, identifierOption))
                .build();
        SlashCommandOption removeCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND_GROUP)
                .setName("edit")
                .setDescription("Remove a value to a carry tier")
                .setOptions(List.of(carryTypeOption, identifierOption))
                .build();

        return List.of(createCommand, deleteCommand, editCommand, addCommand, removeCommand);
    }
}