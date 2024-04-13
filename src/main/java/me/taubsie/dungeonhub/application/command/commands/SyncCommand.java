package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
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
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

/**
 * Represents a synchronization command for handling user data synchronization.
 *
 * <p>The {@code SyncCommand} class extends the base {@link Command} class and is designed to handle user data synchronization.
 * It provides functionality related to synchronizing user information across different systems or platforms.</p>
 *
 * @see Command
 */
@CommandParameters(name = "sync", description = "Update your roles and nickname based on your linked account.")
public class SyncCommand extends Command {

    /**
     * Retrieves the Discord user model associated with the specified user for a slash command event.
     *
     * <p>The {@code getUserModel} method retrieves the {@link DiscordUserModel} associated with the specified {@link User} for a
     * given {@link SlashCommandCreateEvent}. It checks if the user is linked to an in-game account. If not linked, it responds with
     * a modal asking the user to link their in-game account first.</p>
     *
     * @param user                    the {@link User} for whom to retrieve the Discord user model
     * @return an {@link Optional} containing the {@link DiscordUserModel} if the user is linked, otherwise an empty {@link Optional}
     * @throws NullPointerException if either the {@link SlashCommandCreateEvent} or the {@link User} is {@code null}
     * @see DiscordUserConnection#getInstance()
     * @see DiscordUserConnection#getById(long)
     * @see DiscordUserModel#getMinecraftId()
     * @see HighLevelComponent
     * @see ApplicationService#getInstance()
     * @see ApplicationService#getLinkModalComponent()
     */
    private static @NotNull Optional<DiscordUserModel> getUserModel(@NotNull User user) {
        DiscordUserConnection connection = DiscordUserConnection.getInstance();
        Predicate<DiscordUserModel> notNull = (model) -> Objects.nonNull(model.getMinecraftId());
        return connection.getById(user.getId()).filter(notNull);
    }

    /**
     * Generates a response {@link Runnable} for linking an in-game account.
     *
     * <p>The {@code linkAccountResponse} method creates a response {@link Runnable} that sends a modal with a specified message
     * and a link component to the user via a slash command interaction. This is typically used when prompting the user to link
     * their in-game account.</p>
     *
     * @param slashCommandCreateEvent the {@link SlashCommandCreateEvent} associated with the slash command
     * @return a new {@link Runnable} representing the response action for linking an in-game account
     * @throws NullPointerException if the {@link SlashCommandCreateEvent} is {@code null}
     * @see HighLevelComponent
     * @see ApplicationService#getInstance()
     * @see ApplicationService#getLinkModalComponent()
     */
    @Contract(pure = true, value = "_ -> new")
    private static @NotNull Runnable linkAccountResponse(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        return () -> {
            String message = "Please link your ingame-account first.";
            HighLevelComponent component = ApplicationService.getInstance().getLinkModalComponent();
            slashCommandCreateEvent.getSlashCommandInteraction().respondWithModal("link_needed", message, component);
        };
    }

    /**
     * Sends an embed to the user indicating the update of nickname and roles.
     *
     * <p>The {@code sendEmbed} method sends an embed to the specified user indicating the update of nickname and roles. It also checks
     * whether the nickname has been changed and includes this information in the embed.</p>
     *
     * @param completableFuture the {@link CompletableFuture} for handling delayed responses
     * @param user              the {@link User} to whom the embed is sent
     * @param discordUserModel  the {@link DiscordUserModel} associated with the user
     * @param server            the {@link Server} to which the user belongs
     * @throws NullPointerException if any of the parameters is {@code null}
     * @see EmbedBuilder
     * @see EmbedColor#POSITIVE
     * @see ApplicationService#getInstance()
     * @see ApplicationService#getEmbed()
     * @see #updateNickName(CompletableFuture, User, DiscordUserModel, Server)
     */
    private static void sendEmbed(@NotNull CompletableFuture<DelayedResponse> completableFuture, @NotNull User user, @NotNull DiscordUserModel discordUserModel, @NotNull Server server) {

        boolean nickNameChanged = updateNickName(completableFuture, user, discordUserModel, server);

        if(completableFuture.isDone())
        {
            return;
        }

        EmbedBuilder embed = ApplicationService.getInstance().getEmbed();
        embed.setDescription(getDescription(nickNameChanged));
        embed.setColor(EmbedColor.POSITIVE.getColor());
        completableFuture.complete(DelayedResponse.fromEmbed(embed));
    }

    /**
     * Updates the nickname for the specified user on the server.
     *
     * <p>The {@code updateNickName} method updates the nickname for the specified {@link User} on the specified {@link Server}.
     * It catches exceptions related to the update process, such as missing name schema, not being linked, or permission issues.</p>
     *
     * @param future    the {@link CompletableFuture} for handling delayed responses
     * @param user      the {@link User} whose nickname is to be updated
     * @param userModel the {@link DiscordUserModel} associated with the user
     * @param server    the {@link Server} to which the user belongs
     * @return {@code true} if the nickname has been changed, {@code false} otherwise
     * @throws NullPointerException if any of the parameters is {@code null}
     * @see NicknameService#getInstance()
     * @see NicknameService#updateNickname(User, DiscordUserModel, Server)
     * @see NoNameSchemaException
     * @see NotLinkedException
     * @see CompletionException
     */
    private static boolean updateNickName(@NotNull CompletableFuture<DelayedResponse> future, @NotNull User user, @NotNull DiscordUserModel userModel, @NotNull Server server) {
        try {
            NicknameService.getInstance().updateNickname(user, userModel, server);
        }
        catch (NoNameSchemaException noNameSchemaException) {
            return false;
        }
        catch (NotLinkedException notLinkedException) {
            future.complete(DelayedResponse.fromException(notLinkedException));
        }
        catch (CompletionException completionException) {
            //ignored since probably missing permission
        }
        return true;
    }

    /**
     * Retrieves the description for updating roles based on the provided condition.
     *
     * <p>The {@code getDescription} method generates a description for updating roles, including "nickname" if the condition is met.
     * It uses a {@link StringJoiner} for better string concatenation and improved readability.</p>
     *
     * @param nicknameChanged a boolean indicating whether the nickname has changed
     * @return the generated description string
     */
    @Contract(pure = true, value = "_ -> new")
    private static @NotNull String getDescription(boolean nicknameChanged) {
        StringJoiner description = new StringJoiner(" ").add("Updating your");
        if (nicknameChanged) {
            description.add("nickname and");
        }
        return description.add("roles.").toString();
    }

    @Override
    protected void executeCommand(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        User user = getUser();

        getUserModel(user).ifPresentOrElse(model -> {
            CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();
            respondLater(completableFuture);

            Server server = getServer();
            RolesService.getInstance().updateRoles(user, server);
            sendEmbed(completableFuture, user, model, server);
        }, linkAccountResponse(slashCommandCreateEvent));
    }
}