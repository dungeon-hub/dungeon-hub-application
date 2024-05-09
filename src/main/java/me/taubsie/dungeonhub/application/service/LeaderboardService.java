package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.classes.Leaderboard;
import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.CarryTypeConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordServerConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.loader.OnStart;
import me.taubsie.dungeonhub.application.loader.StartupListener;
import me.taubsie.dungeonhub.application.messages.LeaderboardMessage;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.score.LeaderboardModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.common.model.server.DiscordServerModel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;

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

    public void registerPageListener(Message message, @Nullable CarryTypeModel carryType, ScoreType scoreType, Long userId) {
        new LeaderboardMessage(0, message.getChannel().getId(), message.getId(), carryType, scoreType, userId);
    }

    public String getLeaderboardDescription() {
        return "To see how score works, use `/help score`";
    }

    public String getLeaderboardTitle(@Nullable CarryTypeModel carryType, ScoreType scoreType) {
        if (carryType == null) {
            return "Leaderboard | Total score" + scoreType.getLeaderboardSuffix();
        }

        return "Leaderboard | " + carryType.getDisplayName() + "-Carries" + scoreType.getLeaderboardSuffix();
    }

    public EmbedBuilder getLeaderboardEmbed(String title, @Nullable LeaderboardModel leaderboardModel) {
        if (leaderboardModel == null) {
            return getEmptyLeaderboardEmbed(title);
        }

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setTitle(title)
                .setDescription(getLeaderboardDescription())
                .setColor(EmbedColor.DEFAULT.getColor());

        int counter = DungeonHubService.getInstance().getOffsetFromPageNumber(leaderboardModel.getPage());

        for (ScoreModel score : leaderboardModel.getScores()) {
            embed.addField(
                    "#" + ++counter + " Carrier",
                    getPlayerScore(score)
            );
        }

        leaderboardModel.getPlayerScore().ifPresent(playerScore ->
                leaderboardModel.getPlayerPosition().filter(pos -> pos != -1).ifPresent(position ->
                        embed.addField(
                                "__**Your rank:**__ #" + (position + 1),
                                getPlayerScore(playerScore)
                        )
                )
        );

        return embed;
    }

    public String getPlayerScore(ScoreModel score) {
        return "<@" + score.getCarrier().getId() + "> - " + score.getScoreAmount() + " Score";
    }

    public EmbedBuilder getEmptyLeaderboardEmbed(String title) {
        return ApplicationService.getInstance()
                .getEmbed()
                .setTitle(title)
                .setColor(EmbedColor.NEGATIVE.getColor())
                .setDescription("No score has been gained yet!\n" + getLeaderboardDescription());
    }

    public long getNextPossibleRefresh() {
        return lastRefresh.plusSeconds(REFRESH_COOLDOWN).getEpochSecond();
    }

    @Override
    public void onStart() {
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

        for (Leaderboard leaderboard : leaderboards) {
            EmbedBuilder embed = leaderboard.getEmbed()
                    .setFooter(null)
                    .setTimestamp(null);

            if (!leaderboard.isEmpty()) {
                embed.setDescription(null);
            }

            embeds.add(embed);
        }

        if (!embeds.isEmpty()) {
            if (!leaderboards.get(0).isEmpty()) {
                embeds.get(0).setDescription(getLeaderboardDescription());
            }

            embeds.get(embeds.size() - 1)
                    .setFooter(ApplicationService.getInstance().getFooter())
                    .setTimestamp(Instant.now());
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

        Map<ServerTextChannel, List<Leaderboard>> leaderboards = new HashMap<>();

        for (DiscordServerModel serverModel : DiscordServerConnection.getInstance().loadAllServers().orElse(new ArrayList<>())) {
            for (CarryTypeModel carryType :
                    CarryTypeConnection.getInstance(serverModel.getId()).getAllCarryTypes().orElse(List.of())) {
                Optional<ServerTextChannel> leaderboardChannel = carryType.getLeaderboardChannel()
                        .flatMap(id -> DiscordConnection.getInstance().getBot().getServerTextChannelById(id));

                if (leaderboardChannel.isEmpty()) {
                    continue;
                }

                for (ScoreType scoreType : ScoreType.values()) {
                    if (scoreType == ScoreType.EVENT && !carryType.isEventActive()) {
                        continue;
                    }

                    if (leaderboards.containsKey(leaderboardChannel.get())) {
                        leaderboards.get(leaderboardChannel.get()).add(new Leaderboard(
                                getLeaderboardTitle(carryType, scoreType),
                                ScoreConnection.getInstance(carryType).loadLeaderboard(scoreType, 0).orElse(null)
                        ));
                    } else {
                        leaderboards.put(leaderboardChannel.get(), new ArrayList<>(List.of(new Leaderboard(
                                getLeaderboardTitle(carryType, scoreType),
                                ScoreConnection.getInstance(carryType).loadLeaderboard(scoreType, 0).orElse(null)
                        ))));
                    }
                }
            }

            ServerProperty.TOTAL_SCORE_LEADERBOARD_CHANNEL.getValue(serverModel.getId())
                    .flatMap(id -> {
                        try {
                            return DiscordConnection.getInstance().getBot().getServerTextChannelById(id);
                        }
                        catch (CompletionException completionException) {
                            return Optional.empty();
                        }
                    }).ifPresent(leaderboardChannel -> {
                        for (ScoreType scoreType : ScoreType.values()) {
                            if (leaderboards.containsKey(leaderboardChannel)) {
                                leaderboards.get(leaderboardChannel).add(new Leaderboard(
                                        getLeaderboardTitle(null, scoreType),
                                        DiscordServerConnection.getInstance().loadTotalLeaderboard(serverModel.getId(), scoreType, 0).orElse(null)
                                ));
                            } else {
                                leaderboards.put(leaderboardChannel, new ArrayList<>(List.of(new Leaderboard(
                                        getLeaderboardTitle(null, scoreType),
                                        DiscordServerConnection.getInstance().loadTotalLeaderboard(serverModel.getId(), scoreType, 0).orElse(null)
                                ))));
                            }
                        }
                    });
        }

        leaderboards.forEach(this::refreshLeaderboardInChannel);

        return true;
    }
}