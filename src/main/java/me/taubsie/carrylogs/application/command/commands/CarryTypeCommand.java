package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryType;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

//TODO make it so that carry type identifier can't start with either event or alltime
@CommandParameters(name = "carry-type", description = "Set up the carry types for this server.",
        enabledForPermissions = PermissionType.ADMINISTRATOR)
public class CarryTypeCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption subCommand = getOptionAtIndex(0);

        switch(subCommand.getName().toLowerCase()) {
            case "create" -> create(subCommand);
            case "delete" -> delete(subCommand);
            case "edit" -> edit(subCommand);
            case "get" -> get(subCommand);
            default -> throw new InvalidSubCommandException();
        }
    }

    public void create(SlashCommandInteractionOption subCommand) {
        Server server = getServer();
        String identifier = getStringOption(subCommand, "identifier");
        long logChannel = getChannelOption(subCommand, "log-channel").getId();

        //TODO custom method in ConnectionService for that check
        if(DungeonHubConnection.getInstance()
                .loadCarryTypesForServer(server.getId())
                .stream()
                .anyMatch(carryType -> carryType.getIdentifier().equalsIgnoreCase(identifier))) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "That carry type already exists!";
                }
            };
        }

        //TODO add response
        DungeonHubConnection.getInstance().addNewCarryType(server.getId(), identifier, logChannel);
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        Server server = getServer();
        String identifier = getStringOption(subCommand, "identifier");

        //TODO custom method in ConnectionService for that check
        if(DungeonHubConnection.getInstance()
                .loadCarryTypesForServer(server.getId())
                .stream()
                .noneMatch(carryType -> carryType.getIdentifier().equalsIgnoreCase(identifier))) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "That carry type doesn't exists!";
                }
            };
        }

        //TODO add response
        DungeonHubConnection.getInstance().removeCarryType(server.getId(), identifier);
    }

    public void edit(SlashCommandInteractionOption subCommand) {
        //TODO implement
    }

    public void get(SlashCommandInteractionOption subCommand) {
        Optional<CarryType> carryType = DungeonHubConnection.getInstance().loadCarryType(getServer().getId(), getStringOption(subCommand, "carry-type"));

        if(carryType.isEmpty()) {
            //TODO custom exception class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Carry type not found.";
                }
            };
        }

        respondEphemeral(ApplicationService.getInstance().getCarryTypeEmbed(carryType.get()));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption identifierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("identifier")
                .setDescription("The identifier of the carry type")
                .setRequired(true)
                .setMaxLength(30)
                .build();

        //TODO add auto completion
        SlashCommandOption carryTypeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("carry-type")
                .setDescription("The identifier of the carry type")
                .setRequired(true)
                .setMaxLength(30)
                .build();

        SlashCommandOption logChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("log-channel")
                .setDescription("Set the channel that will be used for logging")
                .setRequired(true)
                .build();

        SlashCommandOption deleteCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("delete")
                .setDescription("Delete a carry type")
                .setOptions(List.of(carryTypeOption))
                .build();

        SlashCommandOption createCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("create")
                .setDescription("Create a new carry type")
                .setOptions(List.of(identifierOption, logChannelOption))
                .build();

        SlashCommandOption getCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Get information about a carry type")
                .setOptions(List.of(carryTypeOption))
                .build();

        //TODO add options to edit command
        SlashCommandOption editCommand = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND_GROUP)
                .setName("edit")
                .setDescription("Edit a carry type")
                .build();

        return List.of(createCommand, deleteCommand, getCommand, editCommand);
    }
}