package me.taubsie.dungeonhub.application.listener;

import me.taubsie.dungeonhub.application.connection.MojangConnection;
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
import me.taubsie.dungeonhub.common.model.carry_queue.CarryQueueUpdateModel;
import me.taubsie.dungeonhub.common.model.discord_user.DiscordUserModel;
import me.taubsie.dungeonhub.common.model.score.LoggedCarryModel;
import me.taubsie.dungeonhub.common.model.score.ScoreModel;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
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

import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * @author Taubsie
 * @since 1.0.0
 */
@Listener
public class MessageListener implements MessageCreateListener, MessageEditListener {
    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    private static final long APPROVE_AMOUNT_THRESHOLD = 8;
    private static final long APPROVE_SCORE_THRESHOLD = 30;

    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent) {
        logTicket(messageCreateEvent);

        loadSkycryptFromTicket(messageCreateEvent);
    }

    @Override
    public void onMessageEdit(MessageEditEvent messageEditEvent) {
        logTicket(messageEditEvent);
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
                .setUrl("https://sky.shiiyu.moe/stats/" + ign)
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

        if (!messageEvent.isServerMessage()
                || server == null
                || !(server.getId() == IdList.SERVER.getId()
                || server.getId() == IdList.SERVER.getTestId())) {
            return;
        }

        if (messageEvent.getChannel().getId() == IdList.TRANSCRIPTS_CHANNEL.getLocalId(server.getId())
                && (messageEvent.getMessageContent().startsWith("carrylog;")
                || messageEvent.getMessageContent().startsWith("carrylogs;"))) {

            String[] splitContent = messageEvent.getMessageContent().split(";");
            if (splitContent.length != 3) {
                return;
            }

            long channelId = Long.parseLong(splitContent[1]);
            final String attachmentLink = splitContent[2];

            if (attachmentLink.equals("{transcript_url}")) {
                return;
            }

            long approvingChannelId = IdList.APPROVING_CHANNEL.getLocalId(server.getId());
            Optional<TextChannel> approvingChannel = messageEvent.getApi()
                    .getTextChannelById(approvingChannelId);

            QueueConnection.getInstance()
                    .getCarryQueuesByQueueStep(QueueStep.TRANSCRIPT)
                    .orElse(new HashSet<>())
                    .stream().filter(carryQueueModel -> carryQueueModel.getRelationId() == channelId)
                    .forEach(queueModel -> {
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
                                                            .getEmbed(queueModel.getTime())
                                                            .setTitle("Information")
                                                            .setColor(EmbedColor.DEFAULT.getColor())
                                                            .addInlineField("Number of carries",
                                                                    String.valueOf(queueModel.getAmount()))
                                                            .addInlineField("Type of carry",
                                                                    queueModel.getCarryTier().getDisplayName() + " - "
                                                                            + queueModel.getCarryDifficulty().getDisplayName())
                                                            .addInlineField("Player",
                                                                    messageEvent.getApi().getUserById(queueModel.getPlayer().getId()).join().getMentionTag())
                                                            .addInlineField("Carrier", carrier.getMentionTag())
                                                            .addInlineField("Transcript-Link", "[Click to open]" +
                                                                    "(https://tickettool.xyz/direct?url=" + queueModel.getAttachmentLink() + ")")).join());
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
                                                        "(https://tickettool" +
                                                        ".xyz/direct?url=" + queueModel.getAttachmentLink() +
                                                        ")"));
                            }

                            QueueConnection.getInstance().deleteQueue(queueModel.getId());
                        }
                    });

            LeaderboardService.getInstance().refreshLeaderboard();
        }
    }
}
