package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.InvalidSubCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryTier;
import me.taubsie.dungeonhub.common.CarryType;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CommandParameters(name = "carry-tier", description = "Set up the carry tiers for this server.", enabledForPermissions = PermissionType.ADMINISTRATOR)
public class CarryTierCommand extends Command {
    public static SlashCommandOption getCarryTierOption() {
        return new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName(CarryTier.FIELD_NAME)
                .setDescription("The identifier of the carry tier")
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
        Server server = getServer();

        Optional<CarryType> carryType = DungeonHubConnection.getInstance()
                .loadCarryType(server.getId(), getStringOption(subCommand, "carry-type"));

        if (carryType.isEmpty()) {
            throw new InvalidOptionException("carry-type", "Carry Type couldn't be found.");
        }

        String identifier = getStringOption(subCommand, "identifier")
                .strip()
                .toLowerCase()
                .replace(" ", "_");
        String displayName = getStringOption(subCommand, "display-name");

        if (DungeonHubConnection.getInstance().isCarryTierExistant(carryType.get(), identifier)) {
            throw new InvalidOptionException("identifier", "That carry tier already exists!");
        }

        Map<String, String> optionals = new HashMap<>();

        getOptionalStringOption(subCommand, "descriptive-name")
                .ifPresent(s -> optionals.put("descriptiveName", s));
        Optional<ChannelCategory> category = getOptionalChannelOption(subCommand, "category")
                .flatMap(Channel::asChannelCategory);
        getOptionalChannelOption(subCommand, "price-channel")
                .ifPresent(channel -> optionals.put("priceChannel", channel.getIdAsString()));
        getOptionalStringOption(subCommand, "thumbnail-url")
                .ifPresent(s -> optionals.put("thumbnailUrl", s));
        getOptionalStringOption(subCommand, "price-title")
                .ifPresent(s -> optionals.put("priceTitle", s));

        if (category.isPresent()) {
            Optional<CarryTier> categoryCarryTier =
                    DungeonHubConnection.getInstance()
                            .getCarryTierFromCategory(server.getId(), category.get().getId());
            if (categoryCarryTier.isPresent()) {
                category = Optional.empty();
            }
        }

        category.ifPresent(c -> optionals.put("category", c.getIdAsString()));

        Optional<CarryTier> carryTier = DungeonHubConnection.getInstance()
                .addNewCarryTier(carryType.get(), identifier, displayName, optionals);

        if (carryTier.isEmpty()) {
            //TODO custom class?
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't add that carry tier.";
                }
            };
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(carryTier.get())
                .setTitle("Carry Tier created"));
    }

    public void delete(SlashCommandInteractionOption subCommand) {
        String carryTypeIdentifier = getStringOption(subCommand, "carry-type");
        String identifier = getStringOption(subCommand, CarryTier.FIELD_NAME);

        Optional<CarryType> carryType = DungeonHubConnection.getInstance()
                .loadCarryType(getServer().getId(), carryTypeIdentifier);

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
                .loadCarryTier(carryType.get(), identifier);

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException(CarryTier.FIELD_NAME);
        }

        if (!carryTier.get().getCarryType().equals(carryType.get())) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Well this is weird.. Something doesn't really add up!";
                }
            };
        }

        Optional<CarryTier> deletedCarryTier = DungeonHubConnection.getInstance().removeCarryTier(carryTier.get());

        if (deletedCarryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Carry tier couldn't be deleted!";
                }
            };
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(deletedCarryTier.get())
                .setTitle("Deleted Carry Tier"));
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

        respondEphemeral(ApplicationService.getInstance().getCarryTierEmbed(carryTier.get()));
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

        Optional<String> displayName = getOptionalStringOption(subCommand, "display-name");
        Optional<ChannelCategory> category = getOptionalChannelOption(subCommand, "category")
                .flatMap(Channel::asChannelCategory);
        Optional<ServerChannel> priceChannel = getOptionalChannelOption(subCommand, "price-channel");
        Optional<String> descriptiveName = getOptionalStringOption(subCommand, "descriptive-name");
        Optional<String> thumbnailUrl = getOptionalStringOption(subCommand, "thumbnail-url");
        Optional<String> priceTitle = getOptionalStringOption(subCommand, "price-title");

        if (displayName.isEmpty() && category.isEmpty() && priceChannel.isEmpty() && descriptiveName.isEmpty() && thumbnailUrl.isEmpty() && priceTitle.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Please provide something you want to edit.";
                }
            };
        }

        if (category.isPresent()) {
            Optional<CarryTier> categoryCarryTier =
                    DungeonHubConnection.getInstance()
                            .getCarryTierFromCategory(server.getId(), category.get().getId());
            if (categoryCarryTier.isPresent()) {
                respondEphemeral(ApplicationService.getInstance()
                        .getErrorEmbed(ApplicationService.getInstance().getCarryTierEmbed(categoryCarryTier.get()))
                        .setTitle("Carry Tier for that category is already present!"));
                return;
            }
        }

        displayName.ifPresent(s -> carryTier.get().setDisplayName(s));
        category.map(DiscordEntity::getId).ifPresent(id -> carryTier.get().setCategory(id));
        priceChannel.map(DiscordEntity::getId).ifPresent(id -> carryTier.get().setPriceChannel(id));
        descriptiveName.ifPresent(s -> carryTier.get().setDescriptiveName(s));
        thumbnailUrl.ifPresent(s -> carryTier.get().setThumbnailUrl(s));
        priceTitle.ifPresent(s -> carryTier.get().setPriceTitle(s));

        Optional<CarryTier> updatedCarryTier = DungeonHubConnection.getInstance().updateCarryTier(carryTier.get());

        if (updatedCarryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't update carry tier.";
                }
            };
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(updatedCarryTier.get())
                .setTitle("Updated Carry Tier"));
    }

    public void reset(SlashCommandInteractionOption subCommand) {
        Optional<CarryType> carryType = DungeonHubConnection.getInstance()
                .loadCarryType(getServer().getId(), getStringOption(subCommand, "carry-type"));

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
            throw new InvalidOptionException(CarryTier.FIELD_NAME, "Carry tier doesn't exist");
        }

        Boolean category = getBooleanOption(subCommand, "category");
        Boolean priceChannel = getBooleanOption(subCommand, "price-channel");
        Boolean descriptiveName = getBooleanOption(subCommand, "descriptive-name");
        Boolean thumbnailUrl = getBooleanOption(subCommand, "thumbnail-url");
        Boolean priceTitle = getBooleanOption(subCommand, "price-title");

        if (!category && !priceChannel && !descriptiveName && !thumbnailUrl) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Please provide something you want to reset.";
                }
            };
        }

        if (category) {
            carryTier.get().setCategory(-1L);
        }

        if (priceChannel) {
            carryTier.get().setPriceChannel(-1L);
        }

        if (descriptiveName) {
            carryTier.get().setDescriptiveName(null);
        }

        if (thumbnailUrl) {
            carryTier.get().setThumbnailUrl(null);
        }

        if (priceTitle) {
            carryTier.get().setPriceTitle(null);
        }

        Optional<CarryTier> updatedCarryTier = DungeonHubConnection.getInstance().updateCarryTier(carryTier.get());

        if (updatedCarryTier.isEmpty()) {
            //TODO custom class
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Couldn't update carry tier.";
                }
            };
        }

        respond(ApplicationService.getInstance()
                .getCarryTierEmbed(updatedCarryTier.get())
                .setTitle("Updated Carry Tier with reset values"));
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
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), getCarryTierOption(), descriptiveNameOption, categoryOption, priceChannelOption, thumbnailUrlOption, priceTitleOption))
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
                .setOptions(List.of(CarryTypeCommand.getCarryTypeOption(), identifierOption, displayNameOption, descriptiveNameOption, categoryOption, priceChannelOption, thumbnailUrlOption, priceTitleOption))
                .build();
    }
}