package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MessagesService {
    private static MessagesService instance;

    public static MessagesService getInstance() {
        if (instance == null) {
            instance = new MessagesService();
        }

        return instance;
    }

    public Optional<EmbedBuilder> getPriceEmbed(CarryTierModel carryTier) {
        List<CarryDifficultyModel> carryDifficulties =
                CarryDifficultyConnection.getInstance(carryTier)
                        .getAllCarryDifficulties().stream()
                        .flatMap(Collection::stream)
                        .toList();

        if (carryDifficulties.isEmpty()) {
            return Optional.empty();
        }

        String title = "## " + carryTier.getPriceTitle() + "\n";
        Optional<String> priceDescription = carryTier.getPriceDescription();

        String description = title + priceDescription.map(s -> s + "\n\n").orElse("") + carryDifficulties.stream()
                .map(carryDifficulty -> {
                    StringBuilder result = new StringBuilder();

                    if (carryDifficulty.getBulkAmount().isPresent() && carryDifficulty.getBulkPrice().isPresent()) {
                        result.append("\n");
                    }

                    result.append("**")
                            .append(carryDifficulty.getPriceName())
                            .append("**: ");

                    String priceText = carryDifficulty.getPrice() != 0
                            ? ApplicationService.getInstance().makeNumberReadable(carryDifficulty.getPrice()) + " coins"
                            : "Free";

                    result.append(priceText);

                    if (carryDifficulty.getBulkAmount().isPresent() && carryDifficulty.getBulkPrice().isPresent()) {
                        result.append("\n\\*")
                                .append(ApplicationService.getInstance().makeNumberReadable(carryDifficulty.getBulkPrice().get()))
                                .append(" per carry if you buy ")
                                .append(carryDifficulty.getBulkAmount().get())
                                .append("+ carries.");
                    }

                    return result;
                }).collect(Collectors.joining("\n"));

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbedWithoutTimestamp()
                .setColor(EmbedColor.DEFAULT.getColor())
                .setDescription(description);

        carryTier.getThumbnailUrl().ifPresent(embed::setThumbnail);

        return Optional.of(embed);
    }

    private List<EmbedBuilder> addPriceFooterToLast(List<EmbedBuilder> embeds) {
        for (EmbedBuilder embed : embeds) {
            embed.setFooter(null);
        }

        if (!embeds.isEmpty()) {
            embeds.get(embeds.size() - 1).setFooter(ApplicationService.getInstance().getPriceFooter());
        }

        return embeds;
    }

    private void refreshPriceMessageInChannel(ServerTextChannel textChannel, List<EmbedBuilder> embeds) {
        if (embeds.isEmpty()) {
            return;
        }

        Optional<Message> messageOptional =
                textChannel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();

        if (messageOptional.isEmpty()) {
            textChannel.sendMessage(embeds);
        } else {
            messageOptional.get().createUpdater().removeAllEmbeds().addEmbeds(embeds).applyChanges().join();
        }
    }
}