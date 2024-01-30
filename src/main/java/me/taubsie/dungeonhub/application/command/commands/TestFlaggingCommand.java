package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.classes.FlagResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.FlaggingConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "test-flagging", description = "Tests the flagging feature.", enabledForUsers =
        {356134481452597250L})
public class TestFlaggingCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        User user = getUser();
        DiscordUserModel discordUserModel =
                DiscordUserConnection.getInstance()
                        .getById(user.getId())
                        .orElseGet(() -> new DiscordUserModel(user.getId(), null));

        CompletableFuture<DelayedResponse> completableFuture = new CompletableFuture<>();

        respondLaterEphemeral(completableFuture);

        completableFuture.completeAsync(() -> {
            EmbedBuilder embed = ApplicationService.getInstance().getEmbed();

            List<FlagResponse> flagged =
                    FlaggingConnection.getInstance().isFlagged(discordUserModel.getMinecraftId(), user.getId())
                            .stream()
                            .filter(flagResponse -> flagResponse.uuid() != null || flagResponse.discord() != null)
                            .filter(flagResponse -> (flagResponse.uuid() != null && flagResponse.uuid().flagged())
                                    || (flagResponse.discord() != null && flagResponse.discord().flagged()))
                            .toList();

            if (flagged.isEmpty()) {
                embed.setColor(EmbedColor.POSITIVE.getColor())
                        .setTitle("You aren't flagged as a scammer / ratter!");
            } else {
                embed.setColor(EmbedColor.NEGATIVE.getColor())
                        .setTitle("You are flagged as a scammer / ratter!")
                        .setDescription("Data by:\n" + ApplicationService.getInstance().formatFlagDetails(flagged));
            }

            return DelayedResponse.fromEmbed(embed);
        });
    }
}