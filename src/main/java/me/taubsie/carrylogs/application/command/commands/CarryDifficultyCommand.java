package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

@CommandParameters(name = "carry-difficulty", description = "Set up the carry difficulties for this server.")
public class CarryDifficultyCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        throw new UnknownCommandException();
    }

    //TODO uncomment and implement
    /*@Override
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
                        thumbnailUrlOption))
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
    }*/

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