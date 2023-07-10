package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.*;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryDifficulty;
import me.taubsie.dungeonhub.common.CarryTier;
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
    public static long calculatePrice(CarryDifficulty carryDifficulty, long amount) {
        Optional<Integer> bulkPrice = carryDifficulty.getBulkPrice();
        Optional<Integer> bulkAmount = carryDifficulty.getBulkAmount();

        if (bulkPrice.isPresent() && bulkAmount.isPresent() && bulkAmount.get() <= amount) {
            return bulkPrice.get() * amount;
        }

        return carryDifficulty.getPrice() * amount;
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        Server server = getServer();

        long amount = getLongOption("amount");

        Optional<CarryTier> carryTier = slashCommandCreateEvent.getSlashCommandInteraction().getChannel()
                .flatMap(Channel::asCategorizable)
                .flatMap(Categorizable::getCategory)
                .map(DiscordEntity::getId)
                .flatMap(id -> DungeonHubConnection.getInstance().getCarryTierFromCategory(server.getId(), id));

        try {
            Optional<CarryTier> previousCarryTier = carryTier;

            carryTier = DungeonHubConnection.getInstance()
                    .loadCarryType(server.getId(), getStringOption("carry-type"))
                    .flatMap(carryType -> DungeonHubConnection.getInstance().loadCarryTier(carryType,
                            getStringOption("carry-tier")))
                    .or(() -> previousCarryTier);
        }
        catch (CommandExecutionException commandExecutionException) {
            //ignored since the checking happens later
        }

        if (carryTier.isEmpty()) {
            throw new InvalidOptionException("carry-tier", "Please either use this in a carry ticket or supply a " +
                    "carry type and carry tier.");
        }

        Optional<CarryDifficulty> carryDifficulty = DungeonHubConnection.getInstance()
                .loadCarryDifficulty(carryTier.get(), getStringOption("carry-difficulty"));

        if (carryDifficulty.isEmpty()) {
            throw new InvalidOptionException("carry-difficulty");
        }

        long price = calculatePrice(carryDifficulty.get(), amount);

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

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Carry-Price")
                .addInlineField("Type", carryTier.get().getDisplayName() + " | " + carryDifficulty.get().getDisplayName())
                .addInlineField("Amount", String.valueOf(amount))
                .addInlineField("Price", priceText);

        carryDifficulty.flatMap(CarryDifficulty::getThumbnailUrl).ifPresent(embed::setThumbnail);

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