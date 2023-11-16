package me.taubsie.dungeonhub.application.command.commands;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.command.CommandParameters;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.Attachment;
import org.javacord.api.entity.message.component.ActionRowBuilder;
import org.javacord.api.entity.message.component.ButtonBuilder;
import org.javacord.api.entity.message.component.ButtonStyle;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

@CommandParameters(name = "qr-code", description = "Generates a QR code of the given link.", enabledInDms = true)
public class QrCodeCommand extends Command {
    @Override
    protected void executeCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteractionOption firstOption = getOptionAtIndex(0);

        boolean generate = firstOption.getName().equalsIgnoreCase("generate");

        slashCommandCreateEvent.getSlashCommandInteraction()
                .respondLater().thenAccept(updater -> {
                    if (generate) {
                        String url = getStringOption(firstOption, "url");
                        boolean cdn = getOptionalBooleanOption(firstOption, "cdn").orElse(false);
                        try {
                            BufferedImage image = ApplicationService.getInstance().generateQRCodeImage(url);

                            EmbedBuilder embed = ApplicationService.getInstance()
                                    .getEmbed()
                                    .setColor(EmbedColor.DEFAULT.getColor())
                                    .setImage(image);

                            if (cdn && getUser().isBotOwnerOrTeamMember()) {
                                String cdnLink = ContentConnection.getInstance()
                                        .uploadFile(ApplicationService.getInstance().readImageData(image))
                                        .map(s -> ConfigProperty.CDN_URL + s)
                                        .orElse("Couldn't be generated!");

                                embed.addField("CDN Link", cdnLink);
                            }

                            updater.addEmbed(embed);
                        }
                        catch (WriterException writerException) {
                            updater.addEmbed(ApplicationService.getInstance().getErrorEmbed());
                        }
                    } else {
                        Attachment attachment = getAttachmentOption(firstOption, "qr-code");
                        if (attachment.isImage()) {
                            try {
                                String result = ApplicationService.getInstance()
                                        .readQRCodeImage(attachment.asImage().join());

                                updater.addEmbed(
                                        ApplicationService.getInstance()
                                                .getEmbed()
                                                .setUrl(result)
                                                .setDescription("The given QR code leads to the site:\n" + result)
                                                .setColor(EmbedColor.DEFAULT.getColor())
                                ).addComponents(new ActionRowBuilder()
                                        .addComponents(
                                                new ButtonBuilder()
                                                        .setStyle(ButtonStyle.LINK)
                                                        .setLabel("Result")
                                                        .setUrl(result)
                                                        .build()
                                        ).build());
                            }
                            catch (ChecksumException | NotFoundException | FormatException e) {
                                updater.addEmbed(ApplicationService.getInstance().getErrorEmbed());
                            }
                        } else {
                            updater.addEmbed(ApplicationService.getInstance().getErrorEmbed(
                                    ApplicationService.getInstance().getEmbed()
                                            .setDescription("Please input a valid picture.")
                            ));
                        }
                    }
                    updater.update();
                });
    }

    @Override
    public List<SlashCommandOption> getSlashCommandOptions() {
        SlashCommandOption urlOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.STRING)
                .setName("url")
                .setDescription("The url of the QR code.")
                .setRequired(true)
                .build();

        SlashCommandOption cdnOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.BOOLEAN)
                .setName("cdn")
                .setDescription("Set if a unique link should also be generated.")
                .setRequired(false)
                .build();

        SlashCommandOption generateOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("generate")
                .setDescription("Generate a QR code from an URL.")
                .setOptions(List.of(urlOption, cdnOption))
                .build();

        SlashCommandOption attachmentOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.ATTACHMENT)
                .setName("qr-code")
                .setDescription("The QR code to get the content from.")
                .setRequired(true)
                .build();

        SlashCommandOption readOption = new SlashCommandOptionBuilder()
                .setType(SlashCommandOptionType.SUB_COMMAND)
                .setName("read")
                .setDescription("Read the URL from a QR code.")
                .setOptions(List.of(attachmentOption))
                .build();

        return Arrays.asList(generateOption, readOption);
    }
}