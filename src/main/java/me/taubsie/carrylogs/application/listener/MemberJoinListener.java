package me.taubsie.carrylogs.application.listener;

import me.taubsie.carrylogs.application.enums.IdList;
import me.taubsie.carrylogs.application.service.ProfileModerationService;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.event.server.member.ServerMemberJoinEvent;
import org.javacord.api.listener.server.member.ServerMemberJoinListener;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class MemberJoinListener implements ServerMemberJoinListener
{
    @Override
    public void onServerMemberJoin(ServerMemberJoinEvent serverMemberJoinEvent)
    {
        if (serverMemberJoinEvent.getUser().isBot()
                || ProfileModerationService.getInstance().isOverwritten(serverMemberJoinEvent.getUser().getId()))
        {
            return;
        }

        String result = ProfileModerationService.getInstance().checkUserName(serverMemberJoinEvent.getUser().getName());

        if (result != null)
        {
            try
            {
                serverMemberJoinEvent.getUser().openPrivateChannel().join().sendMessage("You got banned from Dungeon Hub because of a suspicious user profile.\nIf you think this might be a mistake, please use our unban-form: https://forms.gle/rfQAJueSoyQno1gD6");
            }
            catch (Exception exception)
            {
                exception.printStackTrace();
            }

            serverMemberJoinEvent.getServer().banUser(serverMemberJoinEvent.getUser(), Duration.of(6, ChronoUnit.DAYS), "Bad username: " + result);

            Optional<ServerTextChannel> logsChannel = serverMemberJoinEvent.getServer().getTextChannelById(IdList.MODERATION_LOGS_CHANNEL.getId(serverMemberJoinEvent.getServer().getId()));
            logsChannel.ifPresent(serverTextChannel -> serverTextChannel.sendMessage("User " + serverMemberJoinEvent.getUser().getMentionTag() + " got banned because of a bad username:\n" + result));
        }
    }
}