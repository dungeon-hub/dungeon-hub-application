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

import me.taubsie.carrylogs.enums.IdList;
import me.taubsie.carrylogs.start.StartBot;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.component.ActionRow;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.awt.*;
import java.util.Map;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class MessageListener implements MessageCreateListener
{
    private final StartBot startBot;

    public MessageListener(StartBot startBot)
    {
        this.startBot = startBot;
    }

    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent)
    {
        if (!messageCreateEvent.isServerMessage()
                || messageCreateEvent.getServer().isEmpty()
                || !(messageCreateEvent.getServer().get().getId() == IdList.TEST_SERVER.getID()
                || messageCreateEvent.getServer().get().getId() == IdList.SERVER.getID()))
        {
            return;
        }

        if ((messageCreateEvent.getChannel().getId() == IdList.TRANSCRIPTS_CHANNEL.getID()
                || messageCreateEvent.getChannel().getId() == IdList.TEST_TRANSCRIPTS_CHANNEL.getID())
                && messageCreateEvent.getMessageContent().startsWith("carrylog;"))
        {
            String[] splitContent = messageCreateEvent.getMessageContent().split(";");
            if (splitContent.length != 3)
            {
                return;
            }

            long channelId = Long.parseLong(splitContent[1]);
            String attachmentLink = splitContent[2];

            long approvingChannelId = startBot.getApprovingChannelId();

            for (Map.Entry<Long, CarryInformation> entry : StartBot.getLogQueue().entrySet())
            {
                if (entry.getKey() == channelId)
                {
                    Message message = messageCreateEvent.getApi()
                            .getTextChannelById(approvingChannelId)
                            .get()
                            .sendMessage(new EmbedBuilder()
                                            .setTimestamp(entry.getValue().getTime())
                                            .setFooter("discord.gg/dungeons")
                                            .setTitle("Accept carry-log?")
                                            .setColor(new Color(/* TODO green */ 165, 23, 112))
                                            .addInlineField("Number of carries", String.valueOf(entry.getValue().getAmountOfCarries()))
                                            .addInlineField("Type of carry", entry.getValue().getCarryType())
                                            .addInlineField("Player", entry.getValue().getPlayer())
                                            .addInlineField("Carrier", entry.getValue().getCarrier().getMentionTag())
                                            .addInlineField("Transcript-Link", attachmentLink),
                                    ActionRow.of(org.javacord.api.entity.message.component.Button.success("accept_log", "Accept"), Button.danger("deny", "Deny")))
                            .join();

                    CarryInformation carryInformation = entry.getValue();

                    carryInformation.setAttachmentLink(attachmentLink);

                    StartBot.getLogApprovingQueue().put(message.getId(), carryInformation);
                }
            }

            while (StartBot.getLogQueue().containsKey(channelId))
            {
                StartBot.getLogQueue().remove(channelId);
            }
        }
    }
}