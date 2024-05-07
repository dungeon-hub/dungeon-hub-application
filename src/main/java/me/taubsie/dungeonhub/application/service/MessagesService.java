package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryDifficultyConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.loader.StartupListener;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.kord.application.loader.OnStart;
import me.taubsie.dungeonhub.kord.application.service.ServerService;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;

import java.sql.Time;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnStart
public class MessagesService implements StartupListener {
    private static final long REFRESH_PERIOD = 1000L * 60 * 15;
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

    public void refreshPriceMessages(long serverId) {
        refreshPriceMessages(DiscordServerConnection.getInstance()
                .getAllCarryTiers(serverId)
                .orElse(new ArrayList<>())
                .stream());
    }

    public void refreshPriceMessages(Server server) {
        refreshPriceMessages(DiscordServerConnection.getInstance()
                .getAllCarryTiers(server.getId())
                .orElse(new ArrayList<>())
                .stream());
    }

    private void refreshPriceMessages() {
        ServerService.INSTANCE
                .getAllServers()
                .forEach(serverData -> refreshPriceMessages(serverData.getId()));
    }

    private void refreshPriceMessages(Stream<CarryTierModel> carryTiers) {
        Map<Long, List<CarryTierModel>> carryTiersPerChannel = carryTiers
                .filter(carryTier -> carryTier.getPriceChannel().isPresent())
                .collect(Collectors.toMap(
                        carryTier -> carryTier.getPriceChannel().get(),
                        carryTier -> new ArrayList<>(List.of(carryTier)),
                        (o, o2) -> {
                            o.addAll(o2);
                            return o;
                        }
                ));

        carryTiersPerChannel
                .forEach((key, value) -> DiscordConnection.getInstance().getBot()
                        .getServerTextChannelById(key)
                        .ifPresent(serverTextChannel -> refreshPriceMessageInChannel(
                                serverTextChannel,
                                addPriceFooterToLast(
                                        value.stream()
                                                .flatMap(carryTier -> getPriceEmbed(carryTier).stream())
                                                .toList()
                                )
                        ))
                );
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

    @Override
    public void postStart() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshPriceMessages();
            }
        }, new Time(System.currentTimeMillis() + 15000), REFRESH_PERIOD);
    }
}