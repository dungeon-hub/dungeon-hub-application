package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.QueueConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.*;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.application.service.PermissionService;
import me.taubsie.dungeonhub.common.enums.QueueStep;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueUpdateModel;
import me.taubsie.dungeonhub.common.model.score.LoggedCarryModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.MessageComponentCreateEvent;
import org.javacord.api.interaction.MessageComponentInteraction;
import org.javacord.api.listener.interaction.MessageComponentCreateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageComponentListener implements MessageComponentCreateListener {
    private static final Logger logger = LoggerFactory.getLogger(MessageComponentListener.class);

    @Override
    public void onComponentCreate(MessageComponentCreateEvent messageComponentCreateEvent) {
        Optional<Server> server = messageComponentCreateEvent.getMessageComponentInteraction().getServer();

        if (server.isEmpty()) {
            ApplicationService.getInstance().respondWithError(messageComponentCreateEvent.getInteraction(),
                    new MustBeServerException());
            return;
        }

        switch (messageComponentCreateEvent.getMessageComponentInteraction().getCustomId().strip().toLowerCase()) {
            case "discard" -> discard(messageComponentCreateEvent.getMessageComponentInteraction(), server.get());
            case "send_log" -> sendLog(messageComponentCreateEvent.getMessageComponentInteraction());

            case "deny" -> deny(messageComponentCreateEvent.getMessageComponentInteraction(), server.get());
            case "accept_log" -> acceptLog(messageComponentCreateEvent.getMessageComponentInteraction(), server.get());

            case "reload_playerdata", "show_flagged_banned", "show_excluded" -> {
                // This is here so that the default block doesn't get executed and the lambda expressions that are set
                // on creation work
            }

            default -> ApplicationService.getInstance()
                    .respondWithError(messageComponentCreateEvent.getInteraction(), new UnknownCommandException());
        }
    }

    private void discard(MessageComponentInteraction messageComponentInteraction, Server server) {
        Optional<TextChannel> channel = messageComponentInteraction.getChannel();

        if (channel.isEmpty()) {
            ApplicationService.getInstance().respondWithError(messageComponentInteraction,
                    new ChannelNotFoundException());
            return;
        }

        Optional<CarryQueueModel> carryQueue = QueueConnection.getInstance()
                .getCarryQueueByRelatedId(channel.get().getId())
                .map(Collection::stream)
                .flatMap(Stream::findFirst);

        if (carryQueue.isEmpty()) {
            messageComponentInteraction.createImmediateResponder()
                    .setContent("Carry isn't in queue anymore. Please log this again!")
                    .setFlags(MessageFlag.EPHEMERAL)
                    .respond()
                    .join();

            messageComponentInteraction.getMessage().delete("Carry not in queue - weird...");
            return;
        }

        if (!PermissionService.getInstance().mayManageServices(messageComponentInteraction.getUser(), server)
                && carryQueue.get().getCarrier().getId() != messageComponentInteraction.getUser().getId()) {
            ApplicationService.getInstance().respondWithError(messageComponentInteraction,
                    new MissingPermissionException());
            return;
        }

        messageComponentInteraction.createImmediateResponder()
                .setContent("Log discarded!")
                .setFlags(MessageFlag.EPHEMERAL)
                .respond()
                .join();

        QueueConnection.getInstance().deleteQueue(carryQueue.get().getId());

        messageComponentInteraction.getMessage().delete().join();
    }

    private void sendLog(MessageComponentInteraction messageComponentInteraction) {
        Optional<TextChannel> channel = messageComponentInteraction.getChannel();

        if (channel.isEmpty()) {
            ApplicationService.getInstance().respondWithError(messageComponentInteraction,
                    new ChannelNotFoundException());
            return;
        }

        Optional<CarryQueueModel> carryQueue = QueueConnection.getInstance()
                .getCarryQueueByRelatedId(channel.get().getId())
                .map(Collection::stream)
                .flatMap(Stream::findFirst);

        if (carryQueue.isEmpty()) {
            ApplicationService.getInstance().respondWithError(messageComponentInteraction,
                    new CommandExecutionException("Carry isn't in queue anymore. Please discard and log this again!"));
            return;
        }

        if (carryQueue.get().getCarrier().getId() != messageComponentInteraction.getUser().getId()) {
            ApplicationService.getInstance().respondWithError(messageComponentInteraction,
                    new MissingPermissionException());
            return;
        }

        CarryQueueUpdateModel updateModel = new CarryQueueUpdateModel()
                .setQueueStep(QueueStep.TRANSCRIPT);

        messageComponentInteraction.respondLater(true)
                .thenAccept(updater -> {
                    Optional<CarryQueueModel> carryQueueModel = QueueConnection.getInstance()
                            .updateQueue(carryQueue.get().getId(), updateModel);

                    if (carryQueueModel.isEmpty()) {
                        updater.setContent("Couldn't log this ticket. Please contact an administrator.").update();
                        logger.error("Error logging ticket '{}'.", channel.get().getId());

                        return;
                    }

                    updater.setContent(
                            "**Thank you for your service. Your carry will be sent to the staff team for " +
                                    "review once the " +
                                    "ticket is closed.**\n" +
                                    "**You will be notified once it has been reviewed.**"
                    ).update();

                    channel.get().sendMessage(
                            ApplicationService.getInstance()
                                    .loadEmbedFromCarryQueue(carryQueueModel.get())
                                    .setDescription("This will get sent when the ticket is deleted.\n" +
                                            "If the client doesn't want any more carries, please delete this ticket.")
                                    .setTitle("Carry logged")
                    );

                    messageComponentInteraction.getMessage().delete();
                });
    }

    private void deny(MessageComponentInteraction messageComponentInteraction, Server server) {
        messageComponentInteraction.acknowledge();

        Message message = messageComponentInteraction.getMessage();

        for(CarryQueueModel queueModel : QueueConnection.getInstance()
                .getCarryQueuesByQueueStep(QueueStep.APPROVING)
                .orElse(new HashSet<>())) {
            User carrier = messageComponentInteraction.getApi().getUserById(queueModel.getCarrier().getId()).join();

            try {
                PrivateChannel privateChannel = carrier.openPrivateChannel().join();

                privateChannel.sendMessage("Your log was denied by " + messageComponentInteraction.getUser().getMentionTag() + ".",
                        ApplicationService.getInstance()
                                .loadEmbedFromCarryQueue(queueModel)
                                .setColor(EmbedColor.NEGATIVE.getColor())
                                .setTitle("Information"));
            }
            catch (CompletionException | NullPointerException ignored) {
                //ignored since that just means bot is blocked or similar
            }

            ServerProperty.SCORE_LOGS_CHANNEL
                    .getValue(server.getId())
                    .flatMap(server::getTextChannelById)
                    .ifPresent(serverTextChannel -> serverTextChannel.sendMessage(
                            ApplicationService.getInstance()
                                    .loadEmbedFromCarryQueue(queueModel)
                                    .setColor(EmbedColor.NEGATIVE.getColor())
                                    .setTitle("Carry denied")
                                    .addInlineField("Denied by", messageComponentInteraction.getUser().getMentionTag())
                    ));

            logger.debug("Carry denied: {}", queueModel);

            QueueConnection.getInstance().deleteQueue(queueModel.getId());
        }

        message.delete();
    }

    private void acceptLog(MessageComponentInteraction messageComponentInteraction, Server server) {
        messageComponentInteraction.acknowledge();
        Message message = messageComponentInteraction.getMessage();

        for(CarryQueueModel queueModel : QueueConnection.getInstance()
                .getCarryQueueByRelatedIdAndQueueStep(message.getId(), QueueStep.APPROVING)
                .orElse(new HashSet<>())) {
            CarryQueueUpdateModel updateModel = new CarryQueueUpdateModel()
                    .setApprover(messageComponentInteraction.getUser().getId());

            Optional<LoggedCarryModel> loggedCarryModel = QueueConnection.getInstance()
                    .logQueue(queueModel.getId(), updateModel);

            if(loggedCarryModel.isEmpty()) {
                return;
            }

            Long updatedScore = loggedCarryModel.stream()
                    .map(LoggedCarryModel::scoreModels)
                    .flatMap(Collection::stream)
                    .filter(scoreModel -> scoreModel.getScoreType().equals(ScoreType.DEFAULT))
                    .findFirst()
                    .map(ScoreModel::getScoreAmount)
                    .orElseGet(() -> ScoreConnection.getInstance(queueModel.getCarryType())
                            .getScore(queueModel.getCarrier().getId())
                            .map(ScoreModel::getScoreAmount)
                            .orElse(0L));
            long gainedScore = queueModel.calculateScore();

            User carrier = messageComponentInteraction.getApi().getUserById(queueModel.getCarrier().getId()).join();

            try {
                PrivateChannel privateChannel = carrier.openPrivateChannel().join();

                privateChannel.sendMessage(
                        "Your carry was logged!\n\n" +

                                "**Score gained:** " + gainedScore +
                                "\n**Your Updated Score:** " + updatedScore,
                        ApplicationService.getInstance()
                                .loadEmbedFromCarryQueue(queueModel)
                                .setTitle("Information")
                );
            }
            catch (CompletionException | NullPointerException ignored) {
                //ignored since that just means that the bot is blocked
            }

            try {
                loggedCarryModel.get().carryModel()
                        .getCarryType()
                        .getLogChannel()
                        .flatMap(server::getTextChannelById)
                        .ifPresent(serverTextChannel -> serverTextChannel.sendMessage(
                                ApplicationService.getInstance()
                                        .loadEmbedFromCarry(loggedCarryModel.get().carryModel())
                                        .setTitle("Carry accepted.")
                                        .setColor(EmbedColor.POSITIVE.getColor())
                        ));
            }
            catch (CompletionException | NullPointerException ignored) {
                //ignored since it's not required to be logged
            }

            logger.debug("Carry logged: {}", queueModel);
        }

        LeaderboardService.getInstance().refreshLeaderboard();

        message.delete();
    }
}