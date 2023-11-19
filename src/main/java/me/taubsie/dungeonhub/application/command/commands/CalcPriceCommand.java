package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTierConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ServerConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.channel.Categorizable;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "calc-price",
        description = "Calculate the price for some amount of carries.")
public class CalcPriceCommand extends Command {
    //TODO move to service
    public static long calculatePrice(CarryDifficultyModel carryDifficulty, long amount) {
        return calculatePricePerCarry(carryDifficulty, amount) * amount;
    }

    public static long calculatePricePerCarry(CarryDifficultyModel carryDifficulty, long amount) {
        Optional<Integer> bulkPrice = carryDifficulty.getBulkPrice();
        Optional<Integer> bulkAmount = carryDifficulty.getBulkAmount();

        if (bulkPrice.isPresent() && bulkAmount.isPresent() && bulkAmount.get() <= amount) {
            return bulkPrice.get();
        }

        return carryDifficulty.getPrice();
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();

        long amount = getLongOption("amount");

        Optional<CarryTierModel> carryTier = slashCommandCreateEvent.getSlashCommandInteraction().getChannel()
                .flatMap(Channel::asCategorizable)
                .flatMap(Categorizable::getCategory)
                .map(DiscordEntity::getId)
                .flatMap(id -> ServerConnection.getInstance().getCarryTierFromCategory(server.getId(), id));

        try {
            Optional<CarryTierModel> previousCarryTier = carryTier;

            carryTier = CarryTypeConnection.getInstance(server.getId())
                    .getByIdentifier(getStringOption("carry-type"))
                    .flatMap(carryType -> CarryTierConnection.getInstance(carryType)
                            .getByIdentifier(getStringOption("carry-tier")))
                    .or(() -> previousCarryTier);
        }
        catch (CommandExecutionException commandExecutionException) {
            //ignored since the checking happens later
        }

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException("carry-tier", "Please either use this in a carry ticket or supply a " +
                    "carry type and carry tier.");
        }

        Optional<CarryDifficultyModel> carryDifficulty = CarryDifficultyConnection.getInstance(carryTier.get())
                .getByIdentifier(getStringOption("carry-difficulty"));

        if (carryDifficulty.isEmpty()) {
            throw new InvalidOptionException("carry-difficulty");
        }

        long price = calculatePrice(carryDifficulty.get(), amount);
        long pricePerCarry = calculatePricePerCarry(carryDifficulty.get(), amount);

        if (price < 0) {
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "Something went wrong.. The calculated price (" + price + ") is negative?";
                }
            };
        }

        String priceText = price != 0
                ? ApplicationService.getInstance().makeNumberReadable(price) + " coins"
                : "Free";

        String pricePerCarryText = price != 0
                ? ApplicationService.getInstance().makeNumberReadable(pricePerCarry) + " coins"
                : "Free";

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Carry-Price")
                .addInlineField("Type",
                        carryTier.get().getDisplayName() + " | " + carryDifficulty.get().getDisplayName())
                .addInlineField("Amount", String.valueOf(amount))
                .addInlineField("Price", priceText)
                .addInlineField("Price per Carry", pricePerCarryText);

        carryDifficulty.flatMap(CarryDifficultyModel::getThumbnailUrl).ifPresent(embed::setThumbnail);

        respond(embed);
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you want.")
                .setLongMaxValue(200)
                .setLongMinValue(1L)
                .setRequired(true)
                .build();

        return Arrays.asList(CarryTypeCommand.getCarryTypeOption(),
                CarryTierCommand.getCarryTierOption(),
                CarryDifficultyCommand.getCarryDifficultyOption(),
                amountOption);
    }
}