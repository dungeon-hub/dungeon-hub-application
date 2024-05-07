package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.loader.ClassLoaderService;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandOption;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            embed.setDescription("You're now linked to `%s`.".formatted(MojangConnection.getInstance().getNameByUUID(linkedId)));
            embed.setColor(EmbedColor.POSITIVE.getColor());
            return DelayedResponse.fromEmbed(embed);
        };
    }

    @Override
    protected void executeCommand(@NotNull SlashCommandCreateEvent slashCommandCreateEvent) {
        User user = getUser();

        Optional<UUID> linkedTo = DiscordUserConnection.getInstance().getById(user.getId()).map(DiscordUserModel::getMinecraftId);

        if (linkedTo.isPresent()) {
            respond(
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
            );
            return;
        }

        String inGameName = getStringOption("ign");
        CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();
        respondLater(completableFuture);

        try {
            UUID linkedId = NicknameService.getInstance().linkToIgn(inGameName, user);
            completableFuture.completeAsync(linkedEmbedSupplier(linkedId));
            Map<Long, List<Role>> roles = RolesService.getInstance().updateRoles(user);

            try {
                NicknameService.getInstance().updateNickname(user, roles);
            }
            catch (CompletionException ignored) {
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