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
package me.taubsie.carrylogs.start;

import me.taubsie.carrylogs.enums.IdList;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public class StartTestBot extends StartBot
{
    public static void main(String[] args)
    {
        try
        {
            Class.forName("com.mysql.cj.jdbc.Driver");
        }
        catch (ClassNotFoundException e)
        {
            System.out.println("Connection to database couldn't be established successfully.");
        }

        new StartTestBot().startup();
    }

    @Override
    String getBotToken()
    {
        return "bot.test";
    }

    @Override
    public Set<SlashCommandBuilder> getCommands()
    {
        Set<SlashCommandBuilder> commands = new HashSet<>();

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
        commands.add(new SlashCommandBuilder().setName("modaltest").setDescription("test of modal interaction"));

        return commands;
    }

    @Override
    public long getApprovingChannelId()
    {
        return IdList.TEST_APPROVING_CHANNEL.getID();
    }

    //TODO add permissions
    @Override
    public List<Role> getAllowedRolesForDiscardOthers(Server server)
    {
        List<Role> allowedRoles = new ArrayList<>();

        allowedRoles.add(server.getRoleById(1036373005720358972L).orElse(null));

        return allowedRoles;
    }
}