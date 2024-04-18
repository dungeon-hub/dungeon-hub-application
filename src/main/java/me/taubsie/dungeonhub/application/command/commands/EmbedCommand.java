package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidOptionException;
import me.taubsie.dungeonhub.application.exceptions.InvalidSubCommandException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.common.DungeonHubService;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.*;

import java.util.List;
import java.util.stream.Stream;

@CommandParameters(name = "embed", description = "Makes it possible to manage embeds.", enabledForPermissions =
        {PermissionType.MANAGE_MESSAGES})
public class EmbedCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption firstOption = getOptionAtIndex(0);

        switch (firstOption.getName().toLowerCase()) {
            case "get" -> get(slashCommandCreateEvent, firstOption);
            case "send" -> send(slashCommandCreateEvent, firstOption);
            default -> throw new InvalidSubCommandException();
        }
    }

    private void send(SlashCommandCreateEvent slashCommandCreateEvent, SlashCommandInteractionOption firstOption) {
        ServerChannel channel = getChannelOption(firstOption, "channel");

        String source = getStringOption(firstOption, "embed");

        EmbedBuilder embedBuilder = DungeonHubService.getInstance().getGson().fromJson(source, EmbedBuilder.class);

        channel.asTextChannel().orElseThrow(() -> new CommandExecutionException("Unknown channel type"))
                .sendMessage(embedBuilder).join();

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Embed sent!"));
    }

    private void get(SlashCommandCreateEvent slashCommandCreateEvent, SlashCommandInteractionOption firstOption) {
        Server server = getServer();

        String link = getStringOption(firstOption, "link");

        boolean source = getOptionalStringOption(firstOption, "type").map(s -> s.equalsIgnoreCase("source")).orElse(false);

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

        if (source) {
            embedBuilder.setDescription(DungeonHubService.getInstance().getGson().toJson(embed));
        } else {
            DungeonHubService.getInstance().getGson().toJsonTree(embed)
                    .getAsJsonObject()
                    .entrySet()
                    .forEach(entry -> embedBuilder.addField(entry.getKey(), entry.getValue().toString()));
        }

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

        SlashCommandOption typeOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("type")
                .setDescription("Select how you want to get the embed data.")
                .setChoices(Stream.of("beautiful", "source").map(s -> new SlashCommandOptionChoiceBuilder().setName(s).setValue(s).build()).toList())
                .setRequired(false)
                .build();

        SlashCommandOption getOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("get")
                .setDescription("Gets the embed data of a message.")
                .setOptions(List.of(linkOption, typeOption))
                .build();

        SlashCommandOption channelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setName("channel")
                .setDescription("The channel to send the embed into.")
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL, ChannelType.PRIVATE_CHANNEL, ChannelType.SERVER_VOICE_CHANNEL, ChannelType.SERVER_NEWS_CHANNEL, ChannelType.SERVER_PUBLIC_THREAD))
                .setRequired(true)
                .build();

        SlashCommandOption embedOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("embed")
                .setDescription("The embed data to send.")
                .setRequired(true)
                .build();

        SlashCommandOption sendOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("send")
                .setDescription("Sends a custom embed.")
                .setOptions(List.of(channelOption, embedOption))
                .build();

        return List.of(getOption, sendOption);
    }
}