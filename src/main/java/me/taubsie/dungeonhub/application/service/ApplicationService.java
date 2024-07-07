package me.taubsie.dungeonhub.application.service;

import lombok.extern.slf4j.Slf4j;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel;
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel;
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel;
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.Locale;

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

    public EmbedBuilder getEmbed() {
        return getEmbed(Instant.now());
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

    public EmbedBuilder getErrorEmbed(EmbedBuilder embed) {
        return embed.setTitle("Error").setColor(EmbedColor.NEGATIVE.getColor());
    }

    public EmbedBuilder loadEmbedFromDiscordRole(DiscordRoleModel discordRoleModel) {
        EmbedBuilder embed = getEmbed();

        embed.addInlineField("Role", "<@&" + discordRoleModel.getId() + ">");
        embed.addInlineField("Name schema", discordRoleModel.getNameSchema() != null ?
                discordRoleModel.getNameSchema() : "none");
        embed.addInlineField("Verified role", discordRoleModel.isVerifiedRole() ? "yes" : "no");

        return embed;
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
}