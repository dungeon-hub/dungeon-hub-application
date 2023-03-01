package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.*;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

@CommandParameters(name = "calc-price",
        description = "Calculate the price for some amount of carries.",
        enabledInDms = true)
public class CalcPriceCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        long amount = getLongOption("amount");

        CarryType carryType = null;
        try {
            carryType = Arrays.stream(IdList.values())
                    .filter(id -> id.getCarryType() != null
                            && (id.getLocalId(slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElseThrow().getId()) == slashCommandCreateEvent.getSlashCommandInteraction().getChannel().orElseThrow().asCategorizable().orElseThrow().getCategory().orElseThrow().getId()))
                    .findFirst()
                    .orElseThrow()
                    .getCarryType();
        }
        catch(NoSuchElementException ignored) {
            //ignored since exception throwing happens later.
        }

        try {
            String carryTypeOption = getStringOption("type");
            carryType = CarryType.valueOf(carryTypeOption);
        }
        catch(InvalidOptionException invalidOptionException) {
            //If the command is used in a ticket or similar, the option isn't needed.
            if(carryType == null) {
                throw invalidOptionException;
            }
        }
        catch(IllegalArgumentException illegalArgumentException) {
            if(carryType == null) {
                throw new InvalidOptionException("type", "Carry type is unknown.");
            }
        }

        String carryTier = getStringOption("tier");

        if(!ApplicationService.getInstance().isCarryTier(carryTier, carryType)) {
            throw new InvalidOptionException("tier", "This is no valid carry-tier for carry-type " + carryType.name());
        }

        long price = CarryPrice.calculatePrice(carryType, CarryTier.fromString(carryTier).orElse(null), amount);

        if(price <= 0) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setTitle("Carry-Price")
                    .setDescription("The carry-price couldn't be calculated."));
            return;
        }

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Carry-Price")
                .setDescription("The price for " + amount + " `" + carryTier + "` carries is\n" +
                        ApplicationService.getInstance().makeNumberReadable(price) + " coins."));

        throw new UnknownCommandException();
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

        //Comp, S, S+, Tier 2-4, Kuudra Tiers
        SlashCommandOption tierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("tier")
                .setDescription("The tier of carry you want.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        //F4, F5, F6, F7, MM, Eman, Blaze, Kuudra
        SlashCommandOptionBuilder typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("The type of carry you want.")
                .setRequired(false);

        Arrays.stream(CarryType.values())
                .forEach(carryType ->
                        typeOption.addChoice(
                                new SlashCommandOptionChoiceBuilder()
                                        .setName(carryType.name())
                                        .setValue(carryType.name())
                                        .build()
                        ));

        return Arrays.asList(amountOption, tierOption, typeOption.build());
    }
}