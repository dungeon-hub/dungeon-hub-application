package me.taubsie.dungeonhub.application.classes;

import lombok.Builder;

@Builder
public record FlagDetail(boolean flagged, String reason, Long staff, String evidence) {
    public String format() {
        return (reason() != null ? "`" + reason() + "` " : "")
                + (staff() != null ? "added by <@" + staff() + "> " : "")
                + (evidence() != null ? "||" + evidence() + "||" : "");
    }

    @Override
    public String toString() {
        return format();
    }
}