package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryDifficulty;
import me.taubsie.dungeonhub.common.CarryTier;
import me.taubsie.dungeonhub.common.CarryType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;
import java.util.Optional;

@CommandParameters(name = "carry-difficulty", description = "Set up the carry difficulties for this server.")
public class CarryDifficultyCommand extends Command {
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
        Optional<CarryType> carryType = DungeonHubConnection.getInstance().loadCarryType(getServer().getId(),
                getStringOption(subCommand, "carry-type"));

        if (carryType.isEmpty()) {
            //TODO custom exception class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Carry type not found.";
                }
            };
        }

        Optional<CarryTier> carryTier = DungeonHubConnection.getInstance()
                .loadCarryTier(carryType.get(), getStringOption(subCommand, CarryTier.FIELD_NAME));

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(CarryTier.FIELD_NAME, "That carry tier doesn't exist!");
        }

        Optional<CarryDifficulty> carryDifficulty = DungeonHubConnection.getInstance()
                .loadCarryDifficulty(carryTier.get(), getStringOption(subCommand, "carry-difficulty"));

        if (carryDifficulty.isEmpty()) {
            throw new InvalidOptionException("carry-difficulty", "That carry difficulty doesn't exist!");
        }

        respondEphemeral(ApplicationService.getInstance().getCarryDifficultyEmbed(carryDifficulty.get()));
    }

    public void edit(SlashCommandInteractionOption subCommand) {
        Server server = getServer();

        Optional<CarryType> carryType = DungeonHubConnection.getInstance()
                .loadCarryType(server.getId(), getStringOption(subCommand, "carry-type"));

        if (carryType.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "That carry type doesn't exists!";
                }
            };
        }

        Optional<CarryTier> carryTier = DungeonHubConnection.getInstance()
                .loadCarryTier(carryType.get(), getStringOption(subCommand, CarryTier.FIELD_NAME));

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(CarryTier.FIELD_NAME, "That carry tier doesn't exist");
        }

        Optional<CarryDifficulty> carryDifficulty = DungeonHubConnection.getInstance()
                .loadCarryDifficulty(carryTier.get(), getStringOption(subCommand, "carry-difficulty"));

        if(carryDifficulty.isEmpty()) {
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
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Please provide something you want to edit.";
                }
            };
        }

        displayName.ifPresent(s -> carryDifficulty.get().setDisplayName(s));
        price.ifPresent(p -> carryDifficulty.get().setPrice(Math.toIntExact(p)));
        score.ifPresent(s -> carryDifficulty.get().setScore(Math.toIntExact(s)));
        bulkAmount.ifPresent(ba -> carryDifficulty.get().setBulkAmount(Math.toIntExact(ba)));
        bulkPrice.ifPresent(bp -> carryDifficulty.get().setBulkPrice(Math.toIntExact(bp)));
        thumbnailUrl.ifPresent(s -> carryDifficulty.get().setThumbnailUrl(s));
        priceName.ifPresent(s -> carryDifficulty.get().setPriceName(s));

        Optional<CarryDifficulty> updatedCarryDifficulty = DungeonHubConnection.getInstance().updateCarryDifficulty(carryDifficulty.get());

        if (updatedCarryDifficulty.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't update carry difficulty.";
                }
            };
        }

        respond(ApplicationService.getInstance()
                .getCarryDifficultyEmbed(updatedCarryDifficulty.get())
                .setTitle("Updated Carry Difficulty"));
    }

    public void reset(SlashCommandInteractionOption subCommand) {
        throw new InvalidSubCommandException();
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        return List.of(getCreateCommand(), getDeleteCommand(), getGetCommand(), getEditCommand(), getResetCommand());
    }

    private SlashCommandOption getGetCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Get information about a carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(), getCarryDifficultyOption()))
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
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(), getCarryDifficultyOption()))
                .build();
    }

    private SlashCommandOption getDeleteCommand() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("delete")
                .setDescription("Delete a carry difficulty")
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(), getCarryDifficultyOption()))
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
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), CarryTierCommand.getCarryTierOption(), identifierOption, displayNameOption))
                .build();
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