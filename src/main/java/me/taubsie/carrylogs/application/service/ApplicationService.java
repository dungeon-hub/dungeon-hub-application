package me.taubsie.carrylogs.application.service;

import me.taubsie.carrylogs.application.enums.EmbedColor;
import me.taubsie.carrylogs.application.start.BotStarter;
import me.taubsie.dungeonhub.common.CarryInformation;
import me.taubsie.carrylogs.application.command.Command;
import me.taubsie.carrylogs.application.enums.CarryType;
import me.taubsie.carrylogs.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.common.StrikeData;
import me.taubsie.dungeonhub.common.config.ConfigProperty;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.Nameable;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    public User getBotOwner(DiscordApi api) {
        return Objects.requireNonNull(api.getCachedTeam()
                .map(team -> team.requestOwner().join())
                .orElse(api.getOwner().map(CompletableFuture::join)
                        .orElse(null)));
    }

    public boolean isCarryTier(String carryTier, CarryType carryType) {
        if(isInvalidCarryTier(carryTier)) {
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
            case KUUDRA -> "https://cdn.discordapp.com/attachments/842827272733982730/1080981866657615872" +
                    "/Minecraft_entities_magma_cube.png";
            case F4 -> "https://cdn.discordapp.com/emojis/759298333608378388.png?v=1";
            case F5 -> "https://cdn.discordapp.com/emojis/759298251068801044.png?v=1";
            case F6 -> "https://cdn.discordapp.com/emojis/761951536829825035.png?v=1";
            case F7 -> "https://cdn.discordapp.com/emojis/792055627248566312.webp?size=80&quality=lossless";
            case MASTER_MODE -> switch(carryTier) {
                case "Floor 1" -> "https://cdn.discordapp.com/attachments/842827272733982730/1081302674244391023" +
                        "/SkyBlock_npcs_bonzo_undead.png";
                case "Floor 2" -> "https://cdn.discordapp.com/attachments/842827272733982730/1081302768633000006" +
                        "/SkyBlock_entities_scarf.png";
                case "Floor 3" ->
                        "https://cdn.discordapp.com/attachments/842827272733982730/1081302836857557022/latest.png";
                case "Floor 4" -> "https://cdn.discordapp.com/emojis/759298333608378388.png?v=1";
                case "Floor 5" -> "https://cdn.discordapp.com/emojis/759298251068801044.png?v=1";
                case "Floor 6" -> "https://cdn.discordapp.com/emojis/761951536829825035.png?v=1";
                case "Floor 7" -> "https://cdn.discordapp.com/emojis/792055627248566312.webp?size=80&quality=lossless";
                default -> null;
            };
        };
    }

    public boolean isInvalidCarryTier(String carryTier) {
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
                    "Tier 4" -> false;
            default -> true;
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
                        .setColor(EmbedColor.NEGATIVE.getColor()))
                .respond();
    }

    public Optional<Command> getCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        return ApplicationClassLoaderService.getInstance().getCommand(
                slashCommandCreateEvent.getSlashCommandInteraction().getCommandName(),
                slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElse(null)
        );
    }

    public EmbedBuilder loadEmbedFromCarryInformation(CarryInformation carryInformation, EmbedBuilder embedBuilder) {
        CarryType carryType = CarryType.fromString(carryInformation.getCarryDifficulty());

        embedBuilder.setColor(EmbedColor.INFORMATION.getColor())
                .addInlineField("Number of carries",
                        String.valueOf(carryInformation.getAmountOfCarries()))
                .addInlineField("Type of carry",
                        (carryType != null ? carryType.getPrettyName() : carryInformation.getCarryDifficulty())
                                + " - " + carryInformation.getCarryType())
                .addInlineField("Player", "<@" + carryInformation.getPlayer() + ">")
                .addInlineField("Carrier", "<@" + carryInformation.getCarrier() + ">");

        if(carryInformation.getApprover() != null) {
            embedBuilder.addInlineField("Approved by", "<@" + carryInformation.getApprover() + ">");
        }

        if(carryInformation.getAttachmentLink() != null) {
            embedBuilder.addInlineField("Transcript-Link", "[Click to open]" +
                    "(https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")");
        }

        return embedBuilder;
    }

    public EmbedBuilder loadEmbedFromCarryInformation(CarryInformation carryInformation) {
        return loadEmbedFromCarryInformation(carryInformation, getEmbed(carryInformation.getTime()));
    }

    public EmbedBuilder formatStrikes(List<StrikeData> strikeData, User user) {
        EmbedBuilder embedBuilder = getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Strikes of user " + user.getDiscriminatedName());

        if(strikeData.isEmpty()) {
            embedBuilder.setDescription("User has no strikes!");
            return embedBuilder;
        }

        int count = 0;
        for(StrikeData strike : strikeData) {
            String striker = Optional.ofNullable(strike.getStriker())
                    .map(strikerId -> BotStarter.getInstance().getBot().getUserById(strikerId))
                    .map(CompletableFuture::join)
                    .map(User::getDiscriminatedName)
                    .orElse("CONSOLE");

            String reason = Optional.ofNullable(strike.getReason())
                    .map(s -> " because of \"" + s + "\"")
                    .orElse("");

            embedBuilder.addField("Strike #" + ++count,
                    "By " + striker + " at <t:" + strike.getStrikeTime().toEpochMilli() + ">" + reason);
        }

        return embedBuilder;
    }

    public EmbedBuilder formatStrike(StrikeData strikeData) {
        EmbedBuilder embedBuilder = getEmbed(strikeData.getStrikeTime())
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Strike " +
                        (strikeData.getId() != null
                                ? "#" + strikeData.getId()
                                : "for " + BotStarter.getInstance().getBot().getUserById(strikeData.getUser()).join()
                                .getDiscriminatedName())
                        + " on server `"
                        + BotStarter.getInstance().getBot().getServerById(strikeData.getServer())
                        .map(Nameable::getName).orElse("unknown")
                        + "`");

        embedBuilder.addField("User", "<@" + strikeData.getUser() + ">");
        embedBuilder.addField("Striker", strikeData.getStriker() != null ? "<@" + strikeData.getStriker() + ">" :
                "CONSOLE");
        embedBuilder.addField("Reason", strikeData.getReason() != null ? strikeData.getReason() : "No reason provided" +
                ".");

        return embedBuilder;
    }

    public EmbedBuilder getScoreCountMessage(User userToCheck, User user, Server server, Map<String, Long> scoreCount) {
        return getEmbed()
                .setTitle((userToCheck.getId() != user.getId() && server != null)
                        ? userToCheck.getDisplayName(server) + "'s score:"
                        : "Your score:")
                .setColor(EmbedColor.DEFAULT.getColor())
                .addInlineField("Dungeon-Score:", String.valueOf(scoreCount.get("dungeon")))
                .addInlineField("Slayer-Score:", String.valueOf(scoreCount.get("slayer")))
                .addInlineField("Kuudra-Score:", String.valueOf(scoreCount.get("kuudra")))
                .addInlineField("Alltime-Dungeon-Score:", String.valueOf(scoreCount.get("alltime-dungeon")))
                .addInlineField("Alltime-Slayer-Score:", String.valueOf(scoreCount.get("alltime-slayer")))
                .addInlineField("Alltime-Kuudra-Score:", String.valueOf(scoreCount.get("alltime-kuudra")))
                .addInlineField("Event-Dungeon-Score:", String.valueOf(scoreCount.get("event-dungeon")))
                .addInlineField("Event-Slayer-Score:", String.valueOf(scoreCount.get("event-slayer")));
    }
}