package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.exceptions.FailedToLoadException;
import net.codebox.homoglyph.Homoglyph;
import net.codebox.homoglyph.HomoglyphBuilder;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
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

public class ProfileModerationService {
    private static final String[] forbiddenUsernames = new String[]{
            "Captcha.bot",
            "YAGPDB",
            "Giveaway Bot",
            "Giveaway Boat",
            "Ticket Tool",
            "Dyno",
            "Carl-bot",
            "Xenon",
            "SkyKings",
            "SkyHelper",
            "MEE6",
            "Dungeon Hub",
            "Wick",
            "Lunar Client",
            "Badlion"
    };
    private static final Long[] excludedIds = new Long[]{
            727320030462869515L,
            703035551330205716L,
            599475365471059978L
    };
    private static ProfileModerationService instance;
    private final Homoglyph homoglyph;

    ProfileModerationService() {
        try {
            this.homoglyph = HomoglyphBuilder.build();
        } catch(IOException ioException) {
            throw new FailedToLoadException(ioException);
        }
    }

    public static ProfileModerationService getInstance() {
        if(instance == null) {
            instance = new ProfileModerationService();
        }

        return instance;
    }

    @Nullable
    public String checkUserName(String userName) {
        List<Homoglyph.SearchResult> searchResults = homoglyph.search(userName, forbiddenUsernames);

        if(searchResults.isEmpty()) {
            return null;
        }

        return searchResults.stream().map(searchResult -> searchResult.match).collect(Collectors.joining("; "));
    }

    public void handleUserBan(Server server, User user, String reason) {
        String unbanForm = ServerService.getInstance().getServerProperty(server.getId(), ServerProperty.UNBAN_FORM);

        try {
            String message = ServerService.getInstance()
                    .getServerProperty(server.getId(), ServerProperty.PROFILE_MODERATION_BAN_MESSAGE)
                    .replace("%server%", server.getName())
                    .replace("%form%", unbanForm);

            user.openPrivateChannel().join()
                    .sendMessage(message, ActionRow.of(Button.link(unbanForm, "Appeal"))).join();
        } catch(Exception exception) {
            exception.printStackTrace();
        }

        server.banUser(user, Duration.of(6, ChronoUnit.DAYS), "Bad username: " + reason);

        Optional<ServerTextChannel> logsChannel = server.getTextChannelById(ServerService.getInstance().getServerProperty(server.getId(), ServerProperty.MODERATION_LOGS_CHANNEL));

        logsChannel.ifPresent(serverTextChannel -> serverTextChannel.sendMessage("User " + user.getMentionTag() + " " +
                "got banned because of a bad username:\n" + reason));
    }

    public boolean isOverwritten(long userId) {
        return Arrays.stream(excludedIds).anyMatch(id -> id == userId);
    }

    public boolean isVerified(User user, Server server) {
        return user.getRoles(server)
                .stream()
                .anyMatch(role -> role.getId() == IdList.VERIFIED_ROLE.getLocalId(server.getId())
                        || role.getId() == IdList.ALT_VERIFIED_ROLE.getLocalId(server.getId())
                );
    }

    public boolean isExcluded(User user, Server server) {
        return user.isBot() || user.isBotOwnerOrTeamMember() || isOverwritten(user.getId()) || isVerified(user, server);
    }

    public boolean isExcluded(User user) {
        return user.isBot() || user.isBotOwnerOrTeamMember() || isOverwritten(user.getId());
    }
}