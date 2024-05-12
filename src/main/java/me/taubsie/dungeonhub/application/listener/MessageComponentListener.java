package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.classes.HelpDisplay;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.QueueConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.enums.HelpTopic;
import me.taubsie.dungeonhub.application.exceptions.*;
import me.taubsie.dungeonhub.application.loader.ClassLoaderService;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.application.service.PermissionService;
import me.taubsie.dungeonhub.common.enums.QueueStep;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueUpdateModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.score.LoggedCarryModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import me.taubsie.dungeonhub.kord.application.enums.ServerProperty;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.MessageComponentCreateEvent;
import org.javacord.api.interaction.MessageComponentInteraction;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.listener.interaction.MessageComponentCreateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageComponentListener implements MessageComponentCreateListener {
    @Override
    public void onComponentCreate(MessageComponentCreateEvent messageComponentCreateEvent) {
        Optional<Server> server = messageComponentCreateEvent.getMessageComponentInteraction().getServer();

        if (server.isEmpty()) {
            ApplicationService.getInstance().respondWithError(messageComponentCreateEvent.getInteraction(),
                    new MustBeServerException());
            return;
        }

        switch (messageComponentCreateEvent.getMessageComponentInteraction().getCustomId().strip().toLowerCase()) {
            case "discard" -> discard(messageComponentCreateEvent.getMessageComponentInteraction(), server.get());
            case "send_log" -> sendLog(messageComponentCreateEvent.getMessageComponentInteraction());

            case "deny" -> deny(messageComponentCreateEvent.getMessageComponentInteraction(), server.get());
            case "accept_log" -> acceptLog(messageComponentCreateEvent.getMessageComponentInteraction(), server.get());

            case "reload_playerdata", "show_flagged_banned", "show_excluded" -> {
                // This is here so that the default block doesn't get executed and the lambda expressions that are set
                // on creation work
            }

            case "link_user" -> {
                long userId = messageComponentCreateEvent.getMessageComponentInteraction().getUser().getId();

                Optional<UUID> linkedTo = DiscordUserConnection.getInstance().getById(userId).map(DiscordUserModel::getMinecraftId);

                if (linkedTo.isPresent()) {
                    messageComponentCreateEvent.getMessageComponentInteraction()
                            .createImmediateResponder()
                            .addEmbed(
                                    ApplicationService.getInstance()
                                            .getEmbed()
                                            .setColor(EmbedColor.INFORMATION.getColor())
                                            .setDescription("You're already linked to user `"
                                                    + MojangConnection.getInstance().getNameByUUID(linkedTo.get())
                                                    + "`! If you think that's incorrect, try using "
                                                    + ClassLoaderService.getInstance()
                                                    .getSlashCommand("unlink", null)
                                                    .map(SlashCommand::getMentionTag)
                                                    .orElse("`/unlink`")
                                                    + ".")
                            )
                            .setFlags(MessageFlag.EPHEMERAL)
                            .respond();
                    return;
                }

                String message = "Link your ingame-account.";
                HighLevelComponent component = ApplicationService.getInstance().getLinkModalComponent();
                messageComponentCreateEvent.getMessageComponentInteraction().respondWithModal("link_ign", message, component);
            }
        }
    }
}