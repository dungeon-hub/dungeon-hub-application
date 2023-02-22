package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.start.StartBot;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.awt.*;
import java.sql.Time;
import java.time.Instant;
import java.util.*;

public class ApplicationService
{
    private static ApplicationService instance;
    private Instant lastRefresh;

    public static ApplicationService getInstance()
    {
        if (instance == null)
        {
            instance = new ApplicationService();
        }

        return instance;
    }

    private ApplicationService()
    {
        this.lastRefresh = Instant.now().minusSeconds(60 * 5);

        new Timer().scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                System.out.println("Leaderboard refresh suggested!");

                refreshLeaderboard();
            }
        }, new Time(System.currentTimeMillis() + 15000), 1000 * 60 * 5);
    }

    public EmbedBuilder getEmbed()
    {
        return getEmbed(Instant.now());
    }

    public EmbedBuilder getEmbed(Instant time)
    {
        return new EmbedBuilder()
                .setTimestamp(time)
                .setFooter("discord.gg/dungeons • made by Taubsie#0911");
    }

    public boolean isCarryType(String carryType) {
        return switch(carryType) {
            case "Completion",
                    "S",
                    "S+",
                    "Floor 1",
                    "Floor 2",
                    "Floor 3",
                    "Floor 4",
                    "Floor 5",
                    "Floor 6",
                    "Floor 7",
                    "Basic",
                    "Hot",
                    "Burning",
                    "Fiery",
                    "Infernal",
                    "Tier 2",
                    "Tier 3",
                    "Tier 4" -> true;
            default -> false;
        };
    }

    /**
     * Doesn't actually refresh the leaderboard, it just suggests that the leaderboard should be refreshed.
     */
    public void refreshLeaderboard()
    {
        if (lastRefresh.plusSeconds(60 * 5 - 15).isAfter(Instant.now()))
        {
            return;
        }
        this.lastRefresh = Instant.now();
        System.out.println("Leaderboard refresh started!");

        for (Long serverId : new Long[]{IdList.SERVER.getId(), IdList.SERVER.getTestId()})
        {
            Optional<ServerTextChannel> dungeonChannel = StartBot.getInstance().getBot().getServerTextChannelById(IdList.DUNGEON_LEADERBOARD_CHANNEL.getLocalId(serverId));
            dungeonChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel, "Leaderboard | Dungeon-Carries", ConnectionService.getInstance().getDungeonLeaderboard()));

            Optional<ServerTextChannel> slayerChannel = StartBot.getInstance().getBot().getServerTextChannelById(IdList.SLAYER_LEADERBOARD_CHANNEL.getLocalId(serverId));
            slayerChannel.ifPresent(channel -> refreshLeaderboardInChannel(channel, "Leaderboard | Slayer-Carries", ConnectionService.getInstance().getSlayerLeaderboard()));
        }
    }

    private void refreshLeaderboardInChannel(ServerTextChannel channel, String leaderboardTitle, Map<Long, Long> score)
    {
        int counter = 0;
        EmbedBuilder embed = getEmbed()
                .setTitle(leaderboardTitle)
                .setColor(new Color(255, 255, 255));

        if (score.isEmpty())
        {
            embed.setDescription("No score has been gained yet!\n" +
                    "To see how score works, use /score-help");
        }
        else
        {
            embed.setDescription("To see how score works, use /score-help");
        }

        for (Map.Entry<Long, Long> entry : score.entrySet())
        {
            User carrier = StartBot.getInstance().getBot().getUserById(entry.getKey()).join();
            embed.addField(
                    "#" + ++counter + " Carrier",
                    carrier.getMentionTag() + " - " + entry.getValue() + " Score"
            );
        }

        Optional<Message> messageOptional = channel.getMessagesAsStream().filter(message -> message.getAuthor().isYourself()).findFirst();
        if (messageOptional.isEmpty())
        {
            channel.sendMessage(embed);
        }
        else
        {
            messageOptional.get().createUpdater().removeAllEmbeds().addEmbed(embed).applyChanges().join();
        }
    }
}