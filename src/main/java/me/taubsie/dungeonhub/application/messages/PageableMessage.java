package me.taubsie.dungeonhub.application.messages;

import lombok.Getter;
import me.taubsie.dungeonhub.application.connection.DiscordConnection;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.component.*;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.callback.ComponentInteractionOriginalMessageUpdater;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class PageableMessage {
    private static final String FIRST = "first";
    private static final String BACK = "back";
    private static final String FORWARD = "forward";
    private static final String LAST = "last";
    @Getter
    private final long channel;
    @Getter
    private final long messageId;
    private int currentPage;

    protected PageableMessage(int currentPage, long channel, long messageId) {
        this.currentPage = currentPage;
        this.channel = channel;
        this.messageId = messageId;

        getMessage().ifPresent(this::applyListener);
    }

    public Optional<Server> getServer() {
        return getMessage().flatMap(Message::getServer);
    }

    public static HighLevelComponent[] getComponents(boolean firstPage, boolean lastPage) {
        Button startButton = new ButtonBuilder()
                .setCustomId(FIRST)
                .setStyle(ButtonStyle.SUCCESS)
                .setEmoji("⏪")
                .setDisabled(firstPage)
                .build();

        Button backButton = new ButtonBuilder()
                .setCustomId(BACK)
                .setStyle(ButtonStyle.SUCCESS)
                .setEmoji("◀")
                .setDisabled(firstPage)
                .build();

        Button forwardButton = new ButtonBuilder()
                .setCustomId(FORWARD)
                .setStyle(ButtonStyle.SUCCESS)
                .setEmoji("▶")
                .setDisabled(lastPage)
                .build();

        Button endButton = new ButtonBuilder()
                .setCustomId(LAST)
                .setStyle(ButtonStyle.SUCCESS)
                .setEmoji("⏩")
                .setDisabled(lastPage)
                .build();

        return new HighLevelComponent[]{ActionRow.of(startButton, backButton, forwardButton, endButton)};
    }

    public abstract int getMaxPage();

    public abstract void updatePage(ComponentInteractionOriginalMessageUpdater updater, int currentPage);

    public Optional<Message> getMessage() {
        return DiscordConnection.getInstance().getBot()
                .getTextChannelById(channel)
                .map(textChannel -> textChannel.getMessageById(messageId))
                .map(CompletableFuture::join);
    }

    //TODO rework with 0-based counting
    private void applyListener(Message message) {
        message.addMessageComponentCreateListener(event -> {
            String customId = event.getMessageComponentInteraction().getCustomId();

            //TODO test
            int currentMaxPage = getMaxPage() - 1;

            int newPage = switch(customId.toLowerCase()) {
                case BACK -> Math.max(0, (currentPage - 1));
                case FORWARD -> Math.min(currentMaxPage, (currentPage + 1));
                case LAST -> currentMaxPage;
                default -> 0;
            };

            ComponentInteractionOriginalMessageUpdater componentInteractionOriginalMessageUpdater =
                    event.getMessageComponentInteraction().createOriginalMessageUpdater();

            componentInteractionOriginalMessageUpdater
                    .removeAllComponents()
                    .addComponents(getComponents(newPage == 0, newPage == currentMaxPage));

            currentPage = newPage;

            updatePage(componentInteractionOriginalMessageUpdater, newPage);
        }).removeAfter(5, TimeUnit.MINUTES).addRemoveHandler(() -> message.createUpdater().removeAllComponents().applyChanges());
    }
}