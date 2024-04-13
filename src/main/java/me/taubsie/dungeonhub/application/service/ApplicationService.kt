package me.taubsie.dungeonhub.application.service

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Guild
import dev.kord.core.entity.User
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.PrivilegedIntent
import io.ktor.util.debug.*
import kotlinx.coroutines.runBlocking
import me.taubsie.dungeonhub.application.classes.FlagResponse
import me.taubsie.dungeonhub.application.command.Command
import me.taubsie.dungeonhub.application.config.ConfigProperty
import me.taubsie.dungeonhub.application.connection.DiscordConnection
import me.taubsie.dungeonhub.application.connection.FlaggingConnection
import me.taubsie.dungeonhub.application.connection.HypixelConnection
import me.taubsie.dungeonhub.application.connection.MojangConnection
import me.taubsie.dungeonhub.application.enums.EmbedColor
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadEmbedException
import me.taubsie.dungeonhub.application.loader.ClassLoaderService
import me.taubsie.dungeonhub.common.DungeonHubService
import me.taubsie.dungeonhub.common.StrikeData
import me.taubsie.dungeonhub.common.enums.ScoreType
import me.taubsie.dungeonhub.common.model.carry.CarryModel
import me.taubsie.dungeonhub.common.model.carry_difficulty.CarryDifficultyModel
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel
import me.taubsie.dungeonhub.common.model.carry_tier.CarryTierModel
import me.taubsie.dungeonhub.common.model.carry_type.CarryTypeModel
import me.taubsie.dungeonhub.common.model.discord_role.DiscordRoleModel
import me.taubsie.dungeonhub.common.model.score.ScoreModel
import org.javacord.api.entity.message.MessageFlag
import org.javacord.api.entity.message.component.ActionRowBuilder
import org.javacord.api.entity.message.component.HighLevelComponent
import org.javacord.api.entity.message.component.TextInputBuilder
import org.javacord.api.entity.message.component.TextInputStyle
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.event.interaction.SlashCommandCreateEvent
import org.javacord.api.interaction.InteractionBase
import org.javacord.api.interaction.SlashCommandOption
import org.javacord.api.interaction.SlashCommandOptionBuilder
import org.javacord.api.interaction.SlashCommandOptionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.*
import javax.imageio.ImageIO

class ApplicationService {
    private val logger: Logger = LoggerFactory.getLogger(DiscordConnection::class.java)

    private val serverLink: String
        get() = "discord.dungeon-hub.net"

    val footer: String
        /**
         * Returns the default footer used for most embeds.
         * Warning is suppressed, since the escape needs to be made due to some systems having an issue showing the unicode representation through discord.
         *
         * @return the default footer used for most embeds.
         */
        get() = "$serverLink • made by @taubsie"

    val unstableFooter: String
        /**
         * Returns the footer used for embeds of unstable or new features.
         * Warning is suppressed, since the escape needs to be made due to some systems having an issue showing the unicode representation through discord.
         *
         * @return the footer used for embeds of unstable or new features.
         */
        get() = "$serverLink • THIS FEATURE IS UNSTABLE • please report bugs to @taubsie"

    val priceFooter: String
        /**
         * Returns the footer used for price message embeds.
         * Warning is suppressed, since the escape needs to be made due to some systems having an issue showing the unicode representation through discord.
         *
         * @return the footer used for price message embeds.
         */
        get() = "$serverLink • also see /calc-price • made by @taubsie"

    val embed: EmbedBuilder
        get() = getEmbed(Instant.now())

    val embedWithoutTimestamp: EmbedBuilder
        get() = EmbedBuilder()
            .setFooter(footer)

    fun getEmbed(time: Instant?): EmbedBuilder {
        return EmbedBuilder()
            .setTimestamp(time)
            .setFooter(footer)
    }

