package me.taubsie.dungeonhub.application.service;

import me.taubsie.dungeonhub.application.classes.ServerProperty;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public class ProfileModerationService {
    private static final Logger logger = LoggerFactory.getLogger(ProfileModerationService.class);

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

    public static ProfileModerationService getInstance() {
        if (instance == null) {
            instance = new ProfileModerationService();
        }

        return instance;
    }

    public void handleUserBan(Server server, User user, User executor, String reason) {
        dmBannedPerson(server, user, reason);

        executeBan(server, user, executor, reason);
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
}