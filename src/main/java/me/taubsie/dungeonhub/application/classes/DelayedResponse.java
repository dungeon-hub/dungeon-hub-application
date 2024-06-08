package me.taubsie.dungeonhub.application.classes;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.javacord.api.entity.message.component.HighLevelComponent;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.jetbrains.annotations.Nullable;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DelayedResponse {
    @Nullable String content;
    @Nullable EmbedBuilder[] embed;
    @Nullable HighLevelComponent[] highLevelComponents;

    public static DelayedResponseBuilder builder() {
        return new DelayedResponse.DelayedResponseBuilder();
    }

    public static DelayedResponse fromEmbed(EmbedBuilder embedBuilder) {
        return DelayedResponse.builder().setEmbed(embedBuilder).build();
    }

    public static class DelayedResponseBuilder {
        @Setter
        @Nullable
        String content;
        @Nullable
        EmbedBuilder[] embed;
        @Nullable
        HighLevelComponent[] highLevelComponents;

        public DelayedResponse build() {
            return new DelayedResponse(
                    content != null ? content : "",
                    embed != null ? embed : new EmbedBuilder[0],
                    highLevelComponents != null ? highLevelComponents : new HighLevelComponent[0]
            );
        }

        public DelayedResponseBuilder setEmbed(EmbedBuilder... embed) {
            this.embed = embed;
            return this;
        }
    }
}