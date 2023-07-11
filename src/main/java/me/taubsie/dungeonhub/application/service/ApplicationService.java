package me.taubsie.dungeonhub.application.service;

import com.google.gson.JsonObject;
import me.taubsie.dungeonhub.application.command.Command;
import me.taubsie.dungeonhub.application.connection.HypixelConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.start.BotStarter;
import me.taubsie.dungeonhub.common.*;
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
import org.javacord.api.interaction.Interaction;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ApplicationService {
    private static ApplicationService instance;

    public static ApplicationService getInstance() {
        if (instance == null) {
            instance = new ApplicationService();
        }

        return instance;
    }

    public EmbedBuilder getEmbed() {
        return getEmbed(Instant.now());
    }

    public String getFooter() {
        return "discord.gg/dungeons • made by @taubsie";
    }

    public String getPriceFooter() {
        return "discord.gg/dungeons • also see /calc-price • made by @taubsie";
    }

    public EmbedBuilder getEmbedWithoutTimestamp() {
        return new EmbedBuilder()
                .setFooter(getFooter());
    }

    public EmbedBuilder getEmbed(Instant time) {
        return new EmbedBuilder()
                .setTimestamp(time)
                .setFooter(getFooter());
    }

    public User getBotOwner(DiscordApi api) {
        return Objects.requireNonNull(api.getCachedTeam()
                .map(team -> team.requestOwner().join())
                .orElseGet(() -> api.getOwner().map(CompletableFuture::join)
                        .orElse(null)));
    }

    public String makeDoubleReadable(double number) {
        DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.US));
        df.setMaximumFractionDigits(340); //340 = DecimalFormat.DOUBLE_FRACTION_DIGITS

        return df.format(number);
    }

    public String makeNumberReadable(long number) {
        if (number >= 1000000000000L) {
            return makeDoubleReadable(number / 1000000000000.0) + "t";
        }

        if (number >= 1000000000L) {
            return makeDoubleReadable(number / 1000000000.0) + "b";
        }

        if (number >= 1000000L) {
            return makeDoubleReadable(number / 1000000.0) + "m";
        }

        if (number >= 1000L) {
            return makeDoubleReadable(number / 1000.0) + "k";
        }

        return String.valueOf(number);
    }

    public DiscordApiBuilder getApiBuilder() {
        return new DiscordApiBuilder()
                .setToken(ConfigProperty.DISCORD_BOT_TOKEN.getValue())
                .setAllNonPrivilegedIntents()
                .addIntents(Intent.MESSAGE_CONTENT, Intent.GUILD_MEMBERS);
    }

    public EmbedBuilder getErrorEmbed() {
        return getErrorEmbed(getEmbed());
    }

    public EmbedBuilder getErrorEmbed(EmbedBuilder embed) {
        return embed.setTitle("Error").setColor(EmbedColor.NEGATIVE.getColor());
    }

    public void respondWithError(Interaction interaction,
                                 CommandExecutionException commandExecutionException) {
        interaction.createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(getErrorEmbed().setDescription(commandExecutionException.getMessage()))
                .respond();
    }

    public Optional<Command> getCommand(SlashCommandCreateEvent slashCommandCreateEvent) {
        return ApplicationClassLoaderService.getInstance().getCommand(
                slashCommandCreateEvent.getSlashCommandInteraction().getCommandName(),
                slashCommandCreateEvent.getSlashCommandInteraction().getServer().orElse(null)
        );
    }

    public EmbedBuilder loadEmbedFromCarryInformation(CarryInformation carryInformation, EmbedBuilder embedBuilder) {
        embedBuilder.setColor(EmbedColor.INFORMATION.getColor())
                .addInlineField("Number of carries",
                        String.valueOf(carryInformation.getAmountOfCarries()))
                .addInlineField("Type of carry",
                        carryInformation.getCarryTier().getDisplayName() + " - " + carryInformation.getCarryDifficulty().getDisplayName())
                .addInlineField("Player", "<@" + carryInformation.getPlayer() + ">")
                .addInlineField("Carrier", "<@" + carryInformation.getCarrier() + ">");

        if (carryInformation.getApprover() != null) {
            embedBuilder.addInlineField("Approved by", "<@" + carryInformation.getApprover() + ">");
        }

        if (carryInformation.getAttachmentLink() != null) {
            embedBuilder.addInlineField("Transcript-Link", "[Click to open]" +
                    "(https://tickettool.xyz/direct?url=" + carryInformation.getAttachmentLink() + ")");
        }

        return embedBuilder;
    }

    public EmbedBuilder loadEmbedFromCarryInformation(CarryInformation carryInformation) {
        return loadEmbedFromCarryInformation(carryInformation, getEmbed(carryInformation.getTime()));
    }

    public EmbedBuilder formatStrikes(List<StrikeData> strikeData, User user, int page) {
        EmbedBuilder embedBuilder = getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Strikes of user " + user.getDiscriminatedName());

        if (strikeData.isEmpty()) {
            embedBuilder.setDescription("User has no strikes!");
            return embedBuilder;
        }

        strikeData.stream()
                .skip(DungeonHubService.getInstance().getOffsetFromPageNumber(page))
                .limit(10)
                .forEach(strike -> {
                    String striker = Optional.ofNullable(strike.getStriker())
                            .map(strikerId -> BotStarter.getInstance().getBot().getUserById(strikerId))
                            .map(CompletableFuture::join)
                            .map(User::getDiscriminatedName)
                            .orElse("CONSOLE");

                    String reason = Optional.ofNullable(strike.getReason())
                            .map(s -> " because of \"" + s + "\"")
                            .orElse("");

                    embedBuilder.addField("Strike #" + strike.getId(),
                            "By " + striker + " at <t:" + strike.getStrikeTime().toEpochMilli() + ">" + reason);
                });

        return embedBuilder;
    }

    public EmbedBuilder formatStrikeLog(StrikeData strikeData) {
        EmbedBuilder embedBuilder = getEmbed(strikeData.getStrikeTime())
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("Strike " +
                        (strikeData.getId() != null
                                ? "#" + strikeData.getId()
                                : "for " + BotStarter.getInstance().getBot().getUserById(strikeData.getUser()).join()
                                .getDiscriminatedName()));

        embedBuilder.addField("User", "<@" + strikeData.getUser() + ">");
        embedBuilder.addField("Striker", strikeData.getStriker() != null ? "<@" + strikeData.getStriker() + ">" :
                "CONSOLE");
        embedBuilder.addField("Reason", strikeData.getReason() != null ? strikeData.getReason() : "No reason provided" +
                ".");

        return embedBuilder;
    }

    public EmbedBuilder formatStrikeDM(StrikeData strikeData) {
        EmbedBuilder embedBuilder = getEmbed(strikeData.getStrikeTime())
                .setColor(EmbedColor.INFORMATION.getColor())
                .setTitle("You were striked on server `"
                        + BotStarter.getInstance().getBot().getServerById(strikeData.getServer())
                        .map(Nameable::getName).orElse("unknown")
                        + "`");

        embedBuilder.addField("You", "<@" + strikeData.getUser() + ">");
        embedBuilder.addField("Reason", strikeData.getReason() != null ? strikeData.getReason() : "No reason provided" +
                ".");

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

    public EmbedBuilder getNoCarryTypeFoundEmbed() {
        return getEmbed()
                .setTitle("No score was found!")
                .setDescription("Please make sure that a carry type is setup on this server.\n" +
                        "For more information about how to do this, contact `@taubsie` (<@356134481452597250>)!")
                .setColor(EmbedColor.NEGATIVE.getColor());
    }

    public EmbedBuilder getScoreCountMessage(User userToCheck, User user, Server server, List<ScoreValue> scoreCount) {
        if (scoreCount.isEmpty()) {
            return getNoCarryTypeFoundEmbed();
        }

        EmbedBuilder embed = getEmbed()
                .setTitle((userToCheck.getId() != user.getId() && server != null)
                        ? userToCheck.getDisplayName(server) + "'s score:"
                        : "Your score:")
                .setColor(EmbedColor.DEFAULT.getColor());

        Map<ScoreType, List<String>> scoreDescriptions = new EnumMap<>(ScoreType.class);

        scoreCount.forEach(scoreValue -> {
            if (scoreValue.scoreType() == ScoreType.EVENT && !scoreValue.carryType().isEventActive()) {
                return;
            }

            String description = scoreValue.carryType().getDisplayName() + ": " + scoreValue.amount();

            if (scoreDescriptions.containsKey(scoreValue.scoreType())) {
                scoreDescriptions.get(scoreValue.scoreType()).add(description);
            } else {
                scoreDescriptions.put(scoreValue.scoreType(), new ArrayList<>(List.of(description)));
            }
        });

        scoreDescriptions
                .forEach((carryType, strings) -> embed.addInlineField(
                        carryType.getDisplayName(),
                        String.join(System.lineSeparator(), strings)
                ));

        return embed;
    }

    public SlashCommandOption getIngamenameOption() {
        return new SlashCommandOptionBuilder()
                .setName("ign")
                .setDescription("The users ingame-name")
                .setType(SlashCommandOptionType.STRING)
                .setMinLength(2)
                .setRequired(true)
                .build();
    }

    //TODO maybe make it possible to update the embed in 2 intervals, since the mojang+safety+jerry api takes long,
    // as well as the skycrypt api takes long too
    //probably first load skycrypt, then the rest?
    public EmbedBuilder getPlayerDataEmbed(String ign) {
        Map<String, String> skycryptData = HypixelConnection.getInstance().getSkyCryptData(ign);

        String description = skycryptData.getOrDefault("description", "Couldn't load SkyCrypt data.");

        EmbedBuilder embed = ApplicationService.getInstance()
                .getEmbed()
                .setColor(EmbedColor.INFORMATION.getColor())
                .setDescription(description)
                .setTitle(skycryptData.getOrDefault("title", ign))
                .setUrl("https://sky.shiiyu.moe/stats/" + ign)
                .setThumbnail(skycryptData.getOrDefault("icon", null));

        /*String uuid = ConnectionService.getInstance()
                .getUUIDByName(ign);

        if(uuid != null) {
            String[] flagged = ConnectionService.getInstance()
                    .isFlagged(uuid, false);

            if(flagged.length == 0) {
                embed.addInlineField("Flagged", "User is not flagged.");
            } else {
                embed.addInlineField("Flagged", "User is flagged, this means it is not safe to interact with them.\n"
                        + String.join(": ", flagged));
            }
        }*/

        embed.addInlineField("Flagged", "Please remember to run `/lookup`.");

        return embed;
    }

    //TODO remove json object, maybe only work on strings if possible -> connection service as an api only
    public EmbedBuilder loadAuctionsMessage(List<JsonObject> auctionData, int page) {
        if (auctionData.isEmpty()) {
            return getEmbed()
                    .setColor(EmbedColor.NEGATIVE.getColor())
                    .setTitle("No auctions found! Try again later.");
        }

        return getEmbed()
                .setColor(EmbedColor.POSITIVE.getColor())
                .setTitle("Auctions:")
                .setDescription(String.join("\n", auctionData.stream()
                        .skip(DungeonHubService.getInstance().getOffsetFromPageNumber(page))
                        .limit(10)
                        .map(jsonObject -> "`/viewauction "
                                + jsonObject.getAsJsonPrimitive("uuid").getAsString()
                                + "` - "
                                + jsonObject.getAsJsonPrimitive("item_name").getAsString())
                        .toList()));
    }

    public EmbedBuilder getCarryTypeEmbed(CarryType carryType) {
        EmbedBuilder embed = getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor())
                .addInlineField("Identifier", carryType.getIdentifier())
                .addInlineField("Display Name", carryType.getDisplayName());

        carryType.getLogChannel().ifPresent(logChannel -> embed.addInlineField("Log Channel", "<#" + logChannel + ">"));
        carryType.getLeaderboardChannel().ifPresent(leaderboardChannel -> embed.addInlineField("Leaderboard Channel",
                "<#" + leaderboardChannel + ">"));
        embed.addInlineField("Event active", carryType.isEventActive() ? "yes" : "no");

        return embed;
    }

    public EmbedBuilder getCarryTierEmbed(CarryTier carryTier) {
        EmbedBuilder embed = getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor())
                .addInlineField("Identifier", carryTier.getIdentifier())
                .addInlineField("Display Name", carryTier.getDisplayName())
                .addInlineField("Descriptive Name", carryTier.getDescriptiveName())
                .addInlineField("Carry Type",
                        carryTier.getCarryType().getDisplayName() + " (" + carryTier.getCarryType().getIdentifier() + ")");

        carryTier.getCategory().ifPresent(category -> embed.addInlineField("Category", "<#" + category + ">"));
        carryTier.getPriceChannel().ifPresent(priceChannel -> embed.addInlineField("Price Channel",
                "<#" + priceChannel + ">"));
        carryTier.getThumbnailUrl().ifPresent(thumbnailUrl -> embed.addInlineField("Thumbnail URL", thumbnailUrl));
        carryTier.getActualPriceTitle().ifPresent(s -> embed.addInlineField("Price Title", s));
        carryTier.getPriceDescription().ifPresent(s -> embed.addInlineField("Price Description", s));

        return embed;
    }

    public EmbedBuilder getCarryDifficultyEmbed(CarryDifficulty carryDifficulty) {
        EmbedBuilder embed = getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor())
                .addInlineField("Identifier", carryDifficulty.getIdentifier())
                .addInlineField("Display Name", carryDifficulty.getDisplayName())
                .addInlineField("Carry Type",
                        carryDifficulty.getCarryType().getDisplayName() + " (" + carryDifficulty.getCarryType().getIdentifier() + ")")
                .addInlineField("Carry Tier",
                        carryDifficulty.getCarryTier().getDisplayName() + " (" + carryDifficulty.getCarryTier().getIdentifier() + ")")
                .addInlineField("Price", carryDifficulty.getPrice() + " (" + makeNumberReadable(carryDifficulty.getPrice()) + ")")
                .addInlineField("Score", String.valueOf(carryDifficulty.getScore()));

        carryDifficulty.getBulkAmount()
                .ifPresent(integer -> embed.addInlineField("Bulk Amount", String.valueOf(integer)));
        carryDifficulty.getBulkPrice()
                .ifPresent(integer -> embed.addInlineField("Bulk Price", String.valueOf(integer)));
        carryDifficulty.getActualThumbnailUrl().ifPresent(s -> embed.addInlineField("Thumbnail URL", s));
        carryDifficulty.getActualPriceName().ifPresent(s -> embed.addInlineField("Price Title", s));

        return embed;
    }
}