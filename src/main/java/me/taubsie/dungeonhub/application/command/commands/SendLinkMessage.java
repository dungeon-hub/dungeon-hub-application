package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.channel.ChannelType;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.List;

@CommandParameters(name = "send-link-message", description = "Sends a message with components that are there to make linking easier.", enabledForPermissions = {PermissionType.MANAGE_MESSAGES})
public class SendLinkMessage extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        TextChannel channel = getChannelOption(slashCommandCreateEvent.getSlashCommandInteraction(), "channel")
                .asTextChannel().orElseThrow();

        channel.sendMessage(getEmbed()
                        .setColor(EmbedColor.DEFAULT.getColor())
                        .setTitle("Linking")
                        .setDescription("Please link to your Minecraft account using the buttons below.\n" +
                                "Remember to never give out the email connected to your Microsoft account and to never click any links!"),
                ApplicationService.getInstance().getLinkButtons()
        );

        respond(ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setDescription("Trying to send message..."));
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption channelOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.CHANNEL)
                .setName("channel")
                .setDescription("The channel to send the message into.")
                .setChannelTypes(List.of(ChannelType.SERVER_TEXT_CHANNEL, ChannelType.PRIVATE_CHANNEL, ChannelType.SERVER_VOICE_CHANNEL, ChannelType.SERVER_NEWS_CHANNEL, ChannelType.SERVER_PUBLIC_THREAD))
                .setRequired(true)
                .build();

        return List.of(channelOption);
    }
}