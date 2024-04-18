package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.NotLinkedException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.RolesService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserUpdateModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a command to unlink a Discord user from their associated Minecraft account.
 *
 * <p>The {@code UnlinkCommand} class extends the base {@link Command} class and is responsible for handling the
 * execution of commands related to unlinking Discord users from their Minecraft accounts. This command typically
 * involves updating user data, retrieving old user models, and providing feedback through an EmbedBuilder.</p>
 *
 * @see Command
 * @see DiscordUserModel
 * @see DiscordUserConnection
 * @see DiscordUserUpdateModel
 * @see EmbedBuilder
 * @see EmbedColor
 * @see ApplicationService
 */
@CommandParameters(name = "unlink", description = "Unlink from your ingame-account.", enabledInDms = true)
public class UnlinkCommand extends Command {
    /**
     * Updates the Discord user data and returns the previous user model.
     *
     * <p>The {@code updateUser} method takes a {@link User} object, retrieves the previous user model,
     * creates an update model with a specified boolean parameter, and updates the user data through the
     * {@link DiscordUserConnection} instance. If the update fails, a {@link CommandExecutionException} is thrown
     * with an appropriate error message.</p>
     *
     * @param user the Discord {@link User} object to update
     * @return the previous {@link DiscordUserModel} before the update
     * @throws CommandExecutionException if the user data update fails
     * @see DiscordUserUpdateModel
     * @see DiscordUserConnection
     * @see DiscordUserModel
     */
    @Contract(pure = true, value = "_ -> !null")
    private static @NotNull DiscordUserModel updateUser(@NotNull User user) {
        DiscordUserModel oldModel = getOldModel(user);
        DiscordUserUpdateModel updateModel = new DiscordUserUpdateModel(true);
        DiscordUserConnection.getInstance().updateUser(user.getId(), updateModel).orElseThrow(() -> {
            String message = "Couldn't update your user data.";
            return new CommandExecutionException(message);
        });
        return oldModel;
    }

    /**
     * Retrieves the old Discord user model based on the provided user.
     *
     * <p>The {@code getOldModel} method takes a {@link User} object, and using the {@link DiscordUserConnection}
     * instance, it retrieves the previously linked user model. If the user is not linked, a {@link NotLinkedException}
     * is thrown.</p>
     *
     * @param user the Discord {@link User} object for which to retrieve the old model
     * @return the old {@link DiscordUserModel} associated with the user
     * @throws NotLinkedException if the user is not linked
     * @see DiscordUserConnection
     * @see DiscordUserModel
     */
    @Contract(pure = true, value = "_ -> !null")
    private static @NotNull DiscordUserModel getOldModel(@NotNull User user) throws NotLinkedException {
        DiscordUserConnection userConnection = DiscordUserConnection.getInstance();
        return userConnection.getLinkedById(user.getId()).orElseThrow(NotLinkedException::new);
    }

    /**
     * Generates an EmbedBuilder with information about the successful unlinking operation.
     *
     * <p>The {@code getEmbed} method takes the old {@link DiscordUserModel} and uses it to create a success message
     * for unlinking. The message includes the Minecraft UUID of the user. The EmbedBuilder is then colored with a
     * positive color.</p>
     *
     * @param oldUserModel the old {@link DiscordUserModel} before the unlinking operation
     * @return an {@link EmbedBuilder} containing the success message
     * @see DiscordUserModel
     * @see EmbedBuilder
     * @see EmbedColor
     */
    @Contract(pure = true, value = "_ -> new")
    private static @NotNull EmbedBuilder getEmbed(@NotNull DiscordUserModel oldUserModel) {
        ApplicationService service = ApplicationService.getInstance();
        String description = String.format("Unlinked successfully from account `%s`.", MojangConnection.getInstance().getNameByUUID(oldUserModel.getMinecraftId()));
        return service.getEmbed().setDescription(description).setColor(EmbedColor.POSITIVE.getColor());
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();
        respondLater(completableFuture);
        User user = getUser();

        try {
            completableFuture.completeAsync(() -> DelayedResponse.fromEmbed(getEmbed(updateUser(user))));
            RolesService.getInstance().updateRoles(user);
        }
        catch (CommandExecutionException commandExecutionException) {
            completableFuture.complete(DelayedResponse.fromException(commandExecutionException));
        }
    }
}