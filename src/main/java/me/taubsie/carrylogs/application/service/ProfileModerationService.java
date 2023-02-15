package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.enums.IdList;
import net.codebox.homoglyph.Homoglyph;
import net.codebox.homoglyph.HomoglyphBuilder;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProfileModerationService
{
    private static final String[] forbiddenUsernames = new String[]{
            "Captcha.bot",
            "Dyno",
            "Carl-bot",
            "Xenon",
            "SkyKings",
            "SkyHelper",
            "MEE6",
            "Dungeon Hub Bot",
            "Wick"
    };
    private static final Long[] excludedIds = new Long[]{
            727320030462869515L,
            703035551330205716L
    };
    private static ProfileModerationService instance;
    private final Homoglyph homoglyph;

    ProfileModerationService()
    {
        try
        {
            this.homoglyph = HomoglyphBuilder.build();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static ProfileModerationService getInstance()
    {
        if (instance == null)
        {
            instance = new ProfileModerationService();
        }

        return instance;
    }

    @Nullable
    public String checkUserName(String userName)
    {
        List<Homoglyph.SearchResult> searchResults = homoglyph.search(userName, forbiddenUsernames);

        if (searchResults.isEmpty())
        {
            return null;
        }

        return searchResults.stream().map(searchResult -> searchResult.match).collect(Collectors.joining("; "));
    }

    public void handleUserBan(Server server, User user, String reason)
    {
        try
        {
            user.openPrivateChannel().join().sendMessage("You got banned from `" + server.getName() + "` because of a" +
                    " suspicious user profile.\nIf you think this might be a mistake, please appeal at: " +
                    "https://forms.gle/rfQAJueSoyQno1gD6");
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }

        server.banUser(user, Duration.of(6, ChronoUnit.DAYS), "Bad username: " + reason);

        Optional<ServerTextChannel> logsChannel =
                server.getTextChannelById(IdList.MODERATION_LOGS_CHANNEL.getLocalId(server.getId()));
        logsChannel.ifPresent(serverTextChannel -> serverTextChannel.sendMessage("User " + user.getMentionTag() + " " +
                "got banned because of a bad username:\n" + reason));
    }

    public boolean isOverwritten(long userId)
    {
        return Arrays.stream(excludedIds).anyMatch(id -> id == userId);
    }

    public boolean isVerified(User user, Server server)
    {
        return user.getRoles(server)
                .stream()
                .anyMatch(role -> role.getId() == IdList.VERIFIED_ROLE.getLocalId(server.getId())
                        || role.getId() == IdList.ALT_VERIFIED_ROLE.getLocalId(server.getId())
                );
    }

    public boolean isExcluded(User user, Server server)
    {
        return user.isBot() || user.isBotOwnerOrTeamMember() || isOverwritten(user.getId()) || isVerified(user, server);
    }

    public boolean isExcluded(User user)
    {
        return user.isBot() || user.isBotOwnerOrTeamMember() || isOverwritten(user.getId());
    }
}