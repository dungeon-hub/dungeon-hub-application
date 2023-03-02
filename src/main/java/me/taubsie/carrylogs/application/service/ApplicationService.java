package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.enums.CarryType;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.carrylogs.config.ConfigProperty;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.*;

public class ApplicationService {
    private static ApplicationService instance;

    public static ApplicationService getInstance() {
        if(instance == null) {
            instance = new ApplicationService();
        }

        return instance;
    }

    public EmbedBuilder getEmbed() {
        return getEmbed(Instant.now());
    }

    public EmbedBuilder getEmbed(Instant time) {
        return new EmbedBuilder()
                .setTimestamp(time)
                .setFooter("discord.gg/dungeons • made by Taubsie#0911");
    }

    public boolean isCarryTier(String carryTier, CarryType carryType) {
        if(!isCarryTier(carryTier)) {
            return false;
        }

        return switch(carryType) {
            case F4 -> switch(carryTier) {
                case "Completion", "S" -> true;
                default -> false;
            };
            case F5, F6, F7 -> switch(carryTier) {
                case "Completion", "S", "S+" -> true;
                default -> false;
            };
            case EMAN -> switch(carryTier) {
                case "Tier 3", "Tier 4" -> true;
                default -> false;
            };
            case BLAZE -> switch(carryTier) {
                case "Tier 2", "Tier 3", "Tier 4" -> true;
                default -> false;
            };
            case KUUDRA -> switch(carryTier) {
                case "Basic", "Hot", "Burning", "Fiery", "Infernal" -> true;
                default -> false;
            };
            case MASTER_MODE -> switch(carryTier) {
                case "Floor 1", "Floor 2", "Floor 3", "Floor 4", "Floor 5", "Floor 6", "Floor 7" -> true;
                default -> false;
            };
        };
    }

    public String makeDoubleReadable(double number) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.US));
        df.setMaximumFractionDigits(340); //340 = DecimalFormat.DOUBLE_FRACTION_DIGITS

        return df.format(number);
    }

    public String makeNumberReadable(long number) {
        if(number >= 1000000000000L) {
            return makeDoubleReadable(number / 1000000000000.0) + "t";
        }

        if(number >= 1000000000L) {
            return makeDoubleReadable(number / 1000000000.0) + "b";
        }

        if(number >= 1000000L) {
            return makeDoubleReadable(number / 1000000.0) + "m";
        }

        if(number >= 1000L) {
            return makeDoubleReadable(number / 1000.0) + "k";
        }

        return String.valueOf(number);
    }

    public String getCarryTierUrl(CarryType carryType, String carryTier) {
        return switch(carryType) {
            case EMAN -> "https://cdn.discordapp.com/attachments/842827272733982730/992919618236719134/unknown.png";
            case BLAZE -> "https://cdn.discordapp.com/attachments/842827272733982730/992919430369656852/unknown.png";
            case KUUDRA ->
                    "https://cdn.discordapp.com/attachments/842827272733982730/1080981866657615872/Minecraft_entities_magma_cube.png";
            case F4 -> "https://cdn.discordapp.com/emojis/759298333608378388.png?v=1";
            case F5 -> "https://cdn.discordapp.com/emojis/759298251068801044.png?v=1";
            case F6 -> "https://cdn.discordapp.com/emojis/761951536829825035.png?v=1";
            case F7 -> "https://cdn.discordapp.com/emojis/792055627248566312.webp?size=80&quality=lossless";
            case MASTER_MODE -> switch(carryTier) {
                case "Floor 1", "Floor 2", "Floor 3" -> null;
                case "Floor 4" -> "https://cdn.discordapp.com/emojis/759298333608378388.png?v=1";
                case "Floor 5" -> "https://cdn.discordapp.com/emojis/759298251068801044.png?v=1";
                case "Floor 6" -> "https://cdn.discordapp.com/emojis/761951536829825035.png?v=1";
                case "Floor 7" -> "https://cdn.discordapp.com/emojis/792055627248566312.webp?size=80&quality=lossless";
            };
        };
    }

    public boolean isCarryTier(String carryTier) {
        return switch(carryTier) {
            case "Completion",
                    "S",
                    "S+",
                    "Floor 1",
                    "Floor 2",
                    "Floor 3",
                    "Floor 4",
                    "Floor 5",
                    "Floor 6",
                    "Floor 7",
                    "Basic",
                    "Hot",
                    "Burning",
                    "Fiery",
                    "Infernal",
                    "Tier 2",
                    "Tier 3",
                    "Tier 4" -> true;
            default -> false;
        };
    }

    public DiscordApiBuilder getApiBuilder() {
        return new DiscordApiBuilder()
                .setToken(ConfigProperty.DISCORD_BOT_TOKEN.getValue())
                .setAllNonPrivilegedIntents()
                .addIntents(Intent.MESSAGE_CONTENT, Intent.GUILD_MEMBERS);
    }

    public void respondWithError(SlashCommandCreateEvent slashCommandCreateEvent,
                                 CommandExecutionException commandExecutionException) {
        slashCommandCreateEvent.getSlashCommandInteraction()
                .createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(ApplicationService.getInstance()
                        .getEmbed()
                        .setTitle("Error")
                        .setDescription(commandExecutionException.getMessage())
                        .setColor(new Color(255, 0, 0 /*TODO color*/)))
                .respond();
    }

    public Optional<Command> getCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        return ApplicationClassLoaderService.getInstance().getCommand(
                slashCommandCreateEvent.getSlashCommandInteraction().getCommandName(),
                slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElse(null)
        );
    }
}