package me.taubsie.carrylogs.application.messages;

import com.google.gson.JsonObject;
import me.taubsie.carrylogs.application.connection.HypixelConnection;
import me.taubsie.carrylogs.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;

import java.util.List;

public class AuctionsMessage extends PageableMessage {
    private final boolean bin;
    private final String filter;

    public AuctionsMessage(int currentPage, long channel, long messageId, boolean bin, String filter) {
        super(currentPage, channel, messageId);
        this.bin = bin;
        this.filter = filter;
    }

    @Override
    public int getMaxPage() {
        int entries = HypixelConnection.getInstance().getTalismen(bin).size();

        return (int) Math.ceil(entries / 10.0);
    }

    @Override
    public void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage) {
        List<JsonObject> auctionData = HypixelConnection.getInstance().getTalismen(bin);

        auctionData = auctionData.stream().filter(jsonObject -> jsonObject.getAsJsonPrimitive("item_name").getAsString().toLowerCase().equals(filter)).toList();

        EmbedBuilder embedBuilder = ApplicationService.getInstance().loadAuctionsMessage(auctionData, currentPage);

        updater.removeAllEmbeds().addEmbed(embedBuilder).update();
    }
}