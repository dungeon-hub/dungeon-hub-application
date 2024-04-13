package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.application.enums.IdList;
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadException;
import net.codebox.homoglyph.Homoglyph;
import net.codebox.homoglyph.HomoglyphBuilder;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class ProfileModerationService {
    private static final Logger logger = LoggerFactory.getLogger(ProfileModerationService.class);

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
            "Lunar",
            "Badlion",
            "Hypixel",
            "Syntax",
            "Sеcurity",
            "Bouncr",
            "Base"
    };
    private static final Long[] excludedIds = new Long[]{
            727320030462869515L,
            703035551330205716L,
            599475365471059978L,
            678580255384141824L,
            633350165574451200L,
            577147388255272970L,
            1097692461452767272L,
            928744398571831359L,
            542229014681747456L,
            1059959722549190728L,
            1119562575458336828L
    };
    private static ProfileModerationService instance;
    private final Homoglyph homoglyph;

    ProfileModerationService() {
        try {
            this.homoglyph = HomoglyphBuilder.build();
        }
        catch (IOException ioException) {
            throw new FailedToLoadException(ioException);
        }
    }

    public static ProfileModerationService getInstance() {
        if (instance == null) {
            instance = new ProfileModerationService();
        }

        return instance;
    }

    @Nullable
    public String checkUserName(String userName) {
        List<Homoglyph.SearchResult> searchResults = homoglyph.search(userName, forbiddenUsernames);

        if (searchResults.isEmpty()) {
            return null;
        }

        return searchResults.stream().map(searchResult -> searchResult.match).collect(Collectors.joining("; "));
    }

    private boolean isBanDisabled(long serverId) {
        return ServerProperty.PROFILE_MODERATION_BAN.getValue(serverId)
                .map(s -> s.equalsIgnoreCase("false"))
                .orElse(Boolean.TRUE);
    }

    public void handleUserBan(Server server, User user, User executor, String reason) {
        dmBannedPerson(server, user, reason);

        executeBan(server, user, executor, reason);
    }

    public void handleUserBan(Server server, User user, String username) {
        dmBannedPerson(server, user);

        executeBan(server, user, username);
    }

    private void sendDm(User user, String message, @Nullable String unbanForm) {
        try {
            if (unbanForm == null) {
                user.openPrivateChannel().thenAccept(privateChannel -> privateChannel
                        .sendMessage(message));
            } else {
                user.openPrivateChannel().thenAccept(privateChannel -> privateChannel
                        .sendMessage(message, ActionRow.of(Button.link(unbanForm, "Appeal"))));
            }
        }
        catch (CompletionException completionException) {
            //ignored since this just means that the user couldn't be dmed
        }
    }

    private void dmBannedPerson(Server server, User user, String reason) {
        Optional<String> unbanForm = ServerProperty.UNBAN_FORM.getValue(server.getId());

        String message = ServerProperty.BAN_MESSAGE
                .getValue(server.getId())
                .orElse("You got banned from `%server%` because of \"" + reason + "\".\nIf you think this is a mistake, contact the administrators for further information.")
                .replace("%server%", server.getName())
                .replace("%reason", reason);

        if (unbanForm.isPresent()) {
            message = message.replace("%form%", unbanForm.get());
        }

        try {
            sendDm(user, message, unbanForm.orElse(null));
        }
        catch (Exception exception) {
            logger.error("Error when trying to handle user ban.", exception);
        }
    }

    private void dmBannedPerson(Server server, User user) {
        if (isBanDisabled(server.getId())) {
            return;
        }

        Optional<String> unbanForm = ServerProperty.UNBAN_FORM.getValue(server.getId());

        String message = ServerProperty.PROFILE_MODERATION_BAN_MESSAGE
                .getValue(server.getId())
                .orElse("You got banned from `%server%` because of a suspicious user profile.\nIf you think this is a mistake, contact the administrators for further information.")
                .replace("%server%", server.getName());

        if (unbanForm.isPresent()) {
            message = message.replace("%form%", unbanForm.get());
        }

        try {
            sendDm(user, message, unbanForm.orElse(null));
        }
        catch (Exception exception) {
            logger.error("Error when trying to handle user ban.", exception);
        }
    }

    private void executeBan(Server server, User user, String username) {
        ServerProperty.MODERATION_LOGS_CHANNEL
                .getValue(server.getId())
                .flatMap(s -> {
                    try {
                        return server.getTextChannelById(s);
                    }
                    catch (CompletionException completionException) {
                        return Optional.empty();
                    }
                })
                .ifPresent(serverTextChannel ->
                        serverTextChannel.sendMessage("User "
                                + user.getMentionTag()
                                + " got flagged because of a bad username:\n"
                                + username));

        if (isBanDisabled(server.getId())) {
            return;
        }

        server.banUser(user, Duration.of(6, ChronoUnit.DAYS), "Bad username: " + username);
    }

    private void executeBan(Server server, User user, User executor, String reason) {
        server.banUser(user, Duration.of(6, ChronoUnit.DAYS), "Executor: " + executor.getDiscriminatedName() + ", Reason:" + reason);

        ServerProperty.MODERATION_LOGS_CHANNEL
                .getValue(server.getId())
                .flatMap(s -> {
                    try {
                        return server.getTextChannelById(s);
                    }
                    catch (CompletionException completionException) {
                        return Optional.empty();
                    }
                })
                .ifPresent(serverTextChannel ->
                        new MessageBuilder()
                                .setAllowedMentions(new AllowedMentionsBuilder()
                                        .removeUser(executor.getId())
                                        .build())
                                .setContent("User "
                                        + user.getMentionTag()
                                        + " got banned by "
                                        + executor.getMentionTag()
                                        + " for reason:\n" + reason)
                                .send(serverTextChannel));
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
        return isExcluded(user) || isVerified(user, server);
    }

    public boolean isExcluded(User user) {
        return user.isBot() || user.isBotOwnerOrTeamMember() || isOverwritten(user.getId());
    }
}