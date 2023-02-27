package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.OnStart;
import me.taubsie.carrylogs.ProgramOrigin;
import me.taubsie.carrylogs.StartupListener;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.start.BotStarter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.awt.*;
import java.sql.Time;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

@OnStart
public class LeaderboardService implements StartupListener {
    private static final Logger logger = LogManager.getLogger(LeaderboardService.class);
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

    private void refreshLeaderboardInChannel(ServerTextChannel channel, String leaderboardTitle,
                                             Map<Long, Long> score) {
        int counter = 0;
        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setTitle(leaderboardTitle)
                .setColor(new Color(255, 255, 255));

        if(score.isEmpty()) {
            embed.setDescription("No score has been gained yet!\n" +
                    "To see how score works, use /score-help");
        } else {
            embed.setDescription("To see how score works, use /score-help");
        }

        for(Map.Entry<Long, Long> entry : score.entrySet()) {
            User carrier = BotStarter.getInstance().getBot().getUserById(entry.getKey()).join();
            embed.addField(
                    "#" + ++counter + " Carrier",
                    carrier.getMentionTag() + " - " + entry.getValue() + " Score"
            );
        }

        Optional<Message> messageOptional =
                channel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();
        if(messageOptional.isEmpty()) {
            channel.sendMessage(embed);
        } else {
            messageOptional.get().createUpdater().removeAllEmbeds().addEmbed(embed).applyChanges().join();
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
        logger.info("Leaderboard refresh started!");

        for(Long serverId : new Long[]{IdList.SERVER.getId(), IdList.SERVER.getTestId()}) {
            Optional<ServerTextChannel> dungeonChannel =
                    BotStarter.getInstance().getBot().getServerTextChannelById(IdList.DUNGEON_LEADERBOARD_CHANNEL.getLocalId(serverId));
            dungeonChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel, "Leaderboard | Dungeon-Carries",
                    ConnectionService.getInstance().getDungeonLeaderboard()));

            Optional<ServerTextChannel> slayerChannel =
                    BotStarter.getInstance().getBot().getServerTextChannelById(IdList.SLAYER_LEADERBOARD_CHANNEL.getLocalId(serverId));
            slayerChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel, "Leaderboard | Slayer-Carries",
                    ConnectionService.getInstance().getSlayerLeaderboard()));

            Optional<ServerTextChannel> kuudraChannel =
                    BotStarter.getInstance().getBot().getServerTextChannelById(IdList.KUUDRA_LEADERBOARD_CHANNEL.getLocalId(serverId));
            kuudraChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel, "Leaderboard | Kuudra-Carries",
                    ConnectionService.getInstance().getKuudraLeaderboard()));
        }

        return true;
    }
}