package me.taubsie.dungeonhub.kord.application.service

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.EmbedBuilder.Footer
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import me.taubsie.dungeonhub.application.connection.FlaggingConnection
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.carry.CarryModel
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel
import me.taubsie.dungeonhub.common.model.score.ScoreModel
import me.taubsie.dungeonhub.common.model.warning.DetailedWarningModel
import me.taubsie.dungeonhub.common.model.warning.WarningModel
import me.taubsie.dungeonhub.kord.application.config.ConfigProperty
import me.taubsie.dungeonhub.kord.application.connection.DiscordConnection
import me.taubsie.dungeonhub.kord.application.connection.HypixelConnection
import me.taubsie.dungeonhub.kord.application.enums.EmbedColor
import me.taubsie.dungeonhub.kord.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.kord.application.exceptions.FailedToLoadEmbedException
import me.taubsie.dungeonhub.kord.application.misc.FlagResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import javax.imageio.ImageIO

object ApplicationService {
    const val MAX_MINECRAFT_USERNAME_LENGTH = 16
    private val logger: Logger = LoggerFactory.getLogger(DiscordConnection::class.java)

    private val serverLink: String
        get() = "discord.dungeon-hub.net"

    val footer: Footer
        /**
         * Returns the default footer used for most embeds.
         *
         * @return the default footer used for most embeds.
         */
        get() {
            val footer = Footer()
            footer.text = "$serverLink • made by @taubsie"
            return footer
        }

    val unstableFooter: String
        /**
         * Returns the footer used for embeds of unstable or new features.
         *
         * @return the footer used for embeds of unstable or new features.
         */
        get() = "$serverLink • THIS FEATURE IS UNSTABLE • please report bugs to @taubsie"

    val priceFooter: String
        /**
         * Returns the footer used for price message embeds.
         *
         * @return the footer used for price message embeds.
         */
        get() = "$serverLink • also see /calc-price • made by @taubsie"

    val embed: EmbedBuilder
        get() = getEmbed(Clock.System.now())

    val embedWithoutTimestamp: EmbedBuilder
        get() {
            val embed = EmbedBuilder()
            embed.footer { footer }
            return embed
        }

    fun getEmbed(time: Instant?): EmbedBuilder {
        val embed = EmbedBuilder()
        embed.timestamp = time
        embed.footer = footer
        return embed
    }

    suspend fun getBotOwner(kord: Kord): User? {
        val ownerId = kord.getApplicationInfo().team?.ownerUserId ?: kord.getApplicationInfo().ownerId

        if (ownerId == null) {
            return null
        }

        return kord.getUser(ownerId)
    }

