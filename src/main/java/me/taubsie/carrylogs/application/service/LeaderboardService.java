package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.classes.Leaderboard;
import me.taubsie.dungeonhub.common.OnStart;
import me.taubsie.dungeonhub.common.ProgramOrigin;
import me.taubsie.dungeonhub.common.StartupListener;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.start.BotStarter;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.Instant;
import java.util.*;

@OnStart
public class LeaderboardService implements StartupListener {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);
    private static final long REFRESH_COOLDOWN = 15L;
    private static LeaderboardService instance;
    private Instant lastRefresh;

    public static LeaderboardService getInstance() {
        if(instance == null) {
            instance = new LeaderboardService();
        }

        return instance;
    }

    public long getNextPossibleRefresh() {
        return lastRefresh.plusSeconds(REFRESH_COOLDOWN).getEpochSecond();
    }

    @Override
    public void onStart(ProgramOrigin programOrigin) {
        this.lastRefresh = Instant.now().minusSeconds(REFRESH_COOLDOWN + 10L);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshLeaderboard();
            }
        }, new Time(System.currentTimeMillis() + 15000), 1000L * 60 * 5);
    }

    private void refreshLeaderboardInChannel(ServerTextChannel channel, List<Leaderboard> leaderboards) {
        List<EmbedBuilder> embeds = new ArrayList<>();

        for(Leaderboard leaderboard : leaderboards) {
            int counter = 0;
            EmbedBuilder embed = ApplicationService.getInstance()
                    .getEmbed()
                    .setTitle(leaderboard.getLeaderboardTitle())
                    .setColor(EmbedColor.DEFAULT.getColor());

            if(leaderboard.getScoreData().isEmpty()) {
                embed.setDescription("No score has been gained yet!\n" +
                        "To see how score works, use /score-help");
            } else {
                embed.setDescription("To see how score works, use /score-help");
            }

            for(Map.Entry<Long, Long> entry : leaderboard.getScoreData().entrySet()) {
                User carrier = BotStarter.getInstance().getBot().getUserById(entry.getKey()).join();
                embed.addField(
                        "#" + ++counter + " Carrier",
                        carrier.getMentionTag() + " - " + entry.getValue() + " Score"
                );
            }

            embeds.add(embed);
        }


        Optional<Message> messageOptional =
                channel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();
        if(messageOptional.isEmpty()) {
            channel.sendMessage(embeds);
        } else {
            messageOptional.get().createUpdater().removeAllEmbeds().addEmbeds(embeds).applyChanges().join();
        }
    }

    /**
     * Doesn't actually refresh the leaderboard, it just suggests that the leaderboard should be refreshed.
     * Cooldown for a refresh is {@value REFRESH_COOLDOWN} seconds.
     */
    public boolean refreshLeaderboard() {
        if(lastRefresh.plusSeconds(REFRESH_COOLDOWN - 1L).isAfter(Instant.now())) {
            return false;
        }

        this.lastRefresh = Instant.now();
        logger.debug("Leaderboard refresh started!");

        for(Long serverId : new Long[]{IdList.SERVER.getId(), IdList.SERVER.getTestId()}) {
            Optional<ServerTextChannel> dungeonChannel =
                    BotStarter.getInstance().getBot().getServerTextChannelById(IdList.DUNGEON_LEADERBOARD_CHANNEL.getLocalId(serverId));
            dungeonChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel,
                    List.of(
                            new Leaderboard("Leaderboard | Dungeon-Carries",
                                    ConnectionService.getInstance().getDungeonLeaderboard()),
                            new Leaderboard("Leaderboard | Dungeon-Carries (all-time)",
                                    ConnectionService.getInstance().getAlltimeDungeonLeaderboard()),
                            new Leaderboard("Leaderboard | Dungeon-Carries (event)",
                                    ConnectionService.getInstance().getEventDungeonLeaderboard()))));

            Optional<ServerTextChannel> slayerChannel =
                    BotStarter.getInstance().getBot().getServerTextChannelById(IdList.SLAYER_LEADERBOARD_CHANNEL.getLocalId(serverId));
            slayerChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel,
                    List.of(
                            new Leaderboard("Leaderboard | Slayer-Carries",
                                    ConnectionService.getInstance().getSlayerLeaderboard()),
                            new Leaderboard("Leaderboard | Slayer-Carries (all-time)",
                                    ConnectionService.getInstance().getAlltimeSlayerLeaderboard()),
                            new Leaderboard("Leaderboard | Slayer-Carries (event)",
                                    ConnectionService.getInstance().getEventSlayerLeaderboard()))));

            Optional<ServerTextChannel> kuudraChannel =
                    BotStarter.getInstance().getBot().getServerTextChannelById(IdList.KUUDRA_LEADERBOARD_CHANNEL.getLocalId(serverId));
            kuudraChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel,
                    List.of(
                            new Leaderboard("Leaderboard | Kuudra-Carries",
                                    ConnectionService.getInstance().getKuudraLeaderboard()),
                            new Leaderboard("Leaderboard | Kuudra-Carries (all-time)",
                                    ConnectionService.getInstance().getAlltimeKuudraLeaderboard()))));
        }

        return true;
    }
}