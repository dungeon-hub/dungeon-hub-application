package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.DungeonHubService;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "embed", description = "Makes it possible to manage embeds.", enabledForPermissions =
        {PermissionType.MANAGE_MESSAGES})
public class EmbedCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption firstOption = getOptionAtIndex(0);

        switch (firstOption.getName().toLowerCase()) {
            case "get" -> get(slashCommandCreateEvent, firstOption);
            default -> throw new InvalidSubCommandException();
        }
    }

    private void get(SlashCommandCreateEvent slashCommandCreateEvent, SlashCommandInteractionOption firstOption) {
        Server server = getServer();

        String link = getStringOption(firstOption, "link");

        Message message = server.getApi().getMessageByLink(link)
                .orElseThrow(() -> new InvalidOptionException("link"))
                .join();

        if (message.getEmbeds().isEmpty()) {
            throw new CommandExecutionException("The given message doesn't have an embed.");
        }

        Embed embed = message.getEmbeds().get(0);

        EmbedBuilder embedBuilder = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor());

        DungeonHubService.getInstance().getGson().toJsonTree(embed)
                .getAsJsonObject()
                .entrySet()
                .forEach(entry -> embedBuilder.addField(entry.getKey(), entry.getValue().toString()));

        respond(embedBuilder);
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption linkOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("link")
                .setDescription("Please paste the link to the message here.")
                .setRequired(true)
                .build();

        SlashCommandOption getOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Gets the embed content of a message.")
                .setOptions(List.of(linkOption))
                .build();

        return List.of(getOption);
    }
}