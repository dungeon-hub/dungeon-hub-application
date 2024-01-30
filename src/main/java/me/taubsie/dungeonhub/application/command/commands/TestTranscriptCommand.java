package me.taubsie.dungeonhub.application.command.commands;

import lombok.extern.slf4j.Slf4j;
import me.taubsie.dungeonhub.application.classes.DelayedResponse;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.transcripts.DiscordHtmlTranscripts;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "test-transcript", description = "Tests the transcript feature.",
        enabledForUsers = {356134481452597250L})
@Slf4j
public class TestTranscriptCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        respondLaterEphemeral(new CompletableFuture<DelayedResponse>().completeAsync(() -> {
            Optional<ServerTextChannel> serverTextChannel = getChannel().asServerTextChannel();

            if (serverTextChannel.isEmpty()) {
                return DelayedResponse.fromEmbed(
                        ApplicationService.getInstance()
                                .getErrorEmbed()
                                .setDescription("Please use this in a server text channel.")
                );
            }

            try {
                String transcript =
                        DiscordHtmlTranscripts.getInstance().createTranscript(serverTextChannel.get());

                Optional<String> url =
                        ContentConnection.getInstance().uploadFile(transcript.getBytes(StandardCharsets.UTF_8));

                if (url.isPresent()) {
                    return DelayedResponse.fromEmbed(
                            ApplicationService.getInstance()
                                    .getEmbed()
                                    .setDescription("Embed > " + ConfigProperty.CDN_URL + url.get())
                    );
                }
            }
            catch (IOException ioException) {
                log.error(null, ioException);
            }

            return DelayedResponse.fromEmbed(
                    ApplicationService.getInstance()
                            .getErrorEmbed()
                            .setDescription("Couldn't generate transcript.")
            );
        }));
    }
}