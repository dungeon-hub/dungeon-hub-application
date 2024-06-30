package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTierConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.kord.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyUpdateModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

@CommandParameters(name = "carry-difficulty", description = "Set up the carry difficulties for this server.",
        enabledForPermissions = PermissionType.ADMINISTRATOR)
public class CarryDifficultyCommand extends Command {
    public static final String FIELD_NAME = "carry-difficulty";

    public static SlashCommandOption getCarryDifficultyOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName(FIELD_NAME)
                .setDescription("The identifier of the carry difficulty")
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
            case "get" -> get(subCommand);
            case "edit" -> edit(subCommand);
            case "reset" -> reset(subCommand);
            default -> throw new InvalidSubCommandException();
        }
    }

    public void create(SlashCommandInteractionOption subCommand) {
        throw new InvalidSubCommandException();
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        throw new InvalidSubCommandException();
    }

    public void get(SlashCommandInteractionOption subCommand) {
        CarryTypeModel carryType = getCarryType(getServer().getId(),
                getStringOption(subCommand, CarryTypeCommand.FIELD_NAME));

        CarryTierModel carryTier = getCarryTier(carryType,
                getStringOption(subCommand, CarryTierCommand.FIELD_NAME));

        CarryDifficultyModel carryDifficulty = getCarryDifficulty(carryTier,
                getStringOption(subCommand, FIELD_NAME));

        respondEphemeral(ApplicationService.getInstance().getCarryDifficultyEmbed(carryDifficulty));
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
                .getByIdentifier(getStringOption(subCommand, CarryTierCommand.FIELD_NAME));

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(CarryTierCommand.FIELD_NAME, "That carry tier doesn't exist");
        }

        Optional<CarryDifficultyModel> carryDifficulty = CarryDifficultyConnection.getInstance(carryTier.get())
                .getByIdentifier(getStringOption(subCommand, FIELD_NAME));

        if (carryDifficulty.isEmpty()) {
            throw new InvalidOptionException("carry.difficulty", "That carry difficulty doesn't exist");
        }

        Optional<String> displayName = getOptionalStringOption(subCommand, "display-name");
        Optional<Long> price = getOptionalLongOption(subCommand, "price");
        Optional<Long> score = getOptionalLongOption(subCommand, "score");
        Optional<Long> bulkAmount = getOptionalLongOption(subCommand, "bulk-amount");
        Optional<Long> bulkPrice = getOptionalLongOption(subCommand, "bulk-price");
        Optional<String> thumbnailUrl = getOptionalStringOption(subCommand, "thumbnail-url");
        Optional<String> priceName = getOptionalStringOption(subCommand, "price-name");

        if (displayName.isEmpty() && price.isEmpty() && score.isEmpty() && bulkAmount.isEmpty() && bulkPrice.isEmpty() && thumbnailUrl.isEmpty() && priceName.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Please provide something you want to edit.");
        }

        CarryDifficultyUpdateModel updateModel = new CarryDifficultyUpdateModel();

        displayName.ifPresent(updateModel::setDisplayName);
        price.ifPresent(p -> updateModel.setPrice(Math.toIntExact(p)));
        score.ifPresent(s -> updateModel.setScore(Math.toIntExact(s)));
        bulkAmount.ifPresent(ba -> updateModel.setBulkAmount(Math.toIntExact(ba)));
        bulkPrice.ifPresent(bp -> updateModel.setBulkPrice(Math.toIntExact(bp)));
        thumbnailUrl.ifPresent(updateModel::setThumbnailUrl);
        priceName.ifPresent(updateModel::setPriceName);

        Optional<CarryDifficultyModel> updatedCarryDifficulty = CarryDifficultyConnection.getInstance(carryTier.get())
                .updateCarryDifficulty(carryDifficulty.get().getId(), updateModel);

        if (updatedCarryDifficulty.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException("Couldn't update carry difficulty.");
        }

        respond(ApplicationService.getInstance()
                .getCarryDifficultyEmbed(updatedCarryDifficulty.get())
                .setTitle("Updated Carry Difficulty"));
    }

    public void reset(SlashCommandInteractionOption subCommand) {
        throw new InvalidSubCommandException();
    }

    public List<SlashCommandOption> getSlashCommandOptions() {
        return List.of(getCreateCommand(), getDeleteCommand(), getGetCommand(), getEditCommand(), getResetCommand());
    }

    private SlashCommandOption getGetCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Get information about a carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(),
                        getCarryDifficultyOption()))
                .build();
    }

    private SlashCommandOption getEditCommand() {
        SlashCommandOption displayNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("display-name")
                .setDescription("Set the display name of the carry difficulty")
                .build();

        SlashCommandOption priceOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("price")
                .setDescription("Set the price per carry")
                .setLongMinValue(0)
                .build();

        SlashCommandOption scoreOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("score")
                .setDescription("Set the score gained per carry")
                .setLongMinValue(0)
                .setLongMaxValue(500)
                .build();

        SlashCommandOption bulkAmountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("bulk-amount")
                .setDescription("Set the amount after which the carries use the bulk price.")
                .setLongMinValue(1)
                .setLongMaxValue(500)
                .build();

        SlashCommandOption bulkPriceOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("bulk-price")
                .setDescription("Set price for bulk carries. Needs to have bulk-price set to be used.")
                .setLongMinValue(1)
                .build();

        SlashCommandOption thumbnailUrlOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("thumbnail-url")
                .setDescription("Set the url for the thumbnail. This only acts as an override for the carry tier.")
                .build();

        SlashCommandOption priceNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("price-name")
                .setDescription("Set this if this carry difficulty should have a different name in the price embed.")
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("edit")
                .setDescription("Edit a carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(),
                        CarryTierCommand.getCarryTierOption(),
                        getCarryDifficultyOption(),
                        displayNameOption,
                        priceOption,
                        scoreOption,
                        bulkAmountOption,
                        bulkPriceOption,
                        thumbnailUrlOption,
                        priceNameOption))
                .build();
    }

    private SlashCommandOption getResetCommand() {
        //TODO add options to reset command
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("reset")
                .setDescription("Reset a carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(),
                        getCarryDifficultyOption()))
                .build();
    }

    private SlashCommandOption getDeleteCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("delete")
                .setDescription("Delete a carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(),
                        getCarryDifficultyOption()))
                .build();
    }

    private SlashCommandOption getCreateCommand() {
        //TODO add optional arguments
        SlashCommandOption identifierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("identifier")
                .setDescription("The identifier of the carry difficulty")
                .setRequired(true)
                .build();

        SlashCommandOption displayNameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("display-name")
                .setDescription("The display name of the carry difficulty")
                .setRequired(true)
                .setMaxLength(30)
                .build();

        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("create")
                .setDescription("Create a new carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(),
                        identifierOption, displayNameOption))
                .build();
    }
}