/*
 * KissenEssentials
 * Copyright (C) KissenEssentials team and contributors.
 *
 * This program is free software and is free to redistribute
 * and/or modify under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is intended for the purpose of joy,
 * WITHOUT WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.taubsie.carrylogs;

import me.taubsie.carrylogs.start.StartBot;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.MessageComponentCreateEvent;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;
import org.javacord.api.listener.interaction.MessageComponentCreateListener;

import java.awt.*;
import java.util.Optional;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class MessageComponentListener implements MessageComponentCreateListener
{
    private final StartBot startBot;

    public MessageComponentListener(StartBot startBot)
    {
        this.startBot = startBot;
    }

    @Override
    public void onComponentCreate(MessageComponentCreateEvent messageComponentCreateEvent)
    {
        if (messageComponentCreateEvent.getMessageComponentInteraction().getServer().isEmpty())
        {
            messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Use this on a server!").setFlags(MessageFlag.EPHEMERAL).respond().join();
            return;
        }

        switch (messageComponentCreateEvent.getMessageComponentInteraction().getCustomId().trim().toLowerCase())
        {
            case "discard" ->
            {
                if (!startBot.mayDiscardOthers(messageComponentCreateEvent.getMessageComponentInteraction().getUser(), messageComponentCreateEvent.getMessageComponentInteraction().getServer().get())
                        && (messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().isEmpty()
                        || !messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().get().getValue().equalsIgnoreCase(messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag())))
                {
                    messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Nice try!").setFlags(MessageFlag.EPHEMERAL).respond().join();
                    return;
                }

                messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Log discarded!").setFlags(MessageFlag.EPHEMERAL).respond().join();

                if (messageComponentCreateEvent.getMessageComponentInteraction().getChannel().isPresent())
                {
                    StartBot.getCarryInformation().remove(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId());
                }

                messageComponentCreateEvent.getMessageComponentInteraction().getMessage().delete().join();
            }
            case "send_log" ->
            {
                if (messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().isEmpty()
                        || messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().isEmpty()
                        || !messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0).getFields().stream().filter(embedField -> embedField.getName().equalsIgnoreCase("carrier")).findFirst().get().getValue().equalsIgnoreCase(messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag()))
                {
                    messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Nice try!").setFlags(MessageFlag.EPHEMERAL).respond().join();
                    return;
                }

                StartBot.getLogQueue().put(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId(), StartBot.getCarryInformation().get(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId()));
                StartBot.getCarryInformation().remove(messageComponentCreateEvent.getMessageComponentInteraction().getChannel().get().getId());

                messageComponentCreateEvent.getMessageComponentInteraction().createImmediateResponder().setContent("Thanks for your log, once this ticket is closed, you will be rewarded with your score.").setFlags(MessageFlag.EPHEMERAL).respond().join();
                messageComponentCreateEvent.getMessageComponentInteraction().getMessage().delete();
            }
            case "accept_log" ->
            {
                long messageId = messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getId();

                CarryInformation carryInformation = StartBot.getLogApprovingQueue().get(messageId);

                User carrier = carryInformation.getCarrier();

                if (carrier != null && carrier.openPrivateChannel().join() != null)
                {
                    carrier.openPrivateChannel().join();
                    Optional<PrivateChannel> privateChannelOptional = carrier.getPrivateChannel();

                    if (privateChannelOptional.isPresent())
                    {
                        //TODO
                        privateChannelOptional.get()
                                .sendMessage("Your log was accepted!",
                                        new EmbedBuilder()
                                                .setTimestamp(carryInformation.getTime())
                                                .setFooter("discord.gg/dungeons")
                                                .setTitle("Information")
                                                .setColor(new Color(/* TODO green */ 165, 23, 112))
                                                .addInlineField("Number of carries", String.valueOf(carryInformation.getAmountOfCarries()))
                                                .addInlineField("Type of carry", carryInformation.getCarryType())
                                                .addInlineField("Player", carryInformation.getPlayer())
                                                .addInlineField("Carrier", carryInformation.getCarrier().getMentionTag())
                                                .addInlineField("Accepted by", messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag())
                                                .addInlineField("Transcript-Link", carryInformation.getAttachmentLink())).join();
                    }
                }

                StartBot.getLogApprovingQueue().remove(messageId);

                ComponentInteractionOriginalMessageUpdater messageUpdater = messageComponentCreateEvent.getMessageComponentInteraction().createOriginalMessageUpdater();

                Embed embed = messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0);
                if (embed != null)
                {
                    EmbedBuilder embedBuilder = embed.toBuilder().setTitle("Carry-log accepted.").setColor(new Color(0, 255, 0 /*TODO*/)).setTimestampToNow();
                    messageUpdater.removeAllEmbeds().addEmbed(embedBuilder);
                }

                messageUpdater.removeAllComponents();

                messageUpdater.update().join();
            }
            case "deny" ->
            {
                long messageId = messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getId();

                CarryInformation carryInformation = StartBot.getLogApprovingQueue().get(messageId);

                User carrier = carryInformation.getCarrier();

                if (carrier != null && carrier.openPrivateChannel().join() != null)
                {
                    carrier.openPrivateChannel().join();
                    Optional<PrivateChannel> privateChannelOptional = carrier.getPrivateChannel();

                    if (privateChannelOptional.isPresent())
                    {
                        //TODO
                        privateChannelOptional.get()
                                .sendMessage("Your log was denied by " + messageComponentCreateEvent.getMessageComponentInteraction().getUser().getMentionTag() + ".",
                                        new EmbedBuilder()
                                                .setTimestamp(carryInformation.getTime())
                                                .setFooter("discord.gg/dungeons")
                                                .setTitle("Information")
                                                .setColor(new Color(/* TODO green */ 165, 23, 112))
                                                .addInlineField("Number of carries", String.valueOf(carryInformation.getAmountOfCarries()))
                                                .addInlineField("Type of carry", carryInformation.getCarryType())
                                                .addInlineField("Player", carryInformation.getPlayer())
                                                .addInlineField("Carrier", carryInformation.getCarrier().getMentionTag())
                                                .addInlineField("Transcript-Link", carryInformation.getAttachmentLink())).join();
                    }
                }


                StartBot.getLogApprovingQueue().remove(messageId);

                ComponentInteractionOriginalMessageUpdater messageUpdater = messageComponentCreateEvent.getMessageComponentInteraction().createOriginalMessageUpdater();

                Embed embed = messageComponentCreateEvent.getMessageComponentInteraction().getMessage().getEmbeds().get(0);
                if (embed != null)
                {
                    EmbedBuilder embedBuilder = embed.toBuilder().setTitle("Carry-log denied.").setColor(new Color(255, 0, 0 /*TODO*/)).setTimestampToNow();
                    messageUpdater.removeAllEmbeds().addEmbed(embedBuilder);
                }

                messageUpdater.removeAllComponents();

                messageUpdater.update().join();
            }
        }
    }
}