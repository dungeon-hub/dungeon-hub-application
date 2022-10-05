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

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.interaction.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class CarryLogs
{
    public static void main(String[] args)
    {
        DiscordApi bot = new DiscordApiBuilder()
                .setToken("MTAyMzY4NDMwNjg2ODEyMTY3MA.G5ELro.wfl5fHcMHEytytuBWBv3MXKrsBjC4YxKuPv5Rk")
                .setAllNonPrivilegedIntents()
                .setWaitForServersOnStartup(true)
                .login()
                .join();

        bot.updateActivity(ActivityType.COMPETING, "faster startup times.");
        bot.updateStatus(UserStatus.IDLE);

        bot.updateActivity(ActivityType.LISTENING, "to your logs | /log");
        bot.updateStatus(UserStatus.ONLINE);

        bot.bulkOverwriteGlobalApplicationCommands(getCommands());

        bot.addListener(new SlashCommandListener());
        bot.addListener(new AutoCompleteListener());
    }

    public static List<SlashCommandBuilder> getCommands()
    {
        List<SlashCommandBuilder> commands = new ArrayList<>();

        SlashCommandOption amountOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.LONG)
                .setName("amount")
                .setDescription("The amount of carries you did.")
                .setRequired(true)
                .build();

        SlashCommandOption typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("The type of the carry.")
                .setRequired(true)
                .setAutocompletable(true)
                .build();

        SlashCommandBuilder logCommandBuilder = new SlashCommandBuilder()
                .setName("log")
                .setDescription("Use this to log your carries.")
                .setOptions(Arrays.asList(amountOption, typeOption));

        commands.add(logCommandBuilder);

        commands.add(new SlashCommandBuilder().setName("help").setDescription("List of available commands."));
        commands.add(new SlashCommandBuilder().setName("unregisteredcommand").setDescription("test of unknown command"));
        commands.add(new SlashCommandBuilder().setName("modaltest").setDescription("test of modal interaction"));

        return commands;
    }
}