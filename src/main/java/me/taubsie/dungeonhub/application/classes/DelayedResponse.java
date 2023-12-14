package me.taubsie.dungeonhub.application.classes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.taubsie.dungeonhub.application.exceptions.CommandExecutionException;
import me.taubsie.dungeonhub.application.service.ApplicationService;
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Optional;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DelayedResponse {
    @Nullable String content;
    @Nullable URL attachmentUrl;
    @Nullable EmbedBuilder[] embed;
    @Nullable HighLevelComponent[] highLevelComponents;

    public static DelayedResponseBuilder builder() {
        return new DelayedResponse.DelayedResponseBuilder();
    }

    public static DelayedResponse fromEmbed(EmbedBuilder embedBuilder) {
        return DelayedResponse.builder().setEmbed(embedBuilder).build();
    }

    public static DelayedResponse fromEmbedWithAttachment(EmbedBuilder embedBuilder, URL attachmentUrl) {
        return DelayedResponse.builder().setEmbed(embedBuilder).setAttachmentUrl(attachmentUrl).build();
    }

    public static DelayedResponse fromException(CommandExecutionException commandExecutionException) {
        return fromEmbed(ApplicationService.getInstance().getErrorEmbed(commandExecutionException));
    }

    public Optional<URL> getAttachmentUrl() {
        return Optional.ofNullable(attachmentUrl);
    }

    public static class DelayedResponseBuilder {
        @Setter
        @Nullable
        String content;
        @Nullable
        URL attachmentUrl;
        @Nullable
        EmbedBuilder[] embed;
        @Nullable
        HighLevelComponent[] highLevelComponents;

        public DelayedResponse build() {
            return new DelayedResponse(
                    content != null ? content : "",
                    attachmentUrl != null ? attachmentUrl : null,
                    embed != null ? embed : new EmbedBuilder[0],
                    highLevelComponents != null ? highLevelComponents : new HighLevelComponent[0]
            );
        }

        public DelayedResponseBuilder setEmbed(EmbedBuilder... embed) {
            this.embed = embed;
            return this;
        }

        public DelayedResponseBuilder setHighLevelComponents(HighLevelComponent... highLevelComponents) {
            this.highLevelComponents = highLevelComponents;
            return this;
        }

        public DelayedResponseBuilder setAttachmentUrl(URL attachmentUrl) {
            this.attachmentUrl = attachmentUrl;
            return this;
        }
    }
}