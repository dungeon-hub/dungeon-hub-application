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
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class SlashCommandListener implements SlashCommandCreateListener
{
    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        switch (slashCommandCreateEvent.getSlashCommandInteraction().getCommandName().toLowerCase())
        {
            case "log" -> log(slashCommandCreateEvent);
            case "modaltest" ->
            {
                if (slashCommandCreateEvent.getSlashCommandInteraction().getServer().isEmpty()
                        || slashCommandCreateEvent.getSlashCommandInteraction().getServer().get().getId() != IdList.TEST_SERVER.getID())
                {
                    showHelp(slashCommandCreateEvent);
                    return;
                }

                try
                {
                    slashCommandCreateEvent.getSlashCommandInteraction().respondWithModal("modalId", "Title of Modal",
                            ActionRow.of(TextInput.create(TextInputStyle.SHORT, "textInputId", "Input  here")),
                            ActionRow.of(SelectMenu.create("menuId",
                                    new ArrayList<>(Arrays.asList(new SelectMenuOptionBuilder().setLabel("hi").setValue("hivalue").build(),
                                            new SelectMenuOptionBuilder().setLabel("hi2").setValue("value2").build()))))).join();
                }
                catch (Exception exception)
                {
                    exception.printStackTrace();
                    slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).setContent("This still doesn't work! OS is: " + System.getProperty("os.name")).respond().join();
                }
            }
            case "help" -> showHelp(slashCommandCreateEvent);
            default ->
                    slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).setContent("Unknown command.").respond().join();
        }
    }

    private void log(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        if (slashCommandCreateEvent.getSlashCommandInteraction().getServer().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().isEmpty()
                || slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().isEmpty()
                || !IdList.isCarryCategory(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().asCategorizable().get().getCategory().get().getId()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Please use this in a carry-ticket.")
                    .respond()
                    .join();
            return;
        }

        if (StartBot.getCarryInformation().containsKey(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getId()))
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Someone is already logging this carry.")
                    .respond()
                    .join();
            return;
        }

        Optional<Long> amountOfCarries = slashCommandCreateEvent.getSlashCommandInteraction().getOptionLongValueByName("amount");

        if (amountOfCarries.isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Incorrect usage: No amount of carries specified.")
                    .respond()
                    .join();
            return;
        }

        Optional<String> carryType = slashCommandCreateEvent.getSlashCommandInteraction().getOptionStringValueByName("type");

        if (carryType.isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Incorrect usage: No type of carry specified.")
                    .respond()
                    .join();
            return;
        }

        Message firstMessage = slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getMessagesAsStream().reduce((message, message2) -> message2).orElse(null);

        if (firstMessage == null
                || firstMessage.getMentionedUsers().isEmpty())
        {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .createImmediateResponder()
                    .setFlags(MessageFlag.EPHEMERAL)
                    .setContent("Couldn't retrieve bot message. Please report this.")
                    .respond()
                    .join();
            return;
        }

        Instant time = Instant.now();
        User carried = firstMessage.getMentionedUsers().get(0);
        User carrier = slashCommandCreateEvent.getSlashCommandInteraction().getUser();

        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .addEmbed(new EmbedBuilder()
                        .setTimestamp(time)
                        .setFooter("discord.gg/dungeons")
                        .setTitle("Are you sure that you want to log this?")
                        .setColor(new Color(/* TODO green */ 165, 23, 112))
                        .addInlineField("Number of carries", String.valueOf(amountOfCarries.get()))
                        .addInlineField("Type of carry", StartBot.prettifyType(carryType.get()))
                        .addInlineField("Player", carried.getMentionTag())
                        .addInlineField("Carrier", carrier.getMentionTag()))
                .addComponents(ActionRow.of(Button.success("send_log", "Confirm"), Button.danger("discard", "Cancel")))
                .respond().join();

        CarryInformation carryInformation = new CarryInformation();

        carryInformation.setTime(time);
        carryInformation.setAmountOfCarries(amountOfCarries.get());
        carryInformation.setCarrier(carrier);
        carryInformation.setPlayer(carried.getMentionTag());
        carryInformation.setCarryType(StartBot.prettifyType(carryType.get()));

        StartBot.getCarryInformation().put(slashCommandCreateEvent.getSlashCommandInteraction().getChannel().get().getId(), carryInformation);
    }

    private void showHelp(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(new EmbedBuilder().setTitle("**Bot Usage:**").setDescription("""
                        This bot uses slash commands, in order to use it you must have your discord client updated (No need to worry if you're on desktop).

                        Type out `/log` **in the ticket** , you will then see a prompt showing you all you have to input.

                         **Usage:** `/log amount:NUMBER type:Completion/S/S+/Tier 2/Tier 3/Tier 4`""")
                        .setFooter("discord.gg/dungeons").setColor(/*TODO*/ new Color(255, 255, 255))).respond().join();
    }
}