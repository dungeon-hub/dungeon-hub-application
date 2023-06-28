package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.classes.ServerProperty;
import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.exceptions.ChannelNotFoundException;
import me.taubsie.carrylogs.application.exceptions.MissingPermissionException;
import me.taubsie.carrylogs.application.exceptions.MustBeServerException;
import me.taubsie.carrylogs.application.exceptions.UnknownCommandException;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import me.taubsie.carrylogs.application.service.PermissionService;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryInformation;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.MessageComponentCreateEvent;
import org.javacord.api.interaction.Interaction;
import org.javacord.api.interaction.MessageComponentInteraction;
import org.javacord.api.listener.interaction.MessageComponentCreateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletionException;

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

            default -> ApplicationService.getInstance()
                    .respondWithError(messageComponentCreateEvent.getInteraction(), new UnknownCommandException());
        }
    }

    private void discard(MessageComponentInteraction messageComponentInteraction, Server server) {
        Optional<TextChannel> channel = messageComponentInteraction.getChannel();

        if (channel.isEmpty()) {
            ApplicationService.getInstance().respondWithError((Interaction) messageComponentInteraction,
                    new ChannelNotFoundException());
            return;
        }

        CarryInformation carryInformation = BotStarter.getInstance().getCarryInformation().get(channel.get().getId());

        if (carryInformation == null) {
            messageComponentInteraction.createImmediateResponder()
                    .setContent("Carry Information isn't in cache anymore. Please log this again!")
                    .setFlags(MessageFlag.EPHEMERAL)
                    .respond()
                    .join();

            messageComponentInteraction.getMessage().delete("Carry Information not in cache, carrier took too long");
            return;
        }

        if (!PermissionService.getInstance().mayManageServices(messageComponentInteraction.getUser(), server)
                && carryInformation.getCarrier() != messageComponentInteraction.getUser().getId()) {
            ApplicationService.getInstance().respondWithError((Interaction) messageComponentInteraction,
                    new MissingPermissionException());
            return;
        }

        messageComponentInteraction.createImmediateResponder()
                .setContent("Log discarded!")
                .setFlags(MessageFlag.EPHEMERAL)
                .respond()
                .join();

        BotStarter.getInstance().getCarryInformation().remove(channel.get().getId());

        messageComponentInteraction.getMessage().delete().join();
    }

    private void sendLog(MessageComponentInteraction messageComponentInteraction) {
        Optional<TextChannel> channel = messageComponentInteraction.getChannel();

        if (channel.isEmpty()) {
            ApplicationService.getInstance().respondWithError((Interaction) messageComponentInteraction,
                    new ChannelNotFoundException());
            return;
        }

        CarryInformation carryInformation = BotStarter.getInstance().getCarryInformation().get(channel.get().getId());

        if (carryInformation == null) {
            messageComponentInteraction.createImmediateResponder()
                    .setContent("Carry Information isn't loaded. Please discard and log this again!")
                    .setFlags(MessageFlag.EPHEMERAL)
                    .respond()
                    .join();
            return;
        }

        if (carryInformation.getCarrier() != messageComponentInteraction.getUser().getId()) {
            ApplicationService.getInstance().respondWithError((Interaction) messageComponentInteraction,
                    new MissingPermissionException());
            return;
        }

        BotStarter.getInstance().getCarryInformation().remove(channel.get().getId());

        messageComponentInteraction.respondLater(true)
                .thenAccept(updater -> {
                    DungeonHubConnection.getInstance().addToLogQueue(channel.get().getId(), carryInformation);

                    updater.setContent(
                            "**Thank you for your service. Your carry will be sent to the staff team for " +
                                    "review once the " +
                                    "ticket is closed.**\n" +
                                    "**You will be notified once it has been reviewed.**"
                    ).update();

                    channel.get().sendMessage(
                            ApplicationService.getInstance()
                                    .loadEmbedFromCarryInformation(carryInformation)
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

        for(CarryInformation carryInformation :
                DungeonHubConnection.getInstance().getFromLogApprovingQueue(message.getId())) {
            User carrier = messageComponentInteraction.getApi().getUserById(carryInformation.getCarrier()).join();

            try {
                PrivateChannel privateChannel = carrier.openPrivateChannel().join();

                privateChannel.sendMessage("Your log was denied by " + messageComponentInteraction.getUser().getMentionTag() + ".",
                        ApplicationService.getInstance()
                                .loadEmbedFromCarryInformation(carryInformation)
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
                                    .loadEmbedFromCarryInformation(carryInformation)
                                    .setColor(EmbedColor.NEGATIVE.getColor())
                                    .setTitle("Carry denied")
                                    .addInlineField("Denied by", messageComponentInteraction.getUser().getMentionTag())
                    ));

            logger.debug("Carry denied: {}", carryInformation);
        }

        DungeonHubConnection.getInstance().removeFromApprovingQueue(message.getId());

        message.delete();
    }

    private void acceptLog(MessageComponentInteraction messageComponentInteraction, Server server) {
        messageComponentInteraction.acknowledge();
        Message message = messageComponentInteraction.getMessage();

        for(CarryInformation carryInformation :
                DungeonHubConnection.getInstance().getFromLogApprovingQueue(message.getId())) {
            carryInformation.setApprover(messageComponentInteraction.getUser().getId());

            long updatedScore = DungeonHubConnection.getInstance().logCarry(carryInformation);
            long gainedScore = carryInformation.calculateScore();

            User carrier = messageComponentInteraction.getApi().getUserById(carryInformation.getCarrier()).join();

            try {
                PrivateChannel privateChannel = carrier.openPrivateChannel().join();

                privateChannel.sendMessage(
                        "Your carry was logged!\n\n" +

                                "**Score gained:** " + gainedScore +
                                "\n**Your Updated Score:** " + updatedScore,
                        ApplicationService.getInstance()
                                .loadEmbedFromCarryInformation(carryInformation)
                                .setTitle("Information")
                );
            }
            catch (CompletionException | NullPointerException ignored) {
                //ignored since that just means that the bot is blocked
            }

            try {
                carryInformation.getCarryType()
                        .getLogChannel()
                        .flatMap(server::getTextChannelById)
                        .ifPresent(serverTextChannel -> serverTextChannel.sendMessage(
                                ApplicationService.getInstance()
                                        .loadEmbedFromCarryInformation(carryInformation)
                                        .setTitle("Carry accepted.")
                                        .setColor(EmbedColor.POSITIVE.getColor())
                        ));
            }
            catch (CompletionException | NullPointerException ignored) {
                //ignored since it's not required to be logged
            }

            logger.debug("Carry logged: {}", carryInformation);
        }

        LeaderboardService.getInstance().refreshLeaderboard();

        DungeonHubConnection.getInstance().removeFromApprovingQueue(message.getId());

        message.delete();
    }
}