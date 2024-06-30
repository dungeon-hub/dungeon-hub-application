package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTierConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierCreationModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierUpdateModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.ChannelCategory;
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

@CommandParameters(name = "carry-tier", description = "Set up the carry tiers for this server.",
        enabledForPermissions = PermissionType.ADMINISTRATOR)
public class CarryTierCommand extends Command {
    public static final String FIELD_NAME = "carry-tier";

    public static SlashCommandOption getCarryTierOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName(FIELD_NAME)
                .setDescription("The identifier of the carry tier")
                .setRequired(true)
                .setMaxLength(30)
                .setAutocompletable(true)
                .build();
    }

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
        Server server = getServer();

        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(server.getId())
                .getByIdentifier(getStringOption(subCommand, CarryTypeCommand.FIELD_NAME));

        if (carryType.isEmpty()) {
            throw new InvalidOptionException(CarryTypeCommand.FIELD_NAME, "Carry Type couldn't be found.");
        }

        String identifier = getStringOption(subCommand, "identifier")
                .strip()
                .toLowerCase()
                .replace(" ", "_");
        String displayName = getStringOption(subCommand, "display-name");

        if(CarryTierConnection.getInstance(carryType.get()).getByIdentifier(identifier).isPresent()) {
            throw new InvalidOptionException("identifier", "That carry tier already exists!");
        }

        CarryTierCreationModel creationModel = new CarryTierCreationModel();
        creationModel.setIdentifier(identifier);
        creationModel.setDisplayName(displayName);

        getOptionalStringOption(subCommand, "descriptive-name")
                .ifPresent(creationModel::setDescriptiveName);
        getOptionalChannelOption(subCommand, "category")
                .flatMap(Channel::asChannelCategory)
                .filter(channelCategory -> DiscordServerConnection.getInstance()
                        .getCarryTierFromCategory(server.getId(), channelCategory.getId()).isEmpty())
                .map(DiscordEntity::getId)
                .ifPresent(creationModel::setCategory);
        getOptionalChannelOption(subCommand, "price-channel")
                .map(DiscordEntity::getId)
                .ifPresent(creationModel::setPriceChannel);
        getOptionalStringOption(subCommand, "thumbnail-url")
                .ifPresent(creationModel::setThumbnailUrl);
        getOptionalStringOption(subCommand, "price-title")
                .ifPresent(creationModel::setPriceTitle);
        getOptionalStringOption(subCommand, "price-description")
                .ifPresent(creationModel::setPriceDescription);

        Optional<CarryTierModel> carryTier = CarryTierConnection.getInstance(carryType.get())
                .createCarryTier(creationModel);

        if (carryTier.isEmpty()) {
            //TODO custom class?
            throw new CommandExecutionException("Couldn't add that carry tier.");
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(carryTier.get())
                .setTitle("Carry Tier created"));
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        String carryTypeIdentifier = getStringOption(subCommand, CarryTypeCommand.FIELD_NAME);
        String identifier = getStringOption(subCommand, FIELD_NAME);

        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(carryTypeIdentifier);

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("That carry type doesn't exists!");
        }

        Optional<CarryTierModel> carryTier = CarryTierConnection.getInstance(carryType.get())
                .getByIdentifier(identifier);

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(FIELD_NAME);
        }

        if (!carryTier.get().getCarryType().equals(carryType.get())) {
            //TODO custom class
            throw new CommandExecutionException("Well this is weird.. Something doesn't really add up!");
        }

        Optional<CarryTierModel> deletedCarryTier = DungeonHubConnection.getInstance().removeCarryTier(carryTier.get());

        if (deletedCarryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException();
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(deletedCarryTier.get())
                .setTitle("Deleted Carry Tier"));
    }

    public void get(SlashCommandInteractionOption subCommand) {
        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(getStringOption(subCommand, CarryTypeCommand.FIELD_NAME));

        if (carryType.isEmpty()) {
            //TODO custom exception class
            throw new CommandExecutionException("Carry type not found.");
        }

        Optional<CarryTierModel> carryTier = CarryTierConnection.getInstance(carryType.get())
                .getByIdentifier(getStringOption(subCommand, FIELD_NAME));

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(FIELD_NAME, "That carry tier doesn't exist!");
        }

        respondEphemeral(ApplicationService.getInstance().getCarryTierEmbed(carryTier.get()));
    }

    public void edit(SlashCommandInteractionOption subCommand) {
        Server server = getServer();

        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(server.getId())
                .getByIdentifier(getStringOption(subCommand, CarryTypeCommand.FIELD_NAME));

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("That carry type doesn't exists!");
        }

        Optional<CarryTierModel> carryTier = CarryTierConnection.getInstance(carryType.get())
                .getByIdentifier(getStringOption(subCommand, FIELD_NAME));

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(FIELD_NAME, "That carry tier doesn't exist");
        }

        Optional<String> displayName = getOptionalStringOption(subCommand, "display-name");
        Optional<ChannelCategory> category = getOptionalChannelOption(subCommand, "category")
                .flatMap(Channel::asChannelCategory);
        Optional<ServerChannel> priceChannel = getOptionalChannelOption(subCommand, "price-channel");
        Optional<String> descriptiveName = getOptionalStringOption(subCommand, "descriptive-name");
        Optional<String> thumbnailUrl = getOptionalStringOption(subCommand, "thumbnail-url");
        Optional<String> priceTitle = getOptionalStringOption(subCommand, "price-title");

        if (displayName.isEmpty() && category.isEmpty() && priceChannel.isEmpty() && descriptiveName.isEmpty() && thumbnailUrl.isEmpty() && priceTitle.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Please provide something you want to edit.");
        }

        if (category.isPresent()) {
            Optional<CarryTierModel> categoryCarryTier =
                    DiscordServerConnection.getInstance()
                            .getCarryTierFromCategory(server.getId(), category.get().getId());
            if (categoryCarryTier.isPresent()) {
                respondEphemeral(ApplicationService.getInstance()
                        .getErrorEmbed(ApplicationService.getInstance().getCarryTierEmbed(categoryCarryTier.get()))
                        .setTitle("Carry Tier for that category is already present!"));
                return;
            }
        }

        CarryTierUpdateModel updateModel = CarryTierUpdateModel.fromCarryTier(carryTier.get());

        displayName.ifPresent(updateModel::setDisplayName);
        category.map(DiscordEntity::getId).ifPresent(updateModel::setCategory);
        priceChannel.map(DiscordEntity::getId).ifPresent(updateModel::setPriceChannel);
        descriptiveName.ifPresent(updateModel::setDescriptiveName);
        thumbnailUrl.ifPresent(updateModel::setThumbnailUrl);
        priceTitle.ifPresent(updateModel::setPriceTitle);

        Optional<CarryTierModel> updatedCarryTier = CarryTierConnection.getInstance(carryType.get())
                .updateCarryTier(carryTier.get().getId(), updateModel);

        if (updatedCarryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Couldn't update carry tier.");
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(updatedCarryTier.get())
                .setTitle("Updated Carry Tier"));
    }

    public void reset(SlashCommandInteractionOption subCommand) {
        Optional<CarryTypeModel> carryType = CarryTypeConnection.getInstance(getServer().getId())
                .getByIdentifier(getStringOption(subCommand, CarryTypeCommand.FIELD_NAME));

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("That carry type doesn't exists!");
        }

        Optional<CarryTierModel> carryTier = CarryTierConnection.getInstance(carryType.get())
                .getByIdentifier(getStringOption(subCommand, FIELD_NAME));

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(FIELD_NAME, "Carry tier doesn't exist");
        }

        Boolean category = getBooleanOption(subCommand, "category");
        Boolean priceChannel = getBooleanOption(subCommand, "price-channel");
        Boolean descriptiveName = getBooleanOption(subCommand, "descriptive-name");
        Boolean thumbnailUrl = getBooleanOption(subCommand, "thumbnail-url");
        Boolean priceTitle = getBooleanOption(subCommand, "price-title");

        if (!category && !priceChannel && !descriptiveName && !thumbnailUrl && !priceTitle) {
            //TODO custom class
            throw new CommandExecutionException("Please provide something you want to reset.");
        }

        CarryTierUpdateModel updateModel = CarryTierUpdateModel.fromCarryTier(carryTier.get());

        if (Boolean.TRUE.equals(category)) {
            updateModel.setCategory(-1L);
        }

        if (Boolean.TRUE.equals(priceChannel)) {
            updateModel.setPriceChannel(-1L);
        }

        if (Boolean.TRUE.equals(descriptiveName)) {
            updateModel.setDescriptiveName(null);
        }

        if (Boolean.TRUE.equals(thumbnailUrl)) {
            updateModel.setThumbnailUrl(null);
        }

        if (Boolean.TRUE.equals(priceTitle)) {
            updateModel.setPriceTitle(null);
        }

        Optional<CarryTierModel> updatedCarryTier = CarryTierConnection.getInstance(carryType.get())
                .updateCarryTier(carryTier.get().getId(), updateModel);

        if (updatedCarryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Couldn't update carry tier.");
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(updatedCarryTier.get())
                .setTitle("Updated Carry Tier with reset values"));
    }

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
        SlashCommandOption displayNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("display-name")
                .setDescription("Set the display name of the carry tier")
                .build();

        SlashCommandOption descriptiveNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("descriptive-name")
                .setDescription("Set the descriptive name which replaces the display name in some places")
                .build();

        SlashCommandOption categoryOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.CHANNEL_CATEGORY))
                .setName("category")
                .setDescription("Set the category of the tickets")
                .build();

        SlashCommandOption priceChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("price-channel")
                .setDescription("Set the channel where the price list should appear")
                .build();

        SlashCommandOption thumbnailUrlOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("thumbnail-url")
                .setDescription("Set the thumbnail which is used to make some embeds look nicer")
                .build();

        SlashCommandOption priceTitleOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("price-title")
                .setDescription("Set the title of the price embed")
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("edit")
                .setDescription("Edit a carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(),
                        getCarryTierOption(),
                        displayNameOption,
                        descriptiveNameOption,
                        categoryOption,
                        priceChannelOption,
                        thumbnailUrlOption,
                        priceTitleOption))
                .build();
    }

    private SlashCommandOption getResetCommand() {
        SlashCommandOption descriptiveNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("descriptive-name")
                .setDescription("Reset the descriptive name which replaces the display name in some places")
                .setRequired(true)
                .build();

        SlashCommandOption categoryOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("category")
                .setDescription("Reset the category of the tickets")
                .setRequired(true)
                .build();

        SlashCommandOption priceChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("price-channel")
                .setDescription("Reset the channel where the price list should appear")
                .setRequired(true)
                .build();

        SlashCommandOption thumbnailUrlOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("thumbnail-url")
                .setDescription("Reset the thumbnail which is used to make some embeds look nicer")
                .setRequired(true)
                .build();

        SlashCommandOption priceTitleOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("price-title")
                .setDescription("Reset the title of the price embed")
                .setRequired(true)
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("reset")
                .setDescription("Reset a carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), getCarryTierOption(),
                        descriptiveNameOption, categoryOption, priceChannelOption, thumbnailUrlOption,
                        priceTitleOption))
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

        SlashCommandOption descriptiveNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("descriptive-name")
                .setDescription("Set the descriptive name which replaces the display name in some places")
                .build();

        SlashCommandOption categoryOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.CHANNEL_CATEGORY))
                .setName("category")
                .setDescription("Set the category of the tickets")
                .build();

        SlashCommandOption priceChannelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL))
                .setName("price-channel")
                .setDescription("Set the channel where the price list should appear")
                .build();

        SlashCommandOption priceDescriptionOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("price-description")
                .setDescription("Set the price description which is shown on the top of the price message.")
                .build();

        SlashCommandOption thumbnailUrlOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("thumbnail-url")
                .setDescription("Set the thumbnail which is used to make some embeds look nicer")
                .build();

        SlashCommandOption priceTitleOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("price-title")
                .setDescription("Set the title of the price embed")
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("create")
                .setDescription("Create a new carry tier")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), identifierOption, displayNameOption,
                        descriptiveNameOption, categoryOption, priceChannelOption, priceDescriptionOption,
                        thumbnailUrlOption, priceTitleOption))
                .build();
    }
}