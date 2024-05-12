package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.MustBeServerException;
import me.taubsie.dungeonhub.application.loader.ClassLoaderService;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.MessageComponentCreateEvent;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.listener.interaction.MessageComponentCreateListener;

import java.util.Optional;
import java.util.UUID;

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