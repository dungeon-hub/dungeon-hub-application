package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.RolesService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "unlink", description = "Unlink from your ingame-account.", enabledInDms = true)
public class UnlinkCommand extends Command {
    @Override
    @SuppressWarnings("java:S2201") // This is just used for updating the user info...
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();
        respondLater(completableFuture);

        User user = getUser();

        try {
            DiscordUserModel oldUserModel = DiscordUserConnection.getInstance()
                    .getLinkedById(user.getId())
                    .orElseThrow(NotLinkedException::new);

            DiscordUserUpdateModel updateModel = new DiscordUserUpdateModel(true);

            DiscordUserConnection.getInstance()
                    .updateUser(user.getId(), updateModel)
                    .orElseThrow(() -> new CommandExecutionException() {
                        @Override
                        public String getMessage() {
                            return "Couldn't update your user data.";
                        }
                    });

            completableFuture.completeAsync(() -> DelayedResponse.fromEmbed(
                    ApplicationService.getInstance()
                            .getEmbed()
                            .setDescription("Unlinked successfully from UUID `" + oldUserModel.getMinecraftId() + "`.")
                            .setColor(EmbedColor.POSITIVE.getColor()))
            );
        }
        catch (CommandExecutionException commandExecutionException) {
            completableFuture.complete(DelayedResponse.fromException(commandExecutionException));
            return;
        }

        RolesService.getInstance().updateRoles(user);
    }
}