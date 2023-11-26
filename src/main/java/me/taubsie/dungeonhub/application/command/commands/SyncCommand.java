package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.NoNameSchemaException;
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@CommandParameters(name = "sync", description = "Update your roles and nickname based on your linked account.")
public class SyncCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        User user = getUser();

        Optional<DiscordUserModel> discordUserModel = DiscordUserConnection.getInstance()
                .getById(user.getId())
                .filter(discordUserModel1 -> discordUserModel1.getMinecraftId() != null);

        if (discordUserModel.isEmpty()) {
            slashCommandCreateEvent.getSlashCommandInteraction()
                    .respondWithModal("link_needed",
                            "Please link your ingame-account first.",
                            ApplicationService.getInstance().getLinkModalComponent());
            return;
        }

        Server server = getServer();

        CompletableFuture<EmbedBuilder> completableFuture = new CompletableFuture<>();
        respondLater(completableFuture);

        boolean nameChanged = true;

        RolesService.getInstance().updateRoles(user, server);
        try {
            NicknameService.getInstance().updateNickname(user, discordUserModel.get(), server);
        }
        catch (NoNameSchemaException noNameSchemaException) {
            nameChanged = false;
        }
        catch (NotLinkedException notLinkedException) {
            completableFuture.complete(ApplicationService.getInstance()
                    .getErrorEmbed(notLinkedException));
            return;
        }
        catch (CompletionException completionException) {
            //ignored since probably missing permission
        }

        completableFuture.complete(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Updating your " + (nameChanged ? "nickname and " : "") + "roles"));
    }
}