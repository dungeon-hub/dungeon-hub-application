package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.classes.Leaderboard;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.messages.LeaderboardMessage;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.*;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
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
        if (instance == null) {
            instance = new LeaderboardService();
        }

        return instance;
    }

    public void registerPageListener(Message message, CarryType carryType, ScoreType scoreType) {
        new LeaderboardMessage(1, message.getChannel().getId(), message.getId(), carryType, scoreType);
    }

    public Set<String> getAvailableTypes() {
        return getLeaderboards().keySet();
    }

    public String getLeaderboardTitle(CarryType carryType, ScoreType scoreType) {
        return "Leaderboard | " + carryType.getDisplayName() + "-Carries" + scoreType.getLeaderboardSuffix();
    }

    public EmbedBuilder getLeaderboardEmbed(String title, Map<Long, Long> score, int page) {
        String description = score.isEmpty() ? "No score has been gained yet!\n " : "";
        description += "To see how score works, use /score-help";

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setTitle(title)
                .setDescription(description)
                .setColor(EmbedColor.DEFAULT.getColor());

        int counter = CarryLogService.getInstance().getOffsetFromPageNumber(page);

        for(Map.Entry<Long, Long> entry : score.entrySet()) {
            embed.addField(
                    "#" + ++counter + " Carrier",
                    "<@" + entry.getKey() + "> - " + entry.getValue() + " Score"
            );
        }

        return embed;
    }

    public EmbedBuilder getLeaderboardEmbed(String title, Map<Long, Long> score, int page, int maxPage) {
        return getLeaderboardEmbed(title, score, page)
                .setFooter("Page " + page + "/" + maxPage);
    }

    private Map<String, String> getLeaderboards() {
        Map<String, String> leaderboards = new HashMap<>();

        leaderboards.put("dungeons", "Leaderboard | Dungeon-Carries");
        leaderboards.put("slayer", "Leaderboard | Slayer-Carries");
        leaderboards.put("kuudra", "Leaderboard | Kuudra-Carries");
        leaderboards.put("alltime-dungeons", "Leaderboard | Dungeon-Carries (all-time)");
        leaderboards.put("alltime-slayer", "Leaderboard | Slayer-Carries (all-time)");
        leaderboards.put("alltime-kuudra", "Leaderboard | Kuudra-Carries (all-time)");
        leaderboards.put("event-slayer", "Leaderboard | Slayer-Carries (event)");
        leaderboards.put("event-dungeons", "Leaderboard | Dungeon-Carries (event)");

        return leaderboards;
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
            embeds.add(leaderboard.getEmbed());
        }

        Optional<Message> messageOptional =
                channel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();
        if (messageOptional.isEmpty()) {
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
        if (lastRefresh.plusSeconds(REFRESH_COOLDOWN - 1L).isAfter(Instant.now())) {
            return false;
        }

        this.lastRefresh = Instant.now();
        logger.debug("Leaderboard refresh started!");

        for(CarryType carryType : DungeonHubConnection.getInstance().loadCarryTypes()) {
            Optional<ServerTextChannel> leaderboardChannel = carryType.getLeaderboardChannel()
                    .flatMap(id -> BotStarter.getInstance().getBot().getServerTextChannelById(id));

            List<Leaderboard> leaderboards = new ArrayList<>();

            for(ScoreType scoreType : ScoreType.values()) {
                leaderboards.add(new Leaderboard(
                        getLeaderboardTitle(carryType, scoreType),
                        DungeonHubConnection.getInstance().getLeaderboardData(carryType, scoreType, 1)
                ));
            }

            leaderboardChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel,
                    leaderboards));
        }

        return true;
    }
}