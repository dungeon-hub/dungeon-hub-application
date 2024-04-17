package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.NoNameSchemaException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Represents a command to link a Discord user to a Hypixel account.
 *
 * <p>The {@code LinkCommand} class extends the base {@link Command} class and is annotated with {@link CommandParameters}
 * to define its name, description, and whether it is enabled in direct messages (DMs). This command is responsible for
 * handling the execution of commands related to linking Discord users to their Hypixel accounts.</p>
 *
 * @see Command
 * @see CommandParameters
 * @see ApplicationService
 */
@CommandParameters(name = "link", description = "Link your discord to your hypixel account.", enabledInDms = true)
public class LinkCommand extends Command {
    /**
     * Returns a supplier for a delayed response containing an EmbedBuilder indicating successful linking.
     *
     * <p>The {@code linkedEmbed} method takes a {@link UUID} representing the linked ID and returns a
     * {@link Supplier} for a {@link DelayedResponse} that encapsulates an {@link EmbedBuilder}. The EmbedBuilder is
     * configured with a title indicating successful linking, a description containing the formatted linked ID, and a
     * positive color. The supplier allows for delayed response creation.</p>
     *
     * @param linkedId the {@link UUID} representing the linked ID
     * @return a {@link Supplier} for a delayed response containing an {@link EmbedBuilder}
     * @see DelayedResponse
     * @see EmbedBuilder
     * @see EmbedColor
     * @see ApplicationService
     */
    @Contract(pure = true, value = "_ -> new")
    private static @NotNull Supplier<DelayedResponse> linkedEmbedSupplier(@NotNull UUID linkedId) {
        return () -> {
            EmbedBuilder embed = ApplicationService.getInstance().getEmbed();
            embed.setTitle("Linked successfully");
            embed.setDescription("Your UUID is now `%s`".formatted(linkedId));
            embed.setColor(EmbedColor.POSITIVE.getColor());
            return DelayedResponse.fromEmbed(embed);
        };
    }

    @Override
    protected void executeCommand(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        //TODO error / response if linked already

        String inGameName = getStringOption("ign");
        CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();
        respondLater(completableFuture);

        try {
            UUID linkedId = NicknameService.getInstance().linkToIgn(inGameName, getUser());
            completableFuture.completeAsync(linkedEmbedSupplier(linkedId));
            RolesService.getInstance().updateRoles(getUser());

            try {
                NicknameService.getInstance().updateNickname(getUser());
            }
            catch (CompletionException | NoNameSchemaException ignored) {
                //ignored since probably missing permission
            }
        }
        catch (CommandExecutionException commandExecutionException) {
            completableFuture.complete(DelayedResponse.fromException(commandExecutionException));
        }
    }

    @Override
    public @NotNull @Unmodifiable List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        return List.of(ignOption);
    }
}