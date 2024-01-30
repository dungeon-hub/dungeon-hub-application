package me.taubsie.dungeonhub.application.enums;

import lombok.Getter;

public enum IdList {
    SERVER(693263712626278553L, 1023684107877761196L),
    TRANSCRIPTS_CHANNEL(796770232340578365L, 1036375112619937792L),
    APPROVING_CHANNEL(1062481082500522095L, 1036387847000825877L),
    VERIFIED_ROLE(700494113614594179L, 1036373005720358972L),
    ALT_VERIFIED_ROLE(792206452348682251L, 1036373005720358972L);

    @Getter
    private final long id;
    @Getter
    private final long testId;

    IdList(long id, long testId) {
        this.id = id;
        this.testId = testId;
    }

    public long getLocalId(long serverId) {
        if (SERVER.id == serverId) {
            return id;
        } else {
            return testId;
        }
    }
}