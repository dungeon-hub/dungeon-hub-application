package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.*;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;

import java.sql.Time;
import java.util.*;
import java.util.stream.Collectors;

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

    public Optional<EmbedBuilder> getPriceEmbed(CarryTier carryTier) {
        List<CarryDifficulty> carryDifficulties = DungeonHubConnection.getInstance().loadCarryDifficulties(carryTier);

        return getEmbedFromCarryDifficulty(carryDifficulties);
    }

    private Optional<EmbedBuilder> getEmbedFromCarryDifficulty(List<CarryDifficulty> carryDifficulties) {
        if (carryDifficulties.isEmpty()) {
            return Optional.empty();
        }

        CarryDifficulty mainCarryDifficulty = carryDifficulties.get(0);

        String title = mainCarryDifficulty.getCarryTier().getDescriptiveName();

        String description = carryDifficulties.stream()
                .map(carryDifficulty -> {
                    StringBuilder result = new StringBuilder("### ");

                    result.append(carryDifficulty.getDisplayName())
                            .append(" Price\n- ")
                            .append(ApplicationService.getInstance().makeNumberReadable(carryDifficulty.getPrice()))
                            .append(" coins");

                    if (carryDifficulty.getBulkAmount().isPresent() && carryDifficulty.getBulkPrice().isPresent()) {
                        result.append("\n - ")
                                .append(ApplicationService.getInstance().makeNumberReadable(carryDifficulty.getBulkPrice().get()))
                                .append(" per carry if you buy ")
                                .append(carryDifficulty.getBulkAmount().get())
                                .append("+ carries.");
                    }

                    return result;
                }).collect(Collectors.joining("\n"));

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setFooter(ApplicationService.getInstance().getPriceFooter())
                .setColor(EmbedColor.DEFAULT.getColor())
                .setTitle(title)
                .setDescription(description);

        if (mainCarryDifficulty.getCarryTier().getThumbnailUrl().isPresent()) {
            embed.setThumbnail(mainCarryDifficulty.getCarryTier().getThumbnailUrl().get());
        }

        return Optional.of(embed);
    }

    public void refreshPriceMessages(Server server) {
        Map<Long, List<CarryTier>> carryTiersPerChannel = DungeonHubConnection.getInstance()
                .loadCarryTiers(server).stream()
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
                .forEach((key, value) -> BotStarter.getInstance().getBot()
                        .getServerTextChannelById(key)
                        .ifPresent(serverTextChannel -> refreshPriceMessageInChannel(
                                serverTextChannel,
                                value.stream()
                                        .flatMap(carryTier -> getPriceEmbed(carryTier).stream())
                                        .toList()
                        ))
                );
    }

    private void refreshPriceMessages() {
        Map<Long, List<CarryTier>> carryTiersPerChannel = DungeonHubConnection.getInstance()
                .loadCarryTiers().stream()
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
                .forEach((key, value) -> BotStarter.getInstance().getBot()
                        .getServerTextChannelById(key)
                        .ifPresent(serverTextChannel -> refreshPriceMessageInChannel(
                                serverTextChannel,
                                value.stream()
                                        .flatMap(carryTier -> getPriceEmbed(carryTier).stream())
                                        .toList()
                        ))
                );
    }

    private void refreshPriceMessageInChannel(ServerTextChannel textChannel, List<EmbedBuilder> embeds) {
        if(embeds.isEmpty()) {
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
    public void postStart(ProgramOrigin programOrigin) {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshPriceMessages();
            }
        }, new Time(System.currentTimeMillis() + 15000), REFRESH_PERIOD);
    }
}