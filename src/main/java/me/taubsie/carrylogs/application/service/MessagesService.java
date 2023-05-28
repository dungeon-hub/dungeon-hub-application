package me.taubsie.carrylogs.application.service;

import com.google.common.collect.Lists;
import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.enums.CarryPrice;
import me.taubsie.carrylogs.application.enums.CarryType;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.OnStart;
import me.taubsie.dungeonhub.common.ProgramOrigin;
import me.taubsie.dungeonhub.common.StartupListener;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnStart
public class MessagesService implements StartupListener {
    private static MessagesService instance;

    public static MessagesService getInstance() {
        if(instance == null) {
            instance = new MessagesService();
        }

        return instance;
    }

    public List<EmbedBuilder> getDungeonEmbed() {
        List<EmbedBuilder> result = new ArrayList<>();
        Map<CarryType, List<CarryPrice>> priceMap = new HashMap<>();

        for(CarryPrice carryPrice : CarryPrice.getDungeonPrices()) {
            if(priceMap.containsKey(carryPrice.getCarryType())) {
                priceMap.get(carryPrice.getCarryType()).add(carryPrice);
            } else {
                priceMap.put(carryPrice.getCarryType(), Lists.newArrayList(carryPrice));
            }
        }

        Map<CarryType, List<CarryPrice>> sortedMap = new TreeMap<>(priceMap);

        for(Map.Entry<CarryType, List<CarryPrice>> entry : sortedMap.entrySet()) {
            result.add(getEmbedFromPriceInformation(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    public EmbedBuilder getEndermanEmbed() {
        return getEmbedFromPriceInformation(CarryType.EMAN, CarryPrice.getEndermanPrices());
    }

    public EmbedBuilder getBlazeEmbed() {
        return getEmbedFromPriceInformation(CarryType.BLAZE, CarryPrice.getBlazePrices());
    }

    public EmbedBuilder getKuudraEmbed() {
        return getEmbedFromPriceInformation(CarryType.KUUDRA, CarryPrice.getKuudraPrices());
    }

    private EmbedBuilder getEmbedFromPriceInformation(CarryType carryType, List<CarryPrice> carryPrices) {
        String title = carryType.getDescriptiveName();

        String description = "";

        if(carryType.getExtraInformation() != null) {
            description += carryType.getExtraInformation() + "\n\n";
        }

        description+=  carryPrices.stream()
                .map(carryPrice -> {
                    String result = "### "
                            + carryPrice.getCarryTier().getDescriptiveName()
                            + " Price\n- "
                            + ApplicationService.getInstance().makeNumberReadable(carryPrice.getPrice())
                            + " coins";

                    if(carryPrice.getBulkAmount() > 0) {
                        result += "\n- "
                                + ApplicationService.getInstance().makeNumberReadable(carryPrice.getBulkPrice())
                                + " per carry if you buy " + carryPrice.getBulkAmount() + "+ carries.";
                    }

                    return result;
                })
                .collect(Collectors.joining("\n"));

        return ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor())
                .setTitle(title)
                .setDescription(description)
                .setThumbnail(ApplicationService.getInstance().getCarryTierUrl(carryType));
    }

    public void loadAllStaticMessages() {
        DiscordApi bot = BotStarter.getInstance().getBot();

        Stream.concat(bot.getServerById(IdList.SERVER.getId()).stream(), bot.getServerById(IdList.SERVER.getTestId()).stream())
                .forEach(this::refreshPriceMessagesInServer);
    }

    public void refreshPriceMessagesInServer(Server server) {
        if(server.getId() != IdList.SERVER.getId() && server.getId() != IdList.SERVER.getTestId()) {
            return;
        }

        DiscordApi bot = BotStarter.getInstance().getBot();

        ServerProperty.DUNGEON_PRICE_CHANNEL.getValue(server.getId())
                .flatMap(bot::getServerTextChannelById)
                .ifPresent(textChannel -> refreshPriceMessageInChannel(textChannel, getDungeonEmbed()));

        ServerProperty.ENDERMAN_PRICE_CHANNEL.getValue(server.getId())
                .flatMap(bot::getServerTextChannelById)
                .ifPresent(textChannel -> refreshPriceMessageInChannel(textChannel, getEndermanEmbed()));

        ServerProperty.BLAZE_PRICE_CHANNEL.getValue(server.getId())
                .flatMap(bot::getServerTextChannelById)
                .ifPresent(textChannel -> refreshPriceMessageInChannel(textChannel, getBlazeEmbed()));

        ServerProperty.KUUDRA_PRICE_CHANNEL.getValue(server.getId())
                .flatMap(bot::getServerTextChannelById)
                .ifPresent(textChannel -> refreshPriceMessageInChannel(textChannel, getKuudraEmbed()));
    }

    private void refreshPriceMessageInChannel(ServerTextChannel textChannel, EmbedBuilder embed) {
        Optional<Message> messageOptional =
                textChannel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();

        if(messageOptional.isEmpty()) {
            textChannel.sendMessage(embed);
        } else {
            messageOptional.get().createUpdater().removeAllEmbeds().addEmbed(embed).applyChanges().join();
        }
    }

    private void refreshPriceMessageInChannel(ServerTextChannel textChannel, List<EmbedBuilder> embeds) {
        Optional<Message> messageOptional =
                textChannel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();

        if(messageOptional.isEmpty()) {
            textChannel.sendMessage(embeds);
        } else {
            messageOptional.get().createUpdater().removeAllEmbeds().addEmbeds(embeds).applyChanges().join();
        }
    }

    @Override
    public void postStart(ProgramOrigin programOrigin) {
        loadAllStaticMessages();
    }
}