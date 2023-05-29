package me.taubsie.carrylogs.application.command.commands;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.command.CommandParameters;
import me.taubsie.carrylogs.application.enums.*;
import me.taubsie.carrylogs.application.exceptions.InvalidOptionException;
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
                .setThumbnail(ApplicationService.getInstance().getCarryTierUrl(carryType, carryTier))
                .addInlineField("Type", carryType.getPrettyName() + " " + carryTier)
                .addInlineField("Amount", String.valueOf(amount))
                .addInlineField("Price", ApplicationService.getInstance().makeNumberReadable(price) + " coins"));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        //F4, F5, F6, F7, MM, Eman, Blaze, Kuudra
        SlashCommandOptionBuilder typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("The type of carry you want.")
                .setRequired(true);

        Arrays.stream(CarryType.values())
                .forEach(carryType ->
                        typeOption.addChoice(
                                new SlashCommandOptionChoiceBuilder()
                                        .setName(carryType.name())
                                        .setValue(carryType.name())
                                        .build()
                        ));

        //Comp, S, S+, Tier 2-4, Kuudra Tiers
        SlashCommandOption tierOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("tier")
                .setDescription("The tier of carry you want. If nothing shows up here, please enter the type of carry first.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you want.")
                .setLongMaxValue(200)
                .setLongMinValue(1L)
                .setRequired(true)
                .build();

        return Arrays.asList(typeOption.build(), tierOption, amountOption);
    }
}