    fun makeDoubleReadable(number: Double): String {
        val df = DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.US))
        df.setMaximumFractionDigits(340) //340 = DecimalFormat.DOUBLE_FRACTION_DIGITS

        return df.format(number)
    }

    fun makeNumberReadable(number: Long): String {
        if (number >= 1000000000000L) {
            return makeDoubleReadable(number / 1000000000000.0) + "t"
        }

        if (number >= 1000000000L) {
            return makeDoubleReadable(number / 1000000000.0) + "b"
        }

        if (number >= 1000000L) {
            return makeDoubleReadable(number / 1000000.0) + "m"
        }

        if (number >= 1000L) {
            return makeDoubleReadable(number / 1000.0) + "k"
        }

        return number.toString()
    }

    val errorEmbed: EmbedBuilder
        get() = getErrorEmbed(embed = embed)

    fun getErrorEmbed(embed: EmbedBuilder): EmbedBuilder {
        embed.title = "Error"
        embed.color = EmbedColor.NEGATIVE.color

        return embed
    }

    fun getErrorEmbed(commandExecutionException: CommandExecutionException): EmbedBuilder {
        val embed = errorEmbed
        embed.description = commandExecutionException.message
        return embed
    }

    fun getErrorEmbed(embed: EmbedBuilder, commandExecutionException: CommandExecutionException): EmbedBuilder {
        val embedBuilder = getErrorEmbed(embed)
        embedBuilder.description = commandExecutionException.message
        return embedBuilder
    }

    fun loadEmbedFromDiscordRole(discordRoleModel: DiscordRoleModel): EmbedBuilder {
        val embed = embed

        embed.field("Role", true) { "<@&" + discordRoleModel.id + ">" }
        embed.field(
            "Name schema",
            true
        ) { discordRoleModel.nameSchema ?: "none" }
        embed.field("Verified role", true) { if (discordRoleModel.isVerifiedRole) "yes" else "no" }

        return embed
    }

    @JvmOverloads
    fun loadEmbedFromCarry(
        carry: CarryModel,
        embedBuilder: EmbedBuilder = getEmbed(Instant.fromEpochSeconds(carry.time().epochSecond))
    ): EmbedBuilder {
        embedBuilder.color = EmbedColor.INFORMATION.color

        embedBuilder.field("Number of carries", true) { carry.amount.toString() }
        embedBuilder.field(
            "Type of carry",
            true
        ) { carry.carryDifficulty().carryTier.displayName + " - " + carry.carryDifficulty().displayName }
        embedBuilder.field("Player", true) { "<@" + carry.player().id + ">" }
        embedBuilder.field("Carrier", true) { "<@" + carry.carrier().id + ">" }

        if (carry.approver() != null) {
            embedBuilder.field("Approved by", true) { "<@" + carry.approver() + ">" }
        }

        if (carry.attachmentLink() != null) {
            embedBuilder.field("Transcript-Link", true) { "[Click to open]" + "(" + carry.attachmentLink() + ")" }
        }

        return embedBuilder
    }

    fun loadEmbedFromCarryQueue(carryQueue: CarryQueueModel, embedBuilder: EmbedBuilder): EmbedBuilder {
        embedBuilder.color = EmbedColor.INFORMATION.color

        embedBuilder.field("Number of carries", true) { carryQueue.amount.toString() }
        embedBuilder.field(
            "Type of carry",
            true
        ) { carryQueue.carryTier.displayName + " - " + carryQueue.carryDifficulty.displayName }
        embedBuilder.field("Player", true) { "<@" + carryQueue.player.id + ">" }
        embedBuilder.field("Carrier", true) { "<@" + carryQueue.carrier.id + ">" }

        if (carryQueue.attachmentLink != null) {
            embedBuilder.field("Transcript-Link", true) { "[Click to open]" + "(" + carryQueue.attachmentLink + ")" }
        }

        return embedBuilder
    }

    fun loadEmbedFromCarryQueue(carryQueue: CarryQueueModel): EmbedBuilder {
        return loadEmbedFromCarryQueue(carryQueue, getEmbed(Instant.fromEpochSeconds(carryQueue.time.epochSecond)))
    }

    fun formatWarn(warningModel: WarningModel): EmbedBuilder {
        val embed = getEmbed(warningModel.time.toKotlinInstant())
        embed.color = EmbedColor.INFORMATION.color
        embed.title = "Warning #${warningModel.id}"

        embed.field("User") { "<@${warningModel.user.id}>" }
        embed.field("Striker") { warningModel.striker?.let { "<@${it.id}>" } ?: "CONSOLE" }
        embed.field("Severity") { warningModel.warningType.name }
        embed.field("Reason") { warningModel.reason ?: "No reason provided." }
        embed.field("Active") { warningModel.isActive.toString() }

        return embed
    }

    fun formatWarn(warningModel: DetailedWarningModel): EmbedBuilder {
        val embed = getEmbed(warningModel.time.toKotlinInstant())
        embed.color = EmbedColor.INFORMATION.color
        embed.title = "Warning #${warningModel.id}"

        embed.field("User") { "<@${warningModel.user.id}>" }
        embed.field("Striker") { warningModel.striker?.let { "<@${it.id}>" } ?: "CONSOLE" }
        embed.field("Severity") { warningModel.warningType.name }
        embed.field("Reason") { warningModel.reason ?: "No reason provided." }
        embed.field("Active") { warningModel.isActive.toString() }

        if(warningModel.evidences.isNotEmpty()) {
            val evidences = warningModel.evidences.stream().map {
                "- ${it.evidence}"
            }.toList().joinToString(separator = System.lineSeparator())

            embed.field("Evidences") { evidences }
        }

        return embed
    }

    fun formatWarnDm(warningModel: WarningModel): EmbedBuilder {
        val embedBuilder = getEmbed(warningModel.time.toKotlinInstant())
        embedBuilder.color = EmbedColor.INFORMATION.color
        embedBuilder.title = "You were warned on server `${
            runBlocking {
                DiscordConnection.bot!!.kordRef.getGuildOrNull(Snowflake(warningModel.server.id))?.name ?: "unknown"
            }
        }`"

        embedBuilder.field("You") { "<@${warningModel.user.id}>" }
        embedBuilder.field("Severity") { warningModel.warningType.name }
        embedBuilder.field("Reason") {
            warningModel.reason ?: "No reason provided."
        }

        return embedBuilder
    }

    fun formatWarnLog(warningModel: WarningModel): EmbedBuilder {
        val embed = getEmbed(warningModel.time.toKotlinInstant())
        embed.color = EmbedColor.INFORMATION.color
        embed.title = "Warning #${warningModel.id}"

        embed.field("User") { "<@${warningModel.user.id}>" }
        embed.field("Striker") {
            if(warningModel.striker != null) {
                "<@${warningModel.striker.id}>"
            } else {
                "CONSOLE"
            }
        }
        embed.field("Severity") { warningModel.warningType.name }
        embed.field("Reason") { warningModel.reason ?: "No reason provided." }

        return embed
    }

    val noCarryTypeFoundEmbed: EmbedBuilder
        get() {
            val embed = embed
            embed.title = "No score was found!"
            embed.description =
                "Please make sure that a carry type is setup on this server.\n" +
                        "For more information about how to do this, contact `@taubsie` (<@356134481452597250>)!"
            embed.color = EmbedColor.NEGATIVE.color

            return embed
        }

    suspend fun getScoreCountMessage(
        userToCheck: User,
        user: User,
        server: GuildBehavior?,
        scoreCount: List<ScoreModel>
    ): EmbedBuilder {
        if (scoreCount.isEmpty()) {
            return noCarryTypeFoundEmbed
        }

        val embed = embed

        embed.title = if ((userToCheck.id != user.id && server != null)
        ) server.getMember(userToCheck.id).effectiveName + "'s score:"
        else "Your score:"
        embed.color = EmbedColor.DEFAULT.color

        val scoreDescriptions: EnumMap<ScoreType, MutableList<String>> = EnumMap<ScoreType, MutableList<String>>(
            ScoreType::class.java
        )

        scoreCount.forEach { scoreModel: ScoreModel ->
            if (scoreModel.scoreType == ScoreType.EVENT && !scoreModel.carryType?.isEventActive!!) {
                return@forEach
            }
            val description = scoreModel.carryType?.displayName + ": " + scoreModel.scoreAmount
            if (scoreDescriptions.containsKey(scoreModel.scoreType)) {
                scoreDescriptions[scoreModel.scoreType]!!.add(description)
            } else {
                scoreDescriptions[scoreModel.scoreType] = mutableListOf(description)
            }
        }

        scoreDescriptions
            .forEach { (carryType: ScoreType, strings: List<String>?) ->
                embed.field(carryType.displayName, true) { java.lang.String.join(System.lineSeparator(), strings) }
            }

        return embed
    }

    //TODO maybe make it possible to update the embed in 2 intervals, since the mojang+safety+jerry api takes long,
    // as well as the skycrypt api takes long too
    //probably first load skycrypt, then the rest?
    @Throws(FailedToLoadEmbedException::class)
    fun getPlayerDataEmbed(ign: String, discordId: Long?): EmbedBuilder {
        val skycryptData: Map<String, String?> = HypixelConnection.getSkyCryptDataSync(ign)

        val description = skycryptData.getOrDefault(
            "description", "Couldn't load SkyCrypt data. Please try again " +
                    "later."
        )

        val embed = embed
        embed.color = EmbedColor.INFORMATION.color
        embed.description = description
        embed.title = skycryptData.getOrDefault("title", ign)
        embed.url = ConfigProperty.SKYCRYPT_API_URL.toString() + "stats/" + ign

        if (skycryptData.containsKey("icon")) {
            embed.thumbnail {
                url = skycryptData["icon"]!!
            }
        }

        val uuid: UUID = MojangConnection.getInstance().getUUIDByName(ign)

        val flagResponses: List<FlagResponse> = FlaggingConnection.getInstance().isFlagged(uuid, discordId)
            .stream()
            .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
            .filter { flagResponse: FlagResponse ->
                ((flagResponse.uuid != null && flagResponse.uuid.flagged)
                        || (flagResponse.discord != null && flagResponse.discord.flagged))
            }
            .toList()

        if (flagResponses.isNotEmpty()) {
            embed.field("Flagged", false) {
                "**This user is flagged, which means it might not safe to interact with them.**\n${
                    formatFlagDetails(flagResponses)
                }"
            }

            embed.color = EmbedColor.NEGATIVE.color
        } else {
            embed.color = EmbedColor.POSITIVE.color
        }

        if (!skycryptData.containsKey("description") || !skycryptData.containsKey("title")) {
            throw FailedToLoadEmbedException(embed)
        }

        return embed
    }

    fun formatFlagDetails(flagged: List<FlagResponse>): String {
        val result: MutableList<String> = ArrayList()

        for (flagResponse in flagged) {
            if (flagResponse.discord != null && flagResponse.discord.flagged) {
                result.add("- " + flagResponse.name + " (by discord): " + flagResponse.discord.format())
            }

            if (flagResponse.uuid != null && flagResponse.uuid.flagged) {
                result.add("- " + flagResponse.name + " (by UUID): " + flagResponse.uuid.format())
            }
        }

        return java.lang.String.join("\n", result)
    }

    fun getCarryTypeEmbed(carryType: CarryTypeModel): EmbedBuilder {
        val embed = embed
        embed.color = EmbedColor.DEFAULT.color
        embed.field("Identifier", true) { carryType.identifier }
        embed.field("Display Name", true) { carryType.displayName }

        carryType.logChannel.ifPresent { logChannel ->
            embed.field("Log Channel", true) { "<#$logChannel>" }
        }

        carryType.leaderboardChannel.ifPresent { leaderboardChannel ->
            embed.field("Leaderboard Channel", true) { "<#$leaderboardChannel>" }
        }

        embed.field("Event active", true) { if (carryType.isEventActive) "yes" else "no" }

        return embed
    }

    fun getCarryTierEmbed(carryTier: CarryTierModel): EmbedBuilder {
        val embed = embed

        embed.color = EmbedColor.DEFAULT.color
        embed.field("Identifier", true) { carryTier.identifier }
        embed.field("Display Name", true) { carryTier.displayName }
        embed.field("Descriptive Name", true) { carryTier.descriptiveName }
        embed.field(
            "Carry Type",
            true
        ) { carryTier.carryType.displayName + " (" + carryTier.carryType.identifier + ")" }

        carryTier.category.ifPresent { category: Long -> embed.field("Category", true) { "<#$category>" } }
        carryTier.priceChannel.ifPresent { priceChannel: Long ->
            embed.field(
                "Price Channel",
                true
            ) { "<#$priceChannel>" }
        }
        carryTier.thumbnailUrl.ifPresent { thumbnailUrl: String ->
            embed.field("Thumbnail URL", true) { thumbnailUrl }
        }
        carryTier.actualPriceTitle.ifPresent { s: String -> embed.field("Price Title", true) { s } }
        carryTier.priceDescription.ifPresent { s: String -> embed.field("Price Description", true) { s } }

        return embed
    }

    fun getCarryDifficultyEmbed(carryDifficulty: CarryDifficultyModel): EmbedBuilder {
        val embed = embed
        embed.color = EmbedColor.DEFAULT.color

        embed.field("Identifier", true) { carryDifficulty.identifier }
        embed.field("Display Name", true) { carryDifficulty.displayName }
        embed.field(
            "Carry Type",
            true
        ) { carryDifficulty.carryType.displayName + " (" + carryDifficulty.carryType.identifier + ")" }
        embed.field(
            "Carry Tier",
            true
        ) { carryDifficulty.carryTier.displayName + " (" + carryDifficulty.carryTier.identifier + ")" }
        embed.field(
            "Price",
            true
        ) { carryDifficulty.price.toString() + " (" + makeNumberReadable(carryDifficulty.price.toLong()) + ")" }
        embed.field("Score", true) { carryDifficulty.score.toString() }

        carryDifficulty.bulkAmount
            .ifPresent { integer: Int -> embed.field("Bulk Amount", true) { integer.toString() } }
        carryDifficulty.bulkPrice
            .ifPresent { integer: Int -> embed.field("Bulk Price", true) { integer.toString() } }
        carryDifficulty.actualThumbnailUrl.ifPresent { s: String -> embed.field("Thumbnail URL", true) { s } }
        carryDifficulty.actualPriceName.ifPresent { s: String -> embed.field("Price Title", true) { s } }

        return embed
    }

    @Throws(WriterException::class)
    fun generateQRCodeImage(barcodeText: String?): BufferedImage {
        val barcodeWriter = QRCodeWriter()

        val hints: MutableMap<EncodeHintType, Any> = EnumMap(
            EncodeHintType::class.java
        )
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1

        val bitMatrix: BitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, 200, 200, hints)

        return MatrixToImageWriter.toBufferedImage(bitMatrix)
    }

    @Throws(ChecksumException::class, NotFoundException::class, FormatException::class)
    fun readQRCodeImage(bufferedImage: BufferedImage?): String {
        val binaryBitmap = BinaryBitmap(
            HybridBinarizer(
                BufferedImageLuminanceSource(bufferedImage)
            )
        )

        val qrCodeReader = QRCodeReader()

        return qrCodeReader.decode(binaryBitmap).text
    }

    fun readImageData(bufferedImage: BufferedImage?): ByteArray {
        val outputStream = ByteArrayOutputStream()
        try {
            ImageIO.write(bufferedImage, "png", outputStream)
        } catch (ioException: IOException) {
            logger.error(null, ioException)
            return ByteArray(0)
        }
        return outputStream.toByteArray()
    }

    fun calculatePrice(carryDifficulty: CarryDifficultyModel, amount: Long): Long {
        return calculatePricePerCarry(carryDifficulty, amount) * amount
    }

    fun calculatePricePerCarry(carryDifficulty: CarryDifficultyModel, amount: Long): Long {
        val bulkPrice = carryDifficulty.bulkPrice
        val bulkAmount = carryDifficulty.bulkAmount

        if (bulkPrice.isPresent && bulkAmount.isPresent && bulkAmount.get() <= amount) {
            return bulkPrice.get().toLong()
        }

        return carryDifficulty.price.toLong()
    }
}