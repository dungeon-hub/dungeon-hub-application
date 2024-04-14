package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.classes.ServerProperty;
import me.taubsie.dungeonhub.application.config.ConfigProperty;
import me.taubsie.dungeonhub.application.connection.MojangConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ContentConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.DiscordUserConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.QueueConnection;
import me.taubsie.dungeonhub.application.connection.dungeon_hub.ScoreConnection;
import me.taubsie.dungeonhub.application.enums.EmbedColor;
import me.taubsie.dungeonhub.application.enums.IdList;
import me.taubsie.dungeonhub.application.exceptions.FailedToLoadEmbedException;
import me.taubsie.dungeonhub.application.exceptions.PlayerNotFoundException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import me.taubsie.dungeonhub.application.service.LeaderboardService;
import me.taubsie.dungeonhub.common.DungeonHubService;
import me.taubsie.dungeonhub.common.enums.QueueStep;
import me.taubsie.dungeonhub.common.enums.ScoreType;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueModel;
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueUpdateModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.score.LoggedCarryModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageUpdater;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.CertainMessageEvent;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageListener implements MessageCreateListener, MessageEditListener {
    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    private static final long APPROVE_AMOUNT_THRESHOLD = 8;
    private static final long APPROVE_SCORE_THRESHOLD = 30;

    private static final Pattern CHANNEL_FROM_TRANSCRIPT = Pattern.compile("^\\s*Channel: [^(]*\\((?<channel>\\d*)\\)");

    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent) {
        addReactionToPets(messageCreateEvent);

        logTicket(messageCreateEvent);

        loadSkycryptFromTicket(messageCreateEvent);
    }

    @Override
    public void onMessageEdit(MessageEditEvent messageEditEvent) {
        logTicket(messageEditEvent);
    }

    private void addReactionToPets(MessageCreateEvent messageCreateEvent) {
        if (messageCreateEvent.isServerMessage() && messageCreateEvent.getServer().isPresent()) {
            long serverId = messageCreateEvent.getServer().get().getId();
            long channelId = messageCreateEvent.getChannel().getId();
            if ((serverId == 1023684107877761196L && channelId == 1220895875102937098L)
                    || (serverId == 693263712626278553L && channelId == 1219427157655289908L)) {
                if (messageCreateEvent.getMessageAttachments().isEmpty()) {
                    if (messageCreateEvent.getMessageAuthor().isRegularUser() && messageCreateEvent.getMessage().getEmbeds().stream().flatMap(embed -> embed.getThumbnail().stream()).findFirst().isEmpty()) {
                        return;
                    }
                }

                String emoji = getRandomEmoji();

                try {
                    messageCreateEvent.addReactionToMessage(emoji).join();
                }
                catch (CompletionException ignored) {
                    // ignored, just don't add a reaction then
                }
            }
        }
    }

    private String[] getEmojiPool() {
        return new String[]{
                "Woah:1220111116651204608",
                "woah:1220111081150615572",
                "girlwow:1220111157742800956",
                "catelove:1204407157848678430",
                "ZTcool:1204406493353353256",
                "pepega:697756021048868894",
                "smikecate:1204406375791333426",
                "poggorfish:694270485613117480"
        };
    }

    private String getRandomEmoji() {
        double bound = 101.0;

        String[] emojiPool = getEmojiPool();

        int random = new Random().nextInt((int) bound);

        String emoji = emojiPool[(int) (random * (emojiPool.length / bound))];

        if (random == 100) {
            emoji = "swag:708383726370947132";
        }

        return emoji;
    }

    private void loadSkycryptFromTicket(MessageCreateEvent messageCreateEvent) {
        Optional<Server> server = messageCreateEvent.getServer();
        Optional<ServerTextChannel> channel = messageCreateEvent.getMessage().getServerTextChannel();

        if (!messageCreateEvent.isServerMessage()
                || channel.isEmpty()
                || server.isEmpty()
                || !(server.get().getId() == IdList.SERVER.getId()
                || server.get().getId() == IdList.SERVER.getTestId())) {
            return;
        }

        try {
            if (channel.get().getMessages(5).join().size() != 1) {
                return;
            }

            Message firstMessage =
                    channel.get().getMessagesAsStream().reduce((message, message2) -> message2).orElse(null);

            if (firstMessage == null) {
                return;
            }

            List<User> mentionedUsers = firstMessage.getMentionedUsers();

            if (mentionedUsers.size() != 1) {
                return;
            }

            User user = mentionedUsers.get(0);

            String[] lines = firstMessage.getContent().split("\n");

            if (lines.length < 2) {
                return;
            }

            Optional<String> ignOptional = DiscordUserConnection.getInstance()
                    .getLinkedById(user.getId())
                    .map(DiscordUserModel::getMinecraftId)
                    .map(uuid -> MojangConnection.getInstance().getNameByUUID(uuid))
                    .or(() -> Arrays.stream(lines)
                            .filter(s -> s.startsWith("IGN: "))
                            .findFirst())
                    .or(() -> user.getNickname(server.get()));

            if (ignOptional.isEmpty()) {
                return;
            }

            String ign = ignOptional.get()
                    .replace("IGN: ", "")
                    .replaceAll("❮(\\S*)❯", "")
                    .replace("★", "")
                    .replace("✦", "")
                    .replace("✶", "")
                    .replace("✽", "")
                    .replace("❊", "")
                    .strip();

            sendPlayerDataEmbed(ign, channel.get());
        }
        catch (CompletionException ignored) {
            //this just happens when the execution takes so long that the channel gets deleted
            //sending an error then wouldn't be needed
        }
    }

    private Button getSkyCryptButton(String ign) {
        return new ButtonBuilder().setStyle(ButtonStyle.LINK)
                .setUrl(ConfigProperty.SKYCRYPT_API_URL + "stats/" + ign)
                .setLabel("SkyCrypt")
                .build();
    }

    //TODO threads threads threads
    private void sendPlayerDataEmbed(String ign, ServerTextChannel channel) {
        EmbedBuilder playerDataEmbed;
        try {
            playerDataEmbed = ApplicationService.getInstance().getPlayerDataEmbed(ign, null);

            channel.sendMessage(playerDataEmbed,
                    new ActionRowBuilder().addComponents(
                            getSkyCryptButton(ign)
                    ).build());
        }
        catch (PlayerNotFoundException playerNotFoundException) {
            playerDataEmbed = ApplicationService.getInstance().getErrorEmbed(playerNotFoundException);

            //TODO load scammer data from discord?

            channel.sendMessage(playerDataEmbed);
        }
        catch (FailedToLoadEmbedException failedToLoadEmbedException) {
            playerDataEmbed = failedToLoadEmbedException.getEmbed();

            channel.sendMessage(playerDataEmbed,
                            new ActionRowBuilder().addComponents(
                                    getSkyCryptButton(ign),
                                    new ButtonBuilder().setStyle(ButtonStyle.SECONDARY)
                                            .setLabel("Reload")
                                            .setCustomId("reload_playerdata")
                                            .build()
                            ).build())
                    .thenAccept(message -> message.addButtonClickListener(event ->
                            event.getButtonInteractionWithCustomId("reload_playerdata")
                                    .ifPresent(buttonInteraction -> {
                                        buttonInteraction.createOriginalMessageUpdater()
                                                .removeAllComponents()
                                                .removeAllEmbeds()
                                                .addEmbed(ApplicationService.getInstance()
                                                        .getEmbed()
                                                        .setDescription("Loading..."))
                                                .update().join();

                                        //TODO maybe also update username to use?

                                        MessageUpdater updater = message.createUpdater();

                                        try {
                                            EmbedBuilder embed =
                                                    ApplicationService.getInstance().getPlayerDataEmbed(ign, null);

                                            updater.removeAllEmbeds()
                                                    .addEmbed(embed);

                                            updater.removeAllComponents()
                                                    .addComponents(new ActionRowBuilder().addComponents(
                                                            getSkyCryptButton(ign)
                                                    ).build());
                                        }
                                        catch (FailedToLoadEmbedException failedToLoadAgain) {
                                            updater.removeAllEmbeds()
                                                    .addEmbed(failedToLoadAgain.getEmbed());

                                            updater.removeAllComponents()
                                                    .addComponents(new ActionRowBuilder().addComponents(
                                                            getSkyCryptButton(ign),
                                                            new ButtonBuilder().setStyle(ButtonStyle.SECONDARY)
                                                                    .setLabel("Reload")
                                                                    .setCustomId("reload_playerdata")
                                                                    .build()
                                                    ).build());
                                        }

                                        updater.applyChanges();
                                    })
                    ));
        }
    }

    //TODO reduce complexity
    private void logTicket(CertainMessageEvent messageEvent) {
        Server server = messageEvent.getServer().orElse(null);

        if (!messageEvent.isServerMessage() || server == null) {
            return;
        }

        if (ServerProperty.TRANSCRIPTS_CHANNEL.getValue(server.getId()).map(s -> messageEvent.getChannel().getIdAsString().equals(s)).orElse(false)) {
            List<MessageAttachment> attachments = messageEvent.getMessageAttachments();

            if (attachments.size() != 1) {
                return;
            }

            MessageAttachment attachment = attachments.get(0);

            byte[] attachmentContent = attachment.asByteArray().join();

            String content = new String(attachmentContent, StandardCharsets.UTF_8);

            Optional<Long> channelId = content.lines()
                    .limit(7)
                    .map(String::strip)
                    .map(CHANNEL_FROM_TRANSCRIPT::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group("channel"))
                    .map(Long::parseLong)
                    .findFirst();

            String attachmentLink = null;

            Optional<TextChannel> approvingChannel = ServerProperty.LOG_APPROVING_CHANNEL.getValue(server.getId())
                    .flatMap(s -> messageEvent.getApi().getTextChannelById(s));

            for (CarryQueueModel queueModel : QueueConnection.getInstance()
                    .getCarryQueuesByQueueStep(QueueStep.TRANSCRIPT)
                    .orElse(new HashSet<>())
                    .stream().filter(carryQueueModel -> channelId.map(aLong -> aLong.equals(carryQueueModel.getRelationId())).orElse(false))
                    .toList()) {
                if (attachmentLink == null) {
                    Optional<String> attachmentUrl = ContentConnection.getInstance().uploadFile(attachmentContent, "{uuid}.html");

                    if (attachmentUrl.isEmpty()) {
                        logger.error("Couldn't upload content of attachment on message {}.", messageEvent.getMessage().getId());
                        return;
                    }

                    attachmentLink = ContentConnection.getInstance().getCdnUrl(attachmentUrl.get()).toString();
                }

                queueModel.setAttachmentLink(attachmentLink);

                CarryQueueUpdateModel updateModel = new CarryQueueUpdateModel()
                        .setAttachmentLink(attachmentLink);

                if ((queueModel.getAmount() >= APPROVE_AMOUNT_THRESHOLD
                        || queueModel.calculateScore() >= APPROVE_SCORE_THRESHOLD)
                        && approvingChannel.isPresent()) {
                    Message message = approvingChannel.get()
                            .sendMessage(
                                    ApplicationService.getInstance()
                                            .loadEmbedFromCarryQueue(queueModel)
                                            .setTitle("Accept carry-log?")
                                            .setColor(EmbedColor.DEFAULT.getColor()),
                                    ActionRow.of(org.javacord.api.entity.message.component.Button.success(
                                            "accept_log",
                                            "Accept"), Button.danger("deny", "Deny"))
                            ).join();

                    updateModel.setQueueStep(QueueStep.APPROVING)
                            .setRelationId(message.getId());

                    QueueConnection.getInstance().updateQueue(queueModel.getId(), updateModel);
                } else {
                    Long updatedScore = QueueConnection.getInstance()
                            .logQueue(queueModel.getId(), updateModel)
                            .stream()
                            .map(LoggedCarryModel::scoreModels)
                            .flatMap(Collection::stream)
                            .filter(scoreModel -> scoreModel.getScoreType().equals(ScoreType.DEFAULT))
                            .findFirst()
                            .map(ScoreModel::getScoreAmount)
                            .orElseGet(() -> ScoreConnection.getInstance(queueModel.getCarryType())
                                    .getScore(queueModel.getCarrier().getId())
                                    .map(ScoreModel::getScoreAmount)
                                    .orElse(0L));

                    User carrier = messageEvent.getApi().getUserById(queueModel.getCarrier().getId()).join();

                    if (carrier != null && carrier.openPrivateChannel().join() != null) {
                        carrier.openPrivateChannel().join();
                        Optional<PrivateChannel> privateChannelOptional = carrier.getPrivateChannel();
                        long gainedScore = queueModel.calculateScore();

                        try {
                            privateChannelOptional.ifPresent(privateChannel -> privateChannel
                                    .sendMessage("Your carry was logged!\n\n" +
                                                    "**Score gained:** " + gainedScore +
                                                    "\n**Your Updated Score:** " + updatedScore,
                                            ApplicationService.getInstance()
                                                    .loadEmbedFromCarryQueue(queueModel)
                                                    .setTitle("Information")
                                                    .setColor(EmbedColor.DEFAULT.getColor())).join());
                        }
                        catch (CompletionException completionException) {
                            logger.error("Error when sending log message.", completionException);
                        }
                    }

                    Optional<ServerTextChannel> logChannel = queueModel.getCarryTier()
                            .getCarryType()
                            .getLogChannel()
                            .flatMap(server::getTextChannelById);

                    if (logChannel.isPresent()) {
                        logger.debug("Carry logged: {}",
                                DungeonHubService.getInstance().getGson().toJson(queueModel));

                        logChannel.get().sendMessage(
                                ApplicationService.getInstance()
                                        .getEmbed(queueModel.getTime())
                                        .setTitle("Carry accepted.")
                                        .setColor(EmbedColor.POSITIVE.getColor())
                                        .addInlineField("Number of carries",
                                                String.valueOf(queueModel.getAmount()))
                                        .addInlineField("Type of carry",
                                                queueModel.getCarryTier().getDisplayName() + " - " + queueModel.getCarryDifficulty().getDisplayName())
                                        .addInlineField("Player",
                                                messageEvent.getApi().getUserById(queueModel.getPlayer().getId()).join().getMentionTag())
                                        .addInlineField("Carrier",
                                                messageEvent.getApi().getUserById(queueModel.getCarrier().getId()).join().getMentionTag())
                                        .addInlineField("Transcript-Link", "[Click to open]" +
                                                "(" + queueModel.getAttachmentLink() + ")"));
                    }

                    QueueConnection.getInstance().deleteQueue(queueModel.getId());
                }
            }

            LeaderboardService.getInstance().refreshLeaderboard();
        }
    }
}
