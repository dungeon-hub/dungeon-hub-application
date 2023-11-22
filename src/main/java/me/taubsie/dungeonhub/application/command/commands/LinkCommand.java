package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.NicknameService;
import me.taubsie.dungeonhub.application.service.RolesService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "link", description = "Link your discord to your hypixel account.", enabledInDms = true)
public class LinkCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        CompletableFuture<EmbedBuilder> completableFuture = new CompletableFuture<>();
        respondLater(completableFuture);

        UUID linkedId;
        try {
            linkedId = NicknameService.getInstance().linkToIgn(ign, getUser());
        }
        catch (CommandExecutionException commandExecutionException) {
            completableFuture.complete(ApplicationService.getInstance().getErrorEmbed(commandExecutionException));
            return;
        }

        completableFuture.completeAsync(() -> ApplicationService.getInstance()
                .getEmbed()
                .setTitle("Linked successfully")
                .setDescription("Your UUID is now `" + linkedId + "`")
                .setColor(EmbedColor.POSITIVE.getColor()));

        RolesService.getInstance().updateRoles(getUser());
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        return List.of(ignOption);
    }
}