package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.connection.DungeonHubConnection;
import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.CarryInformation;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.CertainMessageEvent;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageListener implements MessageCreateListener, MessageEditListener {
    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    private static final long APPROVE_AMOUNT_THRESHOLD = 8;
    private static final long APPROVE_SCORE_THRESHOLD = 30;

    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent) {
        logTicket(messageCreateEvent);

        loadSkycryptFromTicket(messageCreateEvent);
    }

    @Override
    public void onMessageEdit(MessageEditEvent messageEditEvent) {
        logTicket(messageEditEvent);
    }

    private void loadSkycryptFromTicket(MessageCreateEvent messageCreateEvent) {
        //TODO make optional
        Server server = messageCreateEvent.getServer().orElse(null);
        Optional<ServerTextChannel> channel = messageCreateEvent.getMessage().getServerTextChannel();

        if (!messageCreateEvent.isServerMessage()
                || channel.isEmpty()
                || server == null
                || !(server.getId() == IdList.SERVER.getId()
                || server.getId() == IdList.SERVER.getTestId())) {
            return;
        }

        try {
            if (channel.get().getMessages(5).join().size() != 1) {
                return;
            }

            Message firstMessage =
                    channel.get().getMessagesAsStream().reduce((message, message2) -> message2).orElse(null);

            if (firstMessage == null) {
                return;
            }

            List<User> mentionedUsers = firstMessage.getMentionedUsers();

            if (mentionedUsers.size() != 1) {
                return;
            }

            User user = mentionedUsers.get(0);

            String[] lines = firstMessage.getContent().split("\n");

            if (lines.length < 2) {
                return;
            }

            String ignOptional = Arrays.stream(lines)
                    .filter(s -> s.startsWith("IGN: "))
                    .findFirst()
                    //TODO replace with orElseGet
                    .orElse(user.getNickname(server)
                            .orElse(null));

            if (ignOptional == null) {
                return;
            }

            String ign = ignOptional
                    .replace("IGN: ", "")
                    .replaceAll("❮(\\S*)❯", "")
                    .replace("★", "")
                    .replace("✦", "")
                    .replace("✶", "")
                    .replace("✽", "")
                    .replace("❊", "")
                    .strip();

            channel.get().sendMessage(ApplicationService.getInstance().getPlayerDataEmbed(ign),
                    new ActionRowBuilder().addComponents(
                            new ButtonBuilder().setStyle(ButtonStyle.LINK)
                                    .setUrl("https://sky.shiiyu.moe/stats/" + ign)
                                    .setLabel("SkyCrypt")
                                    .build()
                    ).build());
        }
        catch (CompletionException ignored) {
            //this just happens when the execution takes so long that the channel gets deleted
            //sending an error then wouldn't be needed
        }
    }

    //TODO reduce complexity
    private void logTicket(CertainMessageEvent messageEvent) {
        Server server = messageEvent.getServer().orElse(null);

        if (!messageEvent.isServerMessage()
                || server == null
                || !(server.getId() == IdList.SERVER.getId()
                || server.getId() == IdList.SERVER.getTestId())) {
            return;
        }

        if (messageEvent.getChannel().getId() == IdList.TRANSCRIPTS_CHANNEL.getLocalId(server.getId())
                && (messageEvent.getMessageContent().startsWith("carrylog;")
                || messageEvent.getMessageContent().startsWith("carrylogs;"))) {

            String[] splitContent = messageEvent.getMessageContent().split(";");
            if (splitContent.length != 3) {
                return;
            }

            long channelId = Long.parseLong(splitContent[1]);
            final String attachmentLink = splitContent[2];

            if (attachmentLink.equals("{transcript_url}")) {
                return;
            }

            long approvingChannelId = IdList.APPROVING_CHANNEL.getLocalId(server.getId());

            for(CarryInformation carryInformation : DungeonHubConnection.getInstance().getFromLogQueue(channelId)) {
                carryInformation.setAttachmentLink(attachmentLink);

                if (carryInformation.getAmountOfCarries() >= APPROVE_AMOUNT_THRESHOLD
                        || carryInformation.calculateScore() >= APPROVE_SCORE_THRESHOLD) {
                    Message message = messageEvent.getApi()
                            .getTextChannelById(approvingChannelId)
                            .get()
                            .sendMessage(ApplicationService.getInstance()
                                            .getEmbed(carryInformation.getTime())
                                            .setTitle("Accept carry-log?")
                                            .setColor(EmbedColor.DEFAULT.getColor())
                                            .addInlineField("Number of carries",
                                                    String.valueOf(carryInformation.getAmountOfCarries()))
                                            .addInlineField("Type of carry",
                                                    carryInformation.getCarryTier().getDisplayName() + " - " + carryInformation.getCarryDifficulty().getDisplayName())
                                            .addInlineField("Player",
                                                    messageEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                            .addInlineField("Carrier",
                                                    messageEvent.getApi().getUserById(carryInformation.getCarrier()).join().getMentionTag())
                                            .addInlineField("Transcript-Link", "[Click to open](https://tickettool" +
                                                    ".xyz/direct?url=" + attachmentLink + ")"),
                                    ActionRow.of(org.javacord.api.entity.message.component.Button.success("accept_log"
                                            , "Accept"), Button.danger("deny", "Deny")))
                            .join();

                    DungeonHubConnection.getInstance().addToApprovingQueue(message.getId(), carryInformation);
                } else {
                    long updatedScore = DungeonHubConnection.getInstance().logCarry(carryInformation);

                    User carrier = messageEvent.getApi().getUserById(carryInformation.getCarrier()).join();

                    if (carrier != null && carrier.openPrivateChannel().join() != null) {
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
                                                    .setColor(EmbedColor.DEFAULT.getColor())
                                                    .addInlineField("Number of carries",
                                                            String.valueOf(carryInformation.getAmountOfCarries()))
                                                    .addInlineField("Type of carry",
                                                            carryInformation.getCarryTier().getDisplayName() + " - " + carryInformation.getCarryDifficulty().getDisplayName())
                                                    .addInlineField("Player",
                                                            messageEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                                    .addInlineField("Carrier", carrier.getMentionTag())
                                                    .addInlineField("Transcript-Link", "[Click to open]" +
                                                            "(https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")")).join());
                        }
                        catch (CompletionException completionException) {
                            completionException.printStackTrace();
                        }
                    }

                    Optional<ServerTextChannel> logChannel = carryInformation.getCarryType()
                            .getLogChannel()
                            .flatMap(server::getTextChannelById);

                    if (logChannel.isPresent()) {
                        logger.debug("Carry logged: {}", carryInformation);

                        logChannel.get().sendMessage(
                                ApplicationService.getInstance()
                                        .getEmbed(carryInformation.getTime())
                                        .setTitle("Carry accepted.")
                                        .setColor(EmbedColor.POSITIVE.getColor())
                                        .addInlineField("Number of carries",
                                                String.valueOf(carryInformation.getAmountOfCarries()))
                                        .addInlineField("Type of carry",
                                                carryInformation.getCarryTier().getDisplayName() + " - " + carryInformation.getCarryDifficulty().getDisplayName())
                                        .addInlineField("Player",
                                                messageEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                        .addInlineField("Carrier",
                                                messageEvent.getApi().getUserById(carryInformation.getCarrier()).join().getMentionTag())
                                        .addInlineField("Transcript-Link", "[Click to open](https://tickettool" +
                                                ".xyz/direct?url=" + carryInformation.getAttachmentLink() + ")"));
                    }
                }
            }

            DungeonHubConnection.getInstance().removeFromLogQueue(channelId);
        }
    }
}
