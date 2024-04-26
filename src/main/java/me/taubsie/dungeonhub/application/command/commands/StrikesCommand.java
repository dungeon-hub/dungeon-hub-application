package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.messages.PageableMessage;
import me.taubsie.dungeonhub.application.messages.StrikeMessage;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.StrikeData;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;
import org.javacord.api.interaction.callback.InteractionOriginalResponseUpdater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a command to execute a "strikes" action, allowing users to see their own or another user's strikes.
 *
 * <p>The {@code StrikesCommand} class extends the {@link Command} class and is annotated with {@link CommandParameters}
 * to provide information about the command, such as its name and description. It overrides the
 * {@link Command#executeCommand(SlashCommandCreateEvent)} method to handle the execution of the strikes command.</p>
 *
 * @see Command
 * @see CommandParameters
 */
@CommandParameters(name = "strikes", description = "See your strikes.")
public class StrikesCommand extends Command {
    @Override
    protected void executeCommand(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        User target = getTarget(slashCommandCreateEvent);

        slashCommandCreateEvent.getSlashCommandInteraction().respondLater(true).thenAccept(responseUpdater -> {
            Message message = getMessage(responseUpdater, target);
            new StrikeMessage(0, message.getChannel().getId(), message.getId(), target.getId());
        });
    }

    @Override
    public @NotNull @Unmodifiable List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOptionBuilder userOption = new SlashCommandOptionBuilder();
        userOption.setType(SlashCommandOptionType.USER);
        userOption.setName("user");
        userOption.setDescription("The user to get the strikes of.");
        userOption.setRequired(false);
        return Collections.singletonList(userOption.build());
    }

    /**
     * Retrieves the {@link Message} to be sent as a response to an interaction, containing an embed and components.
     *
     * <p>The {@code getMessage} method constructs and returns a {@link Message} to be sent as a response to an interaction.
     * It utilizes the provided {@link InteractionOriginalResponseUpdater} and {@link User} to generate the response.
     * The method retrieves information from the {@link DungeonHubConnection} singleton instance, such as strike page details,
     * and constructs message components using {@link PageableMessage#getComponents(boolean, boolean)}.
     * The response is composed of an embed obtained from {@link #getEmbed(User, DungeonHubConnection)} and message components.</p>
     *
     * @param responseUpdater the {@link InteractionOriginalResponseUpdater} for updating the interaction response
     * @param user            the {@link User} for whom the response is generated
     * @return the constructed {@link Message} response
     * @throws NullPointerException if either {@code responseUpdater} or {@code user} is `null`
     * @see PageableMessage#getComponents(boolean, boolean)
     * @see #getEmbed(User, DungeonHubConnection)
     */
    private @NotNull Message getMessage(@NotNull InteractionOriginalResponseUpdater responseUpdater, @NotNull User user) {
        DungeonHubConnection connection = DungeonHubConnection.getInstance();

        boolean isMax = Objects.equals(connection.getMaxValidStrikePage(getServer().getId(), user.getId()), 1);
        HighLevelComponent[] message = PageableMessage.getComponents(true, isMax);

        return responseUpdater.addEmbed(getEmbed(user, connection)).addComponents(message).update().join();
    }

    /**
     * Retrieves an {@link EmbedBuilder} containing formatted strike data for a given user.
     *
     * <p>The {@code getEmbed} method constructs and returns an {@link EmbedBuilder} containing formatted strike data
     * for the specified {@link User}. It uses the provided {@link DungeonHubConnection} to load valid strike data and
     * formats the strikes using {@link ApplicationService#formatStrikes(List, User, int)}.</p>
     *
     * @param user       the {@link User} for whom the strike data is formatted
     * @param connection the {@link DungeonHubConnection} instance for loading strike data
     * @return the constructed {@link EmbedBuilder} containing formatted strike data
     * @throws NullPointerException if either {@code user} or {@code connection} is `null`
     * @see ApplicationService#formatStrikes(List, User, int)
     */
    private @NotNull EmbedBuilder getEmbed(@NotNull User user, @NotNull DungeonHubConnection connection) {
        List<StrikeData> strikeData = connection.loadValidStrikeData(getServer().getId(), user.getId());
        return ApplicationService.getInstance().formatStrikes(strikeData, user, 1);
    }

    /**
     * Retrieves the user for whom strike data is to be checked, considering moderation permissions.
     *
     * <p>The {@code getTarget} method checks if the invoking user has the {@link PermissionType#MODERATE_MEMBERS}
     * permission. If the user lacks this permission, it throws an {@link InvalidOptionException} with an appropriate message.
     * It then attempts to retrieve the specified user using {@link #getUserOption(String)} with the option key "user."
     * If an {@link InvalidOptionException} is caught during this process, it falls back to using the invoking user.</p>
     *
     * <p>The method is designed to be used within the context of a {@link SlashCommandCreateEvent}, and it retrieves the
     * target user from the interaction's arguments. If the "user" argument is present, it is used; otherwise, it falls back
     * to the invoking user.</p>
     *
     * @param slashCommandCreateEvent the {@link SlashCommandCreateEvent} representing the interaction event
     * @return the {@link User} for whom strike data is to be checked
     * @throws InvalidOptionException if the invoking user lacks moderation permissions or if an invalid option is encountered
     * @see #getUserOption(String)
     */
    private @NotNull User getTarget(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteraction interaction = slashCommandCreateEvent.getSlashCommandInteraction();
        Optional<SlashCommandInteractionOption> argument = interaction.getArgumentByName("user");
        return argument.map((o) -> getTarget()).orElseGet(this::getUser);
    }

    /**
     * Retrieves the user for whom strike data is to be checked, considering moderation permissions.
     *
     * <p>The {@code getUserToCheck} method checks if the invoking user has the {@link PermissionType#MODERATE_MEMBERS}
     * permission. If the user lacks this permission, it throws an {@link InvalidOptionException} with an appropriate message.
     * It then attempts to retrieve the specified user using {@link #getUserOption(String)} with the option key "user."
     * If an {@link InvalidOptionException} is caught during this process, it falls back to using the invoking user.</p>
     *
     * @return the {@link User} for whom strike data is to be checked
     * @throws InvalidOptionException if the invoking user lacks moderation permissions
     * @see #getUserOption(String)
     */
    private @NotNull User getTarget() {

        User target = getUserOption("user");

        if (!Objects.equals(getUser(), target) && !getServer().hasPermission(getUser(), PermissionType.MODERATE_MEMBERS)) {
            String exceptionMessage = "You don't have the permission to see the strikes of other people.";
            throw new InvalidOptionException("user", exceptionMessage);
        }

        return target;
    }
}