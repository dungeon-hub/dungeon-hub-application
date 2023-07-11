package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.DungeonHubConnection;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "link", description = "Link your discord to your hypixel account.", enabledInDms = true)
public class LinkCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        String ign = getStringOption("ign");

        UUID uuid = DungeonHubConnection.getInstance().getUUIDByName(ign);

        Optional<String> hypixelName = HypixelConnection.getInstance().getHypixelLinkedDiscord(uuid);

        if(hypixelName.isEmpty()) {
            //TODO custom exception
            throw new CommandExecutionException() {
                @Override
                public String getMessage() {
                    return "This account isn't linked to any discord user in hypixel!";
                }
            };
        }

        User user = getUser();
        String username = user.getDiscriminator().equals("0") ? user.getName() : user.getDiscriminatedName();

        if(!hypixelName.get().equalsIgnoreCase(username)) {
            throw new InvalidOptionException("ign",
                    "Please add the correct discord-account to your hypixel social menu.");
        }

        //TODO database access
        respondLater(new CompletableFuture<EmbedBuilder>().completeAsync(() -> ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle("Linked successfully")
                        .setDescription("||Is what I would say if I had database access||")
                        .setColor(EmbedColor.POSITIVE.getColor())));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption ignOption = ApplicationService.getInstance().getIngamenameOption();

        return List.of(ignOption);
    }
}