package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.service.ApplicationService;
import me.taubsie.carrylogs.application.service.ConnectionService;
import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.CarryInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.CertainMessageEvent;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.MessageEditListener;

import java.awt.*;
import java.util.Optional;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageListener implements MessageCreateListener, MessageEditListener
{
    private static final Logger logger = LogManager.getLogger(MessageListener.class);

    private static final long APPROVE_AMOUNT_THRESHOLD = 5;
    private static final long APPROVE_SCORE_THRESHOLD = 20;

    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent)
    {
        logTicket(messageCreateEvent);
    }

    @Override
    public void onMessageEdit(MessageEditEvent messageEditEvent)
    {
        logTicket(messageEditEvent);
    }

    private void logTicket(CertainMessageEvent messageEvent)
    {
        if (!messageEvent.isServerMessage()
                || messageEvent.getServer().isEmpty()
                || !(messageEvent.getServer().get().getId() == IdList.SERVER.getId()
                || messageEvent.getServer().get().getId() == IdList.SERVER.getTestId()))
        {
            return;
        }

        if ((messageEvent.getChannel().getId() == IdList.TRANSCRIPTS_CHANNEL.getLocalId(messageEvent.getServer().get().getId())
                || messageEvent.getChannel().getId() == IdList.TRANSCRIPTS_CHANNEL.getLocalId(messageEvent.getServer().get().getId()))
                && messageEvent.getMessageContent().startsWith("carrylog;"))
        {

            String[] splitContent = messageEvent.getMessageContent().split(";");
            if (splitContent.length != 3)
            {
                return;
            }

            long channelId = Long.parseLong(splitContent[1]);
            final String attachmentLink = splitContent[2];

            if (attachmentLink.equals("{transcript_url}"))
            {
                return;
            }

            long approvingChannelId = IdList.APPROVING_CHANNEL.getLocalId(messageEvent.getServer().get().getId());

            //TODO fix

            for (CarryInformation carryInformation : ConnectionService.getInstance().getFromLogQueue(channelId))
            {
                carryInformation.setAttachmentLink(attachmentLink);

                if (carryInformation.getAmountOfCarries() >= APPROVE_AMOUNT_THRESHOLD
                        || carryInformation.calculateScore() >= APPROVE_SCORE_THRESHOLD)
                {
                    Message message = messageEvent.getApi()
                            .getTextChannelById(approvingChannelId)
                            .get()
                            .sendMessage(ApplicationService.getInstance()
                                            .getEmbed(carryInformation.getTime())
                                            .setTitle("Accept carry-log?")
                                            .setColor(new Color(/* TODO green */ 165, 23, 112))
                                            .addInlineField("Number of carries", String.valueOf(carryInformation.getAmountOfCarries()))
                                            .addInlineField("Type of carry", carryInformation.getCarryDifficulty() + " - " + carryInformation.getCarryType())
                                            .addInlineField("Player", messageEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                            .addInlineField("Carrier", messageEvent.getApi().getUserById(carryInformation.getCarrier()).join().getMentionTag())
                                            .addInlineField("Transcript-Link", "[Click to open](https://tickettool.xyz/direct?url=" + attachmentLink + ")"),
                                    ActionRow.of(org.javacord.api.entity.message.component.Button.success("accept_log", "Accept"), Button.danger("deny", "Deny")))
                            .join();

                    ConnectionService.getInstance().addToApprovingQueue(message.getId(), carryInformation);
                }
                else
                {
                    long updatedScore = ConnectionService.getInstance().logCarry(carryInformation);

                    User carrier = messageEvent.getApi().getUserById(carryInformation.getCarrier()).join();

                    if (carrier != null && carrier.openPrivateChannel().join() != null)
                    {
                        carrier.openPrivateChannel().join();
                        Optional<PrivateChannel> privateChannelOptional = carrier.getPrivateChannel();
                        long gainedScore = carryInformation.calculateScore();

                        //TODO
                        privateChannelOptional.ifPresent(privateChannel -> privateChannel
                                .sendMessage("Your carry was logged!\n\n" +
                                                "**Score gained:** " + gainedScore +
                                                "\n**Your Updated Score:** " + updatedScore,
                                        ApplicationService.getInstance()
                                                .getEmbed(carryInformation.getTime())
                                                .setTitle("Information")
                                                .setColor(new Color(/* TODO green */ 165, 23, 112))
                                                .addInlineField("Number of carries", String.valueOf(carryInformation.getAmountOfCarries()))
                                                .addInlineField("Type of carry", carryInformation.getCarryDifficulty() + " - " + carryInformation.getCarryType())
                                                .addInlineField("Player", messageEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                                .addInlineField("Carrier", carrier.getMentionTag())
                                                .addInlineField("Transcript-Link", "[Click to open](https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")")).join());
                    }

                    Optional<Server> server = messageEvent.getServer();

                    if (server.isPresent())
                    {
                        Optional<ServerTextChannel> logChannel;

                        if (carryInformation.isDungeonCarry())
                        {
                            logChannel = server.get().getTextChannelById(IdList.DUNGEON_LOGS_CHANNEL.getLocalId(server.get().getId()));
                        }
                        else
                        {
                            logChannel = server.get().getTextChannelById(IdList.SLAYER_LOGS_CHANNEL.getLocalId(server.get().getId()));
                        }

                        if (logChannel.isPresent())
                        {
                            logger.info("Carry logged:" + carryInformation);

                            logChannel.get().sendMessage(
                                    ApplicationService.getInstance()
                                            .getEmbed(carryInformation.getTime())
                                            .setTitle("Carry accepted.")
                                            .setColor(new Color(0, 255, 0 /*TODO*/))
                                            .addInlineField("Number of carries", String.valueOf(carryInformation.getAmountOfCarries()))
                                            .addInlineField("Type of carry", carryInformation.getCarryDifficulty() + " - " + carryInformation.getCarryType())
                                            .addInlineField("Player", messageEvent.getApi().getUserById(carryInformation.getPlayer()).join().getMentionTag())
                                            .addInlineField("Carrier", messageEvent.getApi().getUserById(carryInformation.getCarrier()).join().getMentionTag())
                                            .addInlineField("Transcript-Link", "[Click to open](https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")"));
                        }
                    }
                }
            }

            ConnectionService.getInstance().removeFromLogQueue(channelId);
        }
    }
}