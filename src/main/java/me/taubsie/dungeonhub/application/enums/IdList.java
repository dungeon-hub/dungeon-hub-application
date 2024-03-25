package me.taubsie.dungeonhub.application.enums;

import lombok.Getter;

@Getter
public enum IdList {
    SERVER(693263712626278553L, 1023684107877761196L),
    VERIFIED_ROLE(700494113614594179L, 1036373005720358972L),
    ALT_VERIFIED_ROLE(792206452348682251L, 1036373005720358972L);

    private final long id;
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