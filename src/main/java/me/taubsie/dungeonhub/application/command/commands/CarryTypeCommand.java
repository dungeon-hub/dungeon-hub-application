package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeCreationModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeUpdateModel;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

@CommandParameters(name = "carry-type", description = "Set up the carry types for this server.",
        enabledForPermissions = PermissionType.ADMINISTRATOR)
public class CarryTypeCommand extends Command {
    public static final String FIELD_NAME = "carry-type";

    public static SlashCommandOption getCarryTypeOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName(FIELD_NAME)
                .setDescription("The identifier of the carry type")
                .setRequired(true)
                .setMaxLength(30)
                .setAutocompletable(true)
                .build();
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption subCommand = getOptionAtIndex(0);

        switch (subCommand.getName().toLowerCase()) {
            case "create" -> create(subCommand);
            case "delete" -> delete(subCommand);
            case "edit" -> edit(subCommand);
            case "get" -> get(subCommand);
            case "reset" -> reset(subCommand);
            default -> throw new InvalidSubCommandException();
        }
    }

    public void create(SlashCommandInteractionOption subCommand) {
        Server server = getServer();
        String identifier = getStringOption(subCommand, "identifier")
                .strip()
                .toLowerCase()
                .replace(" ", "_");
        String displayName = getStringOption(subCommand, "display-name");

        if (CarryTypeConnection.getInstance(server.getId()).getByIdentifier(identifier).isPresent()) {
            throw new InvalidOptionException("identifier", "That carry type already exists!");
        }

        CarryTypeCreationModel creationModel = new CarryTypeCreationModel(identifier, displayName);

        getOptionalChannelOption(subCommand, "log-channel")
                .ifPresent(channel -> creationModel.setLogChannel(channel.getId()));
        getOptionalChannelOption(subCommand, "leaderboard-channel")
                .ifPresent(channel -> creationModel.setLeaderboardChannel(channel.getId()));
        getOptionalBooleanOption(subCommand, "event-active")
                .ifPresent(creationModel::setEventActive);

        Optional<CarryTypeModel> carryTypeModel = CarryTypeConnection.getInstance(server.getId())
                .addNewCarryType(creationModel);

        if (carryTypeModel.isEmpty()) {
            //TODO custom class?
            throw new CommandExecutionException("Couldn't add that carry type.");
        }

        respond(ApplicationService.getInstance()
                .getCarryTypeEmbed(carryTypeModel.get())
                .setTitle("Carry Type created"));
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        String identifier = getStringOption(subCommand, FIELD_NAME);

        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(identifier);

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("That carry type doesn't exists!");
        }

        Optional<CarryTypeModel> deletedCarryType = CarryTypeConnection.getInstance(carryType.get().getServer().getId())
                .deleteCarryType(carryType.get());

        if (deletedCarryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Carry type couldn't be deleted!");
        }

        respond(ApplicationService.getInstance()
                .getCarryTypeEmbed(deletedCarryType.get())
                .setTitle("Deleted Carry Type"));
    }

    public void edit(SlashCommandInteractionOption editCommand) {
        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(getStringOption(editCommand, FIELD_NAME));

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("That carry type doesn't exists!");
        }

        Optional<String> displayName = getOptionalStringOption(editCommand, "display-name");
        Optional<ServerChannel> logChannel = getOptionalChannelOption(editCommand, "log-channel");
        Optional<ServerChannel> leaderboardChannel = getOptionalChannelOption(editCommand, "leaderboard-channel");
        Optional<Boolean> eventActive = getOptionalBooleanOption(editCommand, "event-active");

        if (displayName.isEmpty() && logChannel.isEmpty() && leaderboardChannel.isEmpty() && eventActive.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Please provide something you want to edit.");
        }

        CarryTypeUpdateModel updateModel = new CarryTypeUpdateModel();

        displayName.ifPresent(updateModel::setDisplayName);
        logChannel.map(DiscordEntity::getId).ifPresent(updateModel::setLogChannel);
        leaderboardChannel.map(DiscordEntity::getId).ifPresent(updateModel::setLeaderboardChannel);
        eventActive.ifPresent(updateModel::setEventActive);

        Optional<CarryTypeModel> updatedCarryType = CarryTypeConnection.getInstance(getServer().getId())
                .updateCarryType(carryType.get().getId(), updateModel);

        if (updatedCarryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Couldn't update carry type.");
        }

        respond(ApplicationService.getInstance()
                .getCarryTypeEmbed(updatedCarryType.get())
                .setTitle("Updated Carry Type"));
    }

    public void reset(SlashCommandInteractionOption resetCommand) {
        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(getStringOption(resetCommand, FIELD_NAME));

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("That carry type doesn't exists!");
        }

        Boolean logChannel = getBooleanOption(resetCommand, "log-channel");
        Boolean leaderboardChannel = getBooleanOption(resetCommand, "leaderboard-channel");

        if (Boolean.FALSE.equals(logChannel) && Boolean.FALSE.equals(leaderboardChannel)) {
            //TODO custom class
            throw new CommandExecutionException("Please provide something you want to reset.");
        }

        CarryTypeUpdateModel updateModel = new CarryTypeUpdateModel();

        if (Boolean.TRUE.equals(logChannel)) {
            updateModel.setLogChannel(-1L);
        }

        if (Boolean.TRUE.equals(leaderboardChannel)) {
            updateModel.setLeaderboardChannel(-1L);
        }

        Optional<CarryTypeModel> updatedCarryType = CarryTypeConnection.getInstance(getServer().getId())
                .updateCarryType(carryType.get().getId(), updateModel);

        if (updatedCarryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Couldn't update carry type.");
        }

        respond(ApplicationService.getInstance()
                .getCarryTypeEmbed(updatedCarryType.get())
                .setTitle("Updated Carry Type with reset values"));
    }

    public void get(SlashCommandInteractionOption subCommand) {
        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(getStringOption(subCommand, FIELD_NAME));

        if (carryType.isEmpty()) {
            //TODO custom exception class
            throw new CommandExecutionException("Carry type not found.");
        }

        respondEphemeral(ApplicationService.getInstance().getCarryTypeEmbed(carryType.get()));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        return List.of(getCreateCommand(), getDeleteCommand(), getGetCommand(), getEditCommand(), getResetCommand());
    }

    private SlashCommandOption getCreateCommand() {
        SlashCommandOption identifierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("identifier")
                .setDescription("The identifier of the carry type.")
                .setRequired(true)
                .setMaxLength(30)
                .build();

        SlashCommandOption displayNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("display-name")
                .setDescription("The display name of the carry type")
                .setRequired(true)
                .setMaxLength(30)
                .build();

        SlashCommandOption logChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("log-channel")
                .setDescription("Set the channel that will be used for logging")
                .setRequired(false)
                .build();

        SlashCommandOption leaderboardChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("leaderboard-channel")
                .setDescription("Set the channel that will be used to show a static leaderboard")
                .setRequired(false)
                .build();

        SlashCommandOption eventActiveOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("event-active")
                .setDescription("Set if there if an active event for score")
                .setRequired(false)
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("create")
                .setDescription("Create a new carry type")
                .setOptions(List.of(identifierOption, displayNameOption, logChannelOption, leaderboardChannelOption,
                        eventActiveOption))
                .build();
    }

    private SlashCommandOption getGetCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Get information about a carry type")
                .setOptions(List.of(getCarryTypeOption()))
                .build();
    }

    private SlashCommandOption getDeleteCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("delete")
                .setDescription("Delete a carry type")
                .setOptions(List.of(getCarryTypeOption()))
                .build();
    }

    private SlashCommandOption getEditCommand() {
        SlashCommandOption logChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("log-channel")
                .setDescription("Set the channel that will be used for logging")
                .setRequired(false)
                .build();

        SlashCommandOption leaderboardChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("leaderboard-channel")
                .setDescription("Set the channel that will be used to show a static leaderboard")
                .setRequired(false)
                .build();

        SlashCommandOption displayNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("display-name")
                .setDescription("Set the display name")
                .setRequired(false)
                .build();

        SlashCommandOption eventActiveOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("event-active")
                .setDescription("Set if there if an active event for score")
                .setRequired(false)
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("edit")
                .setDescription("Edit a carry type")
                .setOptions(List.of(getCarryTypeOption(), displayNameOption, logChannelOption,
                        leaderboardChannelOption, eventActiveOption))
                .build();
    }

    private SlashCommandOption getResetCommand() {
        SlashCommandOption logChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("log-channel")
                .setDescription("Set if the log channel should be reset")
                .setRequired(true)
                .build();

        SlashCommandOption leaderboardChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("leaderboard-channel")
                .setDescription("Set if the leaderboard channel should be reset")
                .setRequired(true)
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("reset")
                .setDescription("Reset properties of a carry type")
                .setOptions(List.of(getCarryTypeOption(), logChannelOption, leaderboardChannelOption))
                .build();
    }
}