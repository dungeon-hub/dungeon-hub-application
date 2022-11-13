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

import lombok.Getter;
import me.taubsie.carrylogs.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.interaction.SlashCommandBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Taubsie
 * @since 1.0.0
 */
public abstract class StartBot
{
    @Getter
    private static final Map<Long, CarryInformation> carryInformation = new HashMap<>();

    @Getter
    private static final Map<Long, CarryInformation> logQueue = new HashMap<>();

    @Getter
    private static final Map<Long, CarryInformation> logApprovingQueue = new HashMap<>();

    abstract String getBotToken();

    public void startup()
    {
        DiscordApi bot;

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("token.properties"))
        {
            if (inputStream == null)
            {
                System.out.println("input is null");
                return;
            }

            Properties properties = new Properties();

            properties.load(inputStream);

            bot = new DiscordApiBuilder()
                    .setToken(properties.getProperty(getBotToken()))
                    .setAllNonPrivilegedIntents()
                    .addIntents(Intent.MESSAGE_CONTENT)
                    .setWaitForServersOnStartup(true)
                    .login()
                    .join();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return;
        }

        bot.updateActivity(ActivityType.WATCHING, "carriers");
        bot.updateStatus(UserStatus.ONLINE);

        bot.bulkOverwriteGlobalApplicationCommands(getCommands());

        bot.addListener(new SlashCommandListener());
        bot.addListener(new AutoCompleteListener());
        bot.addListener(new MessageListener(this));
        bot.addListener(new MessageComponentListener(this));
    }

    // </commandName:ID>
    public abstract Set<SlashCommandBuilder> getCommands();

    public abstract long getApprovingChannelId();

    public static String prettifyType(String type)
    {
        return type.trim()
                .toLowerCase()
                .replaceAll("t2", "Tier 2")
                .replaceAll("t3", "Tier 3")
                .replaceAll("t4", "Tier 4")
                .replaceAll("f1", "Floor 1")
                .replaceAll("f2", "Floor 2")
                .replaceAll("f3", "Floor 3")
                .replaceAll("f4", "Floor 4")
                .replaceAll("f5", "Floor 5")
                .replaceAll("f6", "Floor 6")
                .replaceAll("f7", "Floor 7")
                .replaceAll("comp", "Completion")
                .replaceAll("s-plus", "S+")
                .replaceAll("s", "S");
    }

    public abstract List<Role> getAllowedRolesForDiscardOthers(Server server);

    public boolean mayDiscardOthers(User user, Server server)
    {
        if (user.getRoles(server).isEmpty())
        {
            return false;
        }

        List<Role> allowedRoles = getAllowedRolesForDiscardOthers(server);

        for (Role role : user.getRoles(server))
        {
            if (allowedRoles.contains(role))
            {
                return true;
            }
        }

        return false;
    }
}