package me.taubsie.dungeonhub.application.command.commands;

import com.google.gson.JsonObject;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.messages.AuctionsMessage;
import me.taubsie.dungeonhub.application.messages.PageableMessage;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "load-recombed-talismen", description = "Shows you all recombed talismen currently in the auction house.")
public class LoadRecombedTalismenCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        boolean bin = getBooleanOption("bin");

        List<JsonObject> talismen = HypixelConnection.getInstance().getTalismen(bin);

        if(talismen.isEmpty()) {
            respondEphemeral(ApplicationService.getInstance()
                    .getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setTitle("No auctions found! Try again later."));
            return;
        }

        String filter = null;
        try {
            filter = getStringOption("item").toLowerCase();

            String finalFilter = filter;
            talismen = talismen.stream().filter(jsonObject -> jsonObject.getAsJsonPrimitive("item_name").getAsString().toLowerCase().equals(finalFilter)).toList();
        } catch(InvalidOptionException invalidOptionException) {
            //ignored since option isn't required
        }

        List<JsonObject> finalTalismen = talismen;
        String finalFilter1 = filter;
        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater(true)
                .thenAccept(responseUpdater -> {
                    EmbedBuilder embed = ApplicationService.getInstance().loadAuctionsMessage(finalTalismen, 1);

                    int maxPage = (int) Math.ceil(finalTalismen.size() / 10.0);

                    Message message = responseUpdater
                            .addEmbed(embed)
                            .addComponents(PageableMessage.getComponents(true, maxPage == 1))
                            .update()
                            .join();

                    new AuctionsMessage(1, getChannel().getId(), message.getId(), bin, finalFilter1);
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption binOption = new SlashCommandOptionBuilder()
                .setName("bin")
                .setDescription("True if it should only show bin auctions.")
                .setType(SlashCommandOptionType.BOOLEAN)
                .setRequired(true)
                .build();

        SlashCommandOption itemOption = new SlashCommandOptionBuilder()
                .setName("item")
                .setDescription("Filter for an item.")
                .setType(SlashCommandOptionType.STRING)
                .setRequired(false)
                .build();

        return List.of(binOption, itemOption);
    }
}