    suspend fun getBotOwner(kord: Kord): User? {
        val ownerId = kord.getApplicationInfo().ownerId ?: kord.getApplicationInfo().team?.ownerUserId

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

    suspend fun getKord(): Kord {
        val kord = Kord(ConfigProperty.DISCORD_BOT_TOKEN.value)

        kord.on<MessageCreateEvent> { // runs every time a message is created that our bot can read

            // ignore other bots, even ourselves. We only serve humans here!
            if (message.author?.isBot != false) return@on

            // check if our command is being invoked
            if (message.content != "!ping") return@on

            // all clear, give them the pong!
            message.channel.createMessage("pong!")
        }

        return kord
    }

    suspend fun loginKord(kord: Kord) {
        kord.login {
            // we need to specify this to receive the content of messages
            @OptIn(PrivilegedIntent::class)
            intents += dev.kord.gateway.Intent.MessageContent
        }
    }

    val errorEmbed: EmbedBuilder
        get() = getErrorEmbed(embed)

    fun getErrorEmbed(embed: EmbedBuilder): EmbedBuilder {
        return embed.setTitle("Error").setColor(EmbedColor.NEGATIVE.color)
    }

    fun getErrorEmbed(commandExecutionException: CommandExecutionException): EmbedBuilder {
        return errorEmbed.setDescription(commandExecutionException.message)
    }

    fun getErrorEmbed(embed: EmbedBuilder, commandExecutionException: CommandExecutionException): EmbedBuilder {
        return getErrorEmbed(embed).setDescription(commandExecutionException.message)
    }

    fun respondWithError(
        interactionBase: InteractionBase,
        commandExecutionException: CommandExecutionException
    ) {
        interactionBase.createImmediateResponder()
            .setFlags(MessageFlag.EPHEMERAL)
            .addEmbed(getErrorEmbed(commandExecutionException))
            .respond()
    }

    fun getCommand(slashCommandCreateEvent: SlashCommandCreateEvent): Optional<Command> {
        return ClassLoaderService.getInstance().getCommand(
            slashCommandCreateEvent.slashCommandInteraction.commandName,
            slashCommandCreateEvent.slashCommandInteraction.server.orElse(null)
        )
    }

    fun loadEmbedFromDiscordRole(discordRoleModel: DiscordRoleModel): EmbedBuilder {
        val embed = embed

        embed.addInlineField("Role", "<@&" + discordRoleModel.id + ">")
        embed.addInlineField(
            "Name schema",
            if (discordRoleModel.nameSchema != null) discordRoleModel.nameSchema else "none"
        )
        embed.addInlineField("Verified role", if (discordRoleModel.isVerifiedRole) "yes" else "no")

        return embed
    }

    @JvmOverloads
    fun loadEmbedFromCarry(carry: CarryModel, embedBuilder: EmbedBuilder = getEmbed(carry.time())): EmbedBuilder {
        embedBuilder.setColor(EmbedColor.INFORMATION.color)
            .addInlineField(
                "Number of carries",
                carry.amount().toString()
            )
            .addInlineField(
                "Type of carry",
                carry.carryDifficulty().carryTier.displayName + " - " + carry.carryDifficulty().displayName
            )
            .addInlineField("Player", "<@" + carry.player().id + ">")
            .addInlineField("Carrier", "<@" + carry.carrier().id + ">")

        if (carry.approver() != null) {
            embedBuilder.addInlineField("Approved by", "<@" + carry.approver() + ">")
        }

        if (carry.attachmentLink() != null) {
            embedBuilder.addInlineField(
                "Transcript-Link", "[Click to open]" +
                        "(" + carry.attachmentLink() + ")"
            )
        }

        return embedBuilder
    }

    fun loadEmbedFromCarryQueue(carryQueue: CarryQueueModel, embedBuilder: EmbedBuilder): EmbedBuilder {
        embedBuilder.setColor(EmbedColor.INFORMATION.color)
            .addInlineField(
                "Number of carries",
                carryQueue.amount.toString()
            )
            .addInlineField(
                "Type of carry",
                carryQueue.carryTier.displayName + " - " + carryQueue.carryDifficulty.displayName
            )
            .addInlineField("Player", "<@" + carryQueue.player.id + ">")
            .addInlineField("Carrier", "<@" + carryQueue.carrier.id + ">")

        if (carryQueue.attachmentLink != null) {
            embedBuilder.addInlineField(
                "Transcript-Link", "[Click to open]" +
                        "(" + carryQueue.attachmentLink + ")"
            )
        }

        return embedBuilder
    }

    fun loadEmbedFromCarryQueue(carryQueue: CarryQueueModel): EmbedBuilder {
        return loadEmbedFromCarryQueue(carryQueue, getEmbed(carryQueue.time))
    }

    suspend fun formatStrikes(strikeData: List<StrikeData>, user: User, page: Int): EmbedBuilder {
        val embedBuilder = embed
            .setColor(EmbedColor.INFORMATION.color)
            .setTitle("Strikes of user " + user.tag)

        if (strikeData.isEmpty()) {
            embedBuilder.setDescription("User has no strikes!")
            return embedBuilder
        }

        strikeData.stream()
            .skip(DungeonHubService.getInstance().getOffsetFromPageNumber(page).toLong())
            .limit(10)
            .forEach { strike: StrikeData ->
                val striker: String = Optional.ofNullable<Long>(strike.striker)
                    .map<User> { strikerId: Long ->
                        runBlocking {
                            DiscordConnection.getInstance().bot.getUser(
                                Snowflake(strikerId)
                            )
                        }
                    }
                    .map { obj: User -> obj.tag }
                    .orElse("CONSOLE")
                val reason = Optional.ofNullable(strike.reason)
                    .map { s: String -> " because of \"$s\"" }
                    .orElse("")
                embedBuilder.addField(
                    "Strike #" + strike.id,
                    "By " + striker + " at <t:" + strike.strikeTime.toEpochMilli() + ">" + reason
                )
            }

        return embedBuilder
    }

    suspend fun formatStrikeLog(strikeData: StrikeData): EmbedBuilder {
        val title =
            "Strike " + (if (strikeData.id != null) "#$strikeData.id" else "for " + DiscordConnection.getInstance().bot.getUser(
                Snowflake(strikeData.user)
            )?.tag)

        val embedBuilder = getEmbed(strikeData.strikeTime)
            .setColor(EmbedColor.INFORMATION.color)
            .setTitle(title)

        embedBuilder.addField("User", "<@" + strikeData.user + ">")
        embedBuilder.addField("Striker", if (strikeData.striker != null) "<@" + strikeData.striker + ">" else "CONSOLE")
        embedBuilder.addField(
            "Reason", if (strikeData.reason != null) strikeData.reason else "No reason provided" +
                    "."
        )

        return embedBuilder
    }

    suspend fun formatStrikeDM(strikeData: StrikeData): EmbedBuilder {
        val embedBuilder = getEmbed(strikeData.strikeTime)
            .setColor(EmbedColor.INFORMATION.color)
            .setTitle(
                "You were striked on server `"
                        + (DiscordConnection.getInstance().bot.getGuildOrNull(Snowflake(strikeData.server))?.name
                    ?: "unknown")
                        + "`"
            )

        embedBuilder.addField("You", "<@" + strikeData.user + ">")
        embedBuilder.addField(
            "Reason", if (strikeData.reason != null) strikeData.reason else "No reason provided" +
                    "."
        )

        return embedBuilder
    }

    suspend fun formatStrike(strikeData: StrikeData): EmbedBuilder {
        val title =
            "Strike " + (if (strikeData.id != null) "#$strikeData.id" else "for " + DiscordConnection.getInstance().bot.getUser(
                Snowflake(strikeData.user)
            )?.tag)

        val embedBuilder = getEmbed(strikeData.strikeTime)
            .setColor(EmbedColor.INFORMATION.color)
            .setTitle(title)

        embedBuilder.addField("User", "<@" + strikeData.user + ">")
        embedBuilder.addField("Striker", if (strikeData.striker != null) "<@" + strikeData.striker + ">" else "CONSOLE")
        embedBuilder.addField(
            "Reason", if (strikeData.reason != null) strikeData.reason else "No reason provided" +
                    "."
        )

        return embedBuilder
    }

    val noCarryTypeFoundEmbed: EmbedBuilder
        get() = embed
            .setTitle("No score was found!")
            .setDescription(
                """
    Please make sure that a carry type is setup on this server.
    For more information about how to do this, contact `@taubsie` (<@356134481452597250>)!
    """.trimIndent()
            )
            .setColor(EmbedColor.NEGATIVE.color)

    suspend fun getScoreCountMessage(
        userToCheck: User,
        user: User,
        server: Guild?,
        scoreCount: List<ScoreModel>
    ): EmbedBuilder {
        if (scoreCount.isEmpty()) {
            return noCarryTypeFoundEmbed
        }

        val embed = embed
            .setTitle(
                if ((userToCheck.id != user.id && server != null)
                ) server.getMember(userToCheck.id).effectiveName + "'s score:"
                else "Your score:"
            )
            .setColor(EmbedColor.DEFAULT.color)

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
                scoreDescriptions[scoreModel.scoreType] = ArrayList(java.util.List.of(description))
            }
        }

        scoreDescriptions
            .forEach { (carryType: ScoreType, strings: List<String>?) ->
                embed.addInlineField(
                    carryType.displayName,
                    java.lang.String.join(System.lineSeparator(), strings)
                )
            }

        return embed
    }

    val ingamenameOption: SlashCommandOption
        get() = SlashCommandOptionBuilder()
            .setName("ign")
            .setDescription("The users ingame-name")
            .setType(SlashCommandOptionType.STRING)
            .setMinLength(2)
            .setRequired(true)
            .build()

    //TODO maybe make it possible to update the embed in 2 intervals, since the mojang+safety+jerry api takes long,
    // as well as the skycrypt api takes long too
    //probably first load skycrypt, then the rest?
    @Throws(FailedToLoadEmbedException::class)
    fun getPlayerDataEmbed(ign: String, discordId: Long?): EmbedBuilder {
        val skycryptData: Map<String, String?> = HypixelConnection.getInstance().getSkyCryptData(ign)

        val description = skycryptData.getOrDefault(
            "description", "Couldn't load SkyCrypt data. Please try again " +
                    "later."
        )

        val embed = instance!!.embed
            .setColor(EmbedColor.INFORMATION.color)
            .setDescription(description)
            .setTitle(skycryptData.getOrDefault("title", ign))
            .setUrl(ConfigProperty.SKYCRYPT_API_URL.toString() + "stats/" + ign)
            .setThumbnail(skycryptData.getOrDefault("icon", null))

        val uuid: UUID = MojangConnection.getInstance().getUUIDByName(ign)

        val flagResponses: List<FlagResponse> = FlaggingConnection.getInstance().isFlagged(uuid, discordId)
            .stream()
            .filter { flagResponse: FlagResponse -> flagResponse.uuid != null || flagResponse.discord != null }
            .filter { flagResponse: FlagResponse ->
                ((flagResponse.uuid != null && flagResponse.uuid!!.flagged)
                        || (flagResponse.discord != null && flagResponse.discord!!.flagged))
            }
            .toList()

        if (flagResponses.isNotEmpty()) {
            embed.addField(
                "Flagged", """
     **This user is flagged, which means it might not safe to interact with them.**
     ${formatFlagDetails(flagResponses)}
     """.trimIndent()
            )
                .setColor(EmbedColor.NEGATIVE.color)
        } else {
            embed.setColor(EmbedColor.POSITIVE.color)
        }

        if (!skycryptData.containsKey("description") || !skycryptData.containsKey("title")) {
            throw FailedToLoadEmbedException(embed)
        }

        return embed
    }

    fun formatFlagDetails(flagged: List<FlagResponse>): String {
        val result: MutableList<String> = ArrayList()

        for (flagResponse in flagged) {
            if (flagResponse.discord != null && flagResponse.discord!!.flagged) {
                result.add("- " + flagResponse.name + " (by discord): " + flagResponse.discord!!.format())
            }

            if (flagResponse.uuid != null && flagResponse.uuid!!.flagged) {
                result.add("- " + flagResponse.name + " (by UUID): " + flagResponse.uuid!!.format())
            }
        }

        return java.lang.String.join("\n", result)
    }

    fun getCarryTypeEmbed(carryType: CarryTypeModel): EmbedBuilder {
        val embed = embed
            .setColor(EmbedColor.DEFAULT.color)
            .addInlineField("Identifier", carryType.identifier)
            .addInlineField("Display Name", carryType.displayName)

        carryType.logChannel.ifPresent { logChannel: Long -> embed.addInlineField("Log Channel", "<#$logChannel>") }
        carryType.leaderboardChannel.ifPresent { leaderboardChannel: Long ->
            embed.addInlineField(
                "Leaderboard Channel",
                "<#$leaderboardChannel>"
            )
        }
        embed.addInlineField("Event active", if (carryType.isEventActive) "yes" else "no")

        return embed
    }

    fun getCarryTierEmbed(carryTier: CarryTierModel): EmbedBuilder {
        val embed = embed
            .setColor(EmbedColor.DEFAULT.color)
            .addInlineField("Identifier", carryTier.identifier)
            .addInlineField("Display Name", carryTier.displayName)
            .addInlineField("Descriptive Name", carryTier.descriptiveName)
            .addInlineField(
                "Carry Type",
                carryTier.carryType.displayName + " (" + carryTier.carryType.identifier + ")"
            )

        carryTier.category.ifPresent { category: Long -> embed.addInlineField("Category", "<#$category>") }
        carryTier.priceChannel.ifPresent { priceChannel: Long ->
            embed.addInlineField(
                "Price Channel",
                "<#$priceChannel>"
            )
        }
        carryTier.thumbnailUrl.ifPresent { thumbnailUrl: String? ->
            embed.addInlineField(
                "Thumbnail URL",
                thumbnailUrl
            )
        }
        carryTier.actualPriceTitle.ifPresent { s: String? -> embed.addInlineField("Price Title", s) }
        carryTier.priceDescription.ifPresent { s: String? -> embed.addInlineField("Price Description", s) }

        return embed
    }

    fun getCarryDifficultyEmbed(carryDifficulty: CarryDifficultyModel): EmbedBuilder {
        val embed = embed
            .setColor(EmbedColor.DEFAULT.color)
            .addInlineField("Identifier", carryDifficulty.identifier)
            .addInlineField("Display Name", carryDifficulty.displayName)
            .addInlineField(
                "Carry Type",
                carryDifficulty.carryType.displayName + " (" + carryDifficulty.carryType.identifier + ")"
            )
            .addInlineField(
                "Carry Tier",
                carryDifficulty.carryTier.displayName + " (" + carryDifficulty.carryTier.identifier + ")"
            )
            .addInlineField(
                "Price",
                carryDifficulty.price.toString() + " (" + makeNumberReadable(carryDifficulty.price.toLong()) + ")"
            )
            .addInlineField("Score", carryDifficulty.score.toString())

        carryDifficulty.bulkAmount
            .ifPresent { integer: Int -> embed.addInlineField("Bulk Amount", integer.toString()) }
        carryDifficulty.bulkPrice
            .ifPresent { integer: Int -> embed.addInlineField("Bulk Price", integer.toString()) }
        carryDifficulty.actualThumbnailUrl.ifPresent { s: String? -> embed.addInlineField("Thumbnail URL", s) }
        carryDifficulty.actualPriceName.ifPresent { s: String? -> embed.addInlineField("Price Title", s) }

        return embed
    }

    @Throws(WriterException::class)
    fun generateQRCodeImage(barcodeText: String?): BufferedImage {
        val barcodeWriter: QRCodeWriter = QRCodeWriter()

        val hints: MutableMap<EncodeHintType, Any> = EnumMap<EncodeHintType, Any>(
            EncodeHintType::class.java
        )
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8")
        hints.put(EncodeHintType.MARGIN, 1)

        val bitMatrix: BitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, 200, 200, hints)

        return MatrixToImageWriter.toBufferedImage(bitMatrix)
    }

    @Throws(ChecksumException::class, NotFoundException::class, FormatException::class)
    fun readQRCodeImage(bufferedImage: BufferedImage?): String {
        val binaryBitmap: BinaryBitmap = BinaryBitmap(
            HybridBinarizer(
                BufferedImageLuminanceSource(bufferedImage)
            )
        )

        val qrCodeReader: QRCodeReader = QRCodeReader()

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

    val linkModalComponent: HighLevelComponent
        get() = ActionRowBuilder().addComponents(
            TextInputBuilder(TextInputStyle.SHORT, "ign", "Ingame-Name")
                .setMaximumLength(MAX_MINECRAFT_USERNAME_LENGTH)
                .setMinimumLength(3)
                .setPlaceholder("For example: Taubsie")
                .setRequired(true)
                .build()
        ).build()

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

    companion object {
        private const val MAX_MINECRAFT_USERNAME_LENGTH = 16

        @JvmStatic
        var instance: ApplicationService? = null
            get() {
                if (field == null) {
                    field = ApplicationService()
                }

                return field
            }
            private set
    }
}