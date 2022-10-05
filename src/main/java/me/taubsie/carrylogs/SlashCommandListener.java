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

import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class SlashCommandListener implements SlashCommandCreateListener
{
    @Override public void onSlashCommandCreate(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        switch (slashCommandCreateEvent.getSlashCommandInteraction().getCommandName().toLowerCase())
        {
            case "log" -> log(slashCommandCreateEvent);
            case "modaltest" ->
            {
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
                    slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).setContent("This still doesn't work!").respond().join();
                }
            }
            case "help" -> slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).setContent("Commands: /log").respond().join();
            default -> slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).setContent("Unknown command.").respond().join();
        }

    }

    private void log(SlashCommandCreateEvent slashCommandCreateEvent)
    {
        slashCommandCreateEvent.getSlashCommandInteraction().createImmediateResponder().setFlags(MessageFlag.EPHEMERAL).setContent("Nice one!").respond().join();
    }
}