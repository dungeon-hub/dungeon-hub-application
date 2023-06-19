package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.common.CarryType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

//TODO make sure that category is unique
@CommandParameters(name = "carry-tier", description = "Set up the carry tiers for this server.")
public class CarryTierCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption subCommand = getOptionAtIndex(0);

        switch (subCommand.getName().toLowerCase()) {
            case "create" -> create(subCommand);
            case "delete" -> delete(subCommand);
            case "get" -> get(subCommand);
            case "edit" -> edit(subCommand);
            case "reset" -> reset(subCommand);
            default -> throw new InvalidSubCommandException();
        }
    }

    public void create(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void get(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void edit(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void reset(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    private Optional<CarryType> getFromInteraction(SlashCommandInteractionOption interaction) {
        return DungeonHubConnection.getInstance()
                .loadCarryType(getServer().getId(), getStringOption(interaction, "carry-type"));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        return List.of(getCreateCommand(), getDeleteCommand(), getGetCommand(), getEditCommand(), getResetCommand());
    }

    private SlashCommandOption getGetCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Get information about a carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), getCarryTierOption()))
                .build();
    }

    private SlashCommandOption getEditCommand() {
        //TODO add options to edit command
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("edit")
                .setDescription("Edit a carry tier")
                .setOptions(List.of(getCarryTierOption()))
                .build();
    }

    private SlashCommandOption getResetCommand() {
        //TODO add options to reset command
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("reset")
                .setDescription("Reset a carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), getCarryTierOption()))
                .build();
    }

    private SlashCommandOption getDeleteCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("delete")
                .setDescription("Delete a carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), getCarryTierOption()))
                .build();
    }

    private SlashCommandOption getCreateCommand() {
        SlashCommandOption identifierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("identifier")
                .setDescription("The identifier of the carry tier")
                .setRequired(true)
                .build();

        SlashCommandOption displayNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("display-name")
                .setDescription("The display name of the carry tier")
                .setRequired(true)
                .setMaxLength(30)
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("create")
                .setDescription("Create a new carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), identifierOption, displayNameOption))
                .build();
    }

    public static SlashCommandOption getCarryTierOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-tier")
                .setDescription("The identifier of the carry tier")
                .setRequired(true)
                .setMaxLength(30)
                .setAutocompletable(true)
                .build();
    }
}