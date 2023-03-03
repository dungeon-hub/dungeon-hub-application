package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.enums.CarryType;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.service.LeaderboardService;
import me.taubsie.carrylogs.application.service.PermissionService;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.carrylogs.CarryInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.MessageComponentCreateEvent;
import org.javacord.api.listener.interaction.MessageComponentCreateListener;

import java.awt.Color;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageComponentListener implements MessageComponentCreateListener {
    private static final Logger logger = LogManager.getLogger(MessageListener.class);

    @Override
    public void onComponentCreate(MessageComponentCreateEvent messageComponentCreateEvent) {
        if(messageComponentCreateEvent.getMessageComponentInteraction().getServer().isEmpty()) {
            messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Use " +
                    "this on a server!").setFlags(MessageFlag.EPHEMERAL).respond().join();
            return;
        }

        switch(messageComponentCreateEvent.getMessageComponentInteraction().getCustomId().trim().toLowerCase()) {
            case "discard" -> {
                if(!PermissionService.getInstance().mayDiscardOthers(messageComponentCreateEvent.getMessageComponentInteraction().getUser(), messageComponentCreateEvent.getMessageComponentInteraction().getServer().get())
                        && (messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().isEmpty()
                        || !messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().get().getValue().equalsIgnoreCase(messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag()))) {
                    messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Nice try!").setFlags(MessageFlag.EPHEMERAL).respond().join();
                    return;
                }

                messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent(
                        "Log discarded!").setFlags(MessageFlag.EPHEMERAL).respond().join();

                if(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().isPresent()) {
                    BotStarter.getInstance().getCarryInformation().remove(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId());
                }

                messageComponentCreateEvent.getMessageComponentInteraction().getMessage().delete().join();
            }
            case "send_log" -> {
                if(messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().isEmpty()
                        || !messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().get().getValue().equalsIgnoreCase(messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag())) {
                    messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Nice try!").setFlags(MessageFlag.EPHEMERAL).respond().join();
                    return;
                }

                ConnectionService.getInstance().addToLogQueue(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId(), BotStarter.getInstance().getCarryInformation().get(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId()));
                BotStarter.getInstance().getCarryInformation().remove(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId());

                messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent(
                        "**Thank you for your service. Your carry will be sent to the staff team for review once the " +
                                "ticket is closed.**\n" +
                                "**You will be notified once it has been reviewed.**").setFlags(MessageFlag.EPHEMERAL).respond().join();
                messageComponentCreateEvent.getMessageComponentInteraction().getMessage().delete();
            }
            case "deny" -> {
                messageComponentCreateEvent.getMessageComponentInteraction().acknowledge();
                long messageId = messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getId();

                for(CarryInformation carryInformation :
                        ConnectionService.getInstance().getFromLogApprovingQueue(messageId)) {

                    User carrier =
                            messageComponentCreateEvent.getApi().getUserById(carryInformation.getCarrier()).join();

                    CarryType carryType = CarryType.fromString(carryInformation.getCarryDifficulty());

                    if(carrier != null && carrier.openPrivateChannel().join() != null) {
                        carrier.openPrivateChannel().join();
                        Optional<PrivateChannel> privateChannelOptional = carrier.getPrivateChannel();

                        //TODO
                        try {
                            privateChannelOptional.ifPresent(privateChannel -> privateChannel
                                    .sendMessage("Your log was denied by " + messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag() + ".",
                                            ApplicationService.getInstance()
                                                    .getEmbed(carryInformation.getTime())
                                                    .setTitle("Information")
                                                    .setColor(new Color(/* TODO green */ 165, 23, 112))
                                                    .addInlineField("Number of carries",
                                                            String.valueOf(carryInformation.getAmountOfCarries()))
                                                    .addInlineField("Type of carry",
                                                            (carryType != null ? carryType.getPrettyName() : carryInformation.getCarryDifficulty()) + " - " + carryInformation.getCarryType())
                                                    .addInlineField("Player",
                                                            messageComponentCreateEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                                    .addInlineField("Carrier", carrier.getMentionTag())
                                                    .addInlineField("Transcript-Link", "[Click to open]" +
                                                            "(https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")")).join());
                        } catch(CompletionException completionException) {
                            completionException.printStackTrace();
                        }
                    }

                    Optional<Server> server = messageComponentCreateEvent.getMessageComponentInteraction().getServer();

                    if(server.isPresent()) {
                        Optional<ServerTextChannel> logChannel =
                                server.get().getTextChannelById(IdList.SCORE_LOGS_CHANNEL.getLocalId(server.get().getId()));

                        if(logChannel.isPresent()) {
                            logger.info("Carry denied:" + carryInformation);

                            logChannel.get().sendMessage(
                                    ApplicationService.getInstance()
                                            .getEmbed(carryInformation.getTime())
                                            .setTitle("Carry-log denied.")
                                            .setColor(new Color(0, 255, 0 /*TODO*/))
                                            .addInlineField("Number of carries",
                                                    String.valueOf(carryInformation.getAmountOfCarries()))
                                            .addInlineField("Type of carry",
                                                    (carryType != null ? carryType.getPrettyName() : carryInformation.getCarryDifficulty()) + " - " + carryInformation.getCarryType())
                                            .addInlineField("Player",
                                                    messageComponentCreateEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                            .addInlineField("Carrier",
                                                    messageComponentCreateEvent.getApi().getUserById(carryInformation.getCarrier()).join().getMentionTag())
                                            .addInlineField("Denied by",
                                                    messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag())
                                            .addInlineField("Transcript-Link", "[Click to open](https://tickettool" +
                                                    ".xyz/direct?url=" + carryInformation.getAttachmentLink() + ")"));
                        }
                    }
                }

                ConnectionService.getInstance().removeFromApprovingQueue(messageId);

                messageComponentCreateEvent.getMessageComponentInteraction().getMessage().delete();
            }
            case "accept_log" -> {
                messageComponentCreateEvent.getMessageComponentInteraction().acknowledge();
                long messageId = messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getId();

                for(CarryInformation carryInformation :
                        ConnectionService.getInstance().getFromLogApprovingQueue(messageId)) {
                    carryInformation.setApprover(messageComponentCreateEvent.getMessageComponentInteraction().getUser().getId());

                    long updatedScore = ConnectionService.getInstance().logCarry(carryInformation);

                    User carrier =
                            messageComponentCreateEvent.getApi().getUserById(carryInformation.getCarrier()).join();

                    CarryType carryType = CarryType.fromString(carryInformation.getCarryDifficulty());

                    if(carrier != null && carrier.openPrivateChannel().join() != null) {
                        carrier.openPrivateChannel().join();
                        Optional<PrivateChannel> privateChannelOptional = carrier.getPrivateChannel();
                        long gainedScore = carryInformation.calculateScore();

                        //TODO
                        try {
                            privateChannelOptional.ifPresent(privateChannel -> privateChannel
                                    .sendMessage("Your carry was logged!\n\n" +

                                                    "**Score gained:** " + gainedScore +
                                                    "\n**Your Updated Score:** " + updatedScore,
                                            ApplicationService.getInstance()
                                                    .getEmbed(carryInformation.getTime())
                                                    .setTitle("Information")
                                                    .setColor(new Color(/* TODO green */ 165, 23, 112))
                                                    .addInlineField("Number of carries",
                                                            String.valueOf(carryInformation.getAmountOfCarries()))
                                                    .addInlineField("Type of carry",
                                                            (carryType != null ? carryType.getPrettyName() : carryInformation.getCarryDifficulty()) + " - " + carryInformation.getCarryType())
                                                    .addInlineField("Player",
                                                            messageComponentCreateEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                                    .addInlineField("Carrier", carrier.getMentionTag())
                                                    .addInlineField("Accepted by",
                                                            messageComponentCreateEvent.getApi().getUserById(carryInformation.getApprover()).join().getMentionTag())
                                                    .addInlineField("Transcript-Link", "[Click to open]" +
                                                            "(https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")")).join());
                        } catch(CompletionException completionException) {
                            completionException.printStackTrace();
                        }
                    }

                    Optional<Server> server = messageComponentCreateEvent.getMessageComponentInteraction().getServer();

                    if(server.isPresent()) {
                        Optional<ServerTextChannel> logChannel;

                        if(carryInformation.isDungeonCarry()) {
                            logChannel =
                                    server.get().getTextChannelById(IdList.DUNGEON_LOGS_CHANNEL.getLocalId(server.get().getId()));
                        } else {
                            logChannel =
                                    server.get().getTextChannelById(IdList.SLAYER_LOGS_CHANNEL.getLocalId(server.get().getId()));
                        }

                        if(logChannel.isPresent()) {
                            logger.info("Carry logged:" + carryInformation);

                            logChannel.get().sendMessage(
                                    ApplicationService.getInstance()
                                            .getEmbed(carryInformation.getTime())
                                            .setTitle("Carry accepted.")
                                            .setColor(new Color(0, 255, 0 /*TODO*/))
                                            .addInlineField("Number of carries",
                                                    String.valueOf(carryInformation.getAmountOfCarries()))
                                            .addInlineField("Type of carry",
                                                    (carryType != null ? carryType.getPrettyName() : carryInformation.getCarryDifficulty()) +
                                                    " - " + carryInformation.getCarryType())
                                            .addInlineField("Player",
                                                    messageComponentCreateEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                            .addInlineField("Carrier",
                                                    messageComponentCreateEvent.getApi().getUserById(carryInformation.getCarrier()).join().getMentionTag())
                                            .addInlineField("Accepted by",
                                                    messageComponentCreateEvent.getApi().getUserById(carryInformation.getApprover()).join().getMentionTag())
                                            .addInlineField("Transcript-Link", "[Click to open](https://tickettool" +
                                                    ".xyz/direct?url=" + carryInformation.getAttachmentLink() + ")"));
                        }
                    }
                }

                LeaderboardService.getInstance().refreshLeaderboard();

                ConnectionService.getInstance().removeFromApprovingQueue(messageId);

                messageComponentCreateEvent.getMessageComponentInteraction().getMessage().delete();
            }
        }
    }
}