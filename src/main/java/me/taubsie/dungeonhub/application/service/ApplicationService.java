package me.taubsie.dungeonhub.application.service;

import lombok.extern.slf4j.Slf4j;
import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.StrikeData;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException;
import org.javacord.api.entity.Nameable;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.interaction.InteractionBase;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ApplicationService {
    private static ApplicationService instance;

    public static ApplicationService getInstance() {
        if (instance == null) {
            instance = new ApplicationService();
        }

        return instance;
    }

    public String getServerLink() {
        return "discord.dungeon-hub.net";
    }

    /**
     * Returns the default footer used for most embeds.
     *
     * @return the default footer used for most embeds.
     */
    public String getFooter() {
        return getServerLink() + " • made by @taubsie";
    }

    /**
     * Returns the footer used for price message embeds.
     *
     * @return the footer used for price message embeds.
     */
    public String getPriceFooter() {
        return getServerLink() + " • also see /calc-price • made by @taubsie";
    }

    public EmbedBuilder getEmbed() {
        return getEmbed(Instant.now());
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

    public EmbedBuilder getErrorEmbed() {
        return getErrorEmbed(getEmbed());
    }

    public EmbedBuilder getErrorEmbed(EmbedBuilder embed) {
        return embed.setTitle("Error").setColor(EmbedColor.NEGATIVE.getColor());
    }

    public EmbedBuilder getErrorEmbed(CommandExecutionException commandExecutionException) {
        return getErrorEmbed().setDescription(commandExecutionException.getMessage());
    }

    public void respondWithError(InteractionBase interactionBase,
                                 CommandExecutionException commandExecutionException) {
        interactionBase.createImmediateResponder()
                .setFlags(MessageFlag.EPHEMERAL)
                .addEmbed(getErrorEmbed(commandExecutionException))
                .respond();
    }

    public EmbedBuilder loadEmbedFromDiscordRole(DiscordRoleModel discordRoleModel) {
        EmbedBuilder embed = getEmbed();

        embed.addInlineField("Role", "<@&" + discordRoleModel.getId() + ">");
        embed.addInlineField("Name schema", discordRoleModel.getNameSchema() != null ?
                discordRoleModel.getNameSchema() : "none");
        embed.addInlineField("Verified role", discordRoleModel.isVerifiedRole() ? "yes" : "no");

        return embed;
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
                            .map(strikerId -> DiscordConnection.getInstance().getBot().getUserById(strikerId))
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
                                :
                                "for " + DiscordConnection.getInstance().getBot().getUserById(strikeData.getUser()).join()
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
                        + DiscordConnection.getInstance().getBot().getServerById(strikeData.getServer())
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
                                :
                                "for " + DiscordConnection.getInstance().getBot().getUserById(strikeData.getUser()).join()
                                        .getDiscriminatedName())
                        + " on server `"
                        + DiscordConnection.getInstance().getBot().getServerById(strikeData.getServer())
                        .map(Nameable::getName).orElse("unknown")
                        + "`");

        embedBuilder.addField("User", "<@" + strikeData.getUser() + ">");
        embedBuilder.addField("Striker", strikeData.getStriker() != null ? "<@" + strikeData.getStriker() + ">" :
                "CONSOLE");
        embedBuilder.addField("Reason", strikeData.getReason() != null ? strikeData.getReason() : "No reason provided" +
                ".");

        return embedBuilder;
    }

    public EmbedBuilder getCarryTypeEmbed(CarryTypeModel carryType) {
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

    public EmbedBuilder getCarryTierEmbed(CarryTierModel carryTier) {
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

    public EmbedBuilder getCarryDifficultyEmbed(CarryDifficultyModel carryDifficulty) {
        EmbedBuilder embed = getEmbed()
                .setColor(EmbedColor.DEFAULT.getColor())
                .addInlineField("Identifier", carryDifficulty.getIdentifier())
                .addInlineField("Display Name", carryDifficulty.getDisplayName())
                .addInlineField("Carry Type",
                        carryDifficulty.getCarryType().getDisplayName() + " (" + carryDifficulty.getCarryType().getIdentifier() + ")")
                .addInlineField("Carry Tier",
                        carryDifficulty.getCarryTier().getDisplayName() + " (" + carryDifficulty.getCarryTier().getIdentifier() + ")")
                .addInlineField("Price",
                        carryDifficulty.getPrice() + " (" + makeNumberReadable(carryDifficulty.getPrice()) + ")")
                .addInlineField("Score", String.valueOf(carryDifficulty.getScore()));

        carryDifficulty.getBulkAmount()
                .ifPresent(integer -> embed.addInlineField("Bulk Amount", String.valueOf(integer)));
        carryDifficulty.getBulkPrice()
                .ifPresent(integer -> embed.addInlineField("Bulk Price", String.valueOf(integer)));
        carryDifficulty.getActualThumbnailUrl().ifPresent(s -> embed.addInlineField("Thumbnail URL", s));
        carryDifficulty.getActualPriceName().ifPresent(s -> embed.addInlineField("Price Title", s));

        return embed;
    }

    public HighLevelComponent getLinkButtons() {
        return new ActionRowBuilder().addComponents(
                new ButtonBuilder()
                        .setCustomId("link_user")
                        .setEmoji("\uD83D\uDD17")
                        .setLabel("Link")
                        .setStyle(ButtonStyle.PRIMARY)
                        .build(),
                new ButtonBuilder()
                        .setCustomId("show_help_linking")
                        .setEmoji("❔")
                        .setLabel("Help")
                        .setStyle(ButtonStyle.SECONDARY)
                        .build()
        ).build();
    }
}