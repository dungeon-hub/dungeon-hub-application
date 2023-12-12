package me.taubsie.dungeonhub.application.command.commands;

import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.enums.KnownStaticResource;
import me.taubsie.dungeonhub.application.exceptions.MissingPermissionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.PermissionService;
import org.javacord.api.entity.Attachment;
import org.javacord.api.entity.message.component.ActionRowBuilder;
import org.javacord.api.entity.message.component.ButtonBuilder;
import org.javacord.api.entity.message.component.ButtonStyle;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "cdn", description = "Manage the CDN", enabledForUsers = {356134481452597250L,
        531094512819240960L, 564353701003657216L, 574048571364605992L, 1116284449190064220L, 795048346955677748L,
        884589309037011015L, 346292488837005334L},
        enabledInDms = true)
public class CdnCommand extends Command {
    @Override
    public long[] getEnabledServers() {
        return new long[]{693263712626278553L, 1023684107877761196L};
    }

    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        User user = getUser();

        if (!PermissionService.getInstance().mayUseCdn(user)) {
            throw new MissingPermissionException();
        }

        SlashCommandInteractionOption firstOption =
                getOptionAtIndex(slashCommandCreateEvent.getSlashCommandInteraction(), 0);

        if (firstOption.getName().equalsIgnoreCase("static")) {
            KnownStaticResource resource = getEnumOption(firstOption, "file", KnownStaticResource.class);

            String url = ContentConnection.getInstance().getApiUrl(resource.getPath()).toString();

            respondEphemeral(ApplicationService.getInstance()
                            .getEmbed()
                            .setColor(EmbedColor.POSITIVE.getColor())
                            .setTitle("Static resource")
                            .addInlineField("Name", resource.getDisplayName())
                            .addInlineField("File name", resource.getName())
                            .addField("Full URL", url)
                            .setImage(url),
                    new ActionRowBuilder().addComponents(
                            new ButtonBuilder().setStyle(ButtonStyle.LINK)
                                    .setUrl(url)
                                    .setLabel("Open")
                                    .build()
                    ).build());

            return;
        }

        Attachment fileOption = getAttachmentOption(firstOption, "file");

        Optional<String> nameOption = getOptionalStringOption(firstOption, "name");

        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater(true)
                .thenAccept(updater -> {
                    Optional<String> fileUrl;

                    if (nameOption.isPresent()) {
                        fileUrl = ContentConnection.getInstance()
                                .uploadFile(fileOption.asByteArray().join(), nameOption.get());
                    } else {
                        fileUrl = ContentConnection.getInstance()
                                .uploadFile(fileOption.asByteArray().join());
                    }

                    EmbedBuilder embedBuilder;
                    if (fileUrl.isPresent()) {
                        String url = ContentConnection.getInstance().getApiUrl(fileUrl.get()).toString();

                        embedBuilder = ApplicationService.getInstance()
                                .getEmbed()
                                .setTitle("File added.")
                                .setColor(EmbedColor.POSITIVE.getColor())
                                .setImage(url)
                                .setFooter(ApplicationService.getInstance()
                                        .getUnstableFooter())
                                .setTimestamp(null)
                                .addField("URL", url);

                        updater.addComponents(new ActionRowBuilder()
                                .addComponents(new ButtonBuilder()
                                        .setStyle(ButtonStyle.LINK)
                                        .setUrl(url)
                                        .setLabel("Click to open")
                                        .build())
                                .build());
                    } else {
                        embedBuilder = ApplicationService.getInstance()
                                .getErrorEmbed()
                                .setDescription("Couldn't upload media file.");
                    }

                    updater.addEmbed(embedBuilder).update();
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption attachmentOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.ATTACHMENT)
                .setName("file")
                .setDescription("The file to add.")
                .setRequired(true)
                .build();

        SlashCommandOption nameOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("name")
                .setDescription("The name of the file.")
                .setRequired(false)
                .build();

        SlashCommandOption addOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("add")
                .setDescription("Add a file to the CDN.")
                .setOptions(List.of(attachmentOption, nameOption))
                .build();

        SlashCommandOptionBuilder fileOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("file")
                .setDescription("The static file to get.")
                .setRequired(true);

        Arrays.stream(KnownStaticResource.values()).forEach(ressource -> fileOption.addChoice(ressource.getDisplayName(), ressource.name()));

        SlashCommandOption staticOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("static")
                .setDescription("Show the list of static files of the CDN.")
                .setOptions(List.of(fileOption.build()))
                .build();

        return List.of(addOption, staticOption);
    }
}