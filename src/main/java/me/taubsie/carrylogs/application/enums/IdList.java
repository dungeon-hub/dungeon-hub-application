package me.taubsie.carrylogs.application.enums;

import lombok.Getter;

public enum IdList {
    SERVER(693263712626278553L, 1023684107877761196L),
    TRANSCRIPTS_CHANNEL(796770232340578365L, 1036375112619937792L),
    APPROVING_CHANNEL(1062481082500522095L, 1036387847000825877L),
    DUNGEON_LOGS_CHANNEL(1021543535834583090L, 1043328648641523825L),
    SLAYER_LOGS_CHANNEL(1048235346422407168L, 1043328648641523825L),
    KUUDRA_LOGS_CHANNEL(0L /*TODO*/, 1067118963831615609L),
    DUNGEON_LEADERBOARD_CHANNEL(1063987051986440262L, 1063900837291774032L),
    SLAYER_LEADERBOARD_CHANNEL(1063987003806453781L, 1063900854974955590L),
    KUUDRA_LEADERBOARD_CHANNEL(0L /*TODO*/, 1078053187229065268L),
    SCORE_LOGS_CHANNEL(1068265390263767152L, 1067118963831615609L),
    MODERATION_LOGS_CHANNEL(996151183519514814L, 1067118963831615609L),
    VERIFIED_ROLE(700494113614594179L, 1036373005720358972L),
    ALT_VERIFIED_ROLE(792206452348682251L, 1036373005720358972L),
    F4_CATEGORY(CarryType.F4, 805834037108670464L, 1026291896336793631L),
    F5_CATEGORY(CarryType.F5, 805833843633815664L, 1026291912157696000L),
    F6_CATEGORY(CarryType.F6, 805833672678309908L, 1026291924216336395L),
    F7_CATEGORY(CarryType.F7, 805833601748828200L, 1026291937440960615L),
    MASTER_CATEGORY(CarryType.MASTER_MODE, 842840550704939053L, 1026291957594587167L),
    EMAN_CATEGORY(CarryType.EMAN, 992922867857641594L, 1026291971872018432L),
    BLAZE_CATEGORY(CarryType.BLAZE, 992922801075912764L, 1026291986090704968L),
    KUUDRA_CATEGORY(CarryType.KUUDRA, 0L /*TODO*/, 1078053050004033646L);

    @Getter
    private final long id;
    @Getter
    private final long testId;

    private CarryType carryType;

    IdList(long id, long testId) {
        this.id = id;
        this.testId = testId;
    }

    IdList(CarryType carryType, long id, long testId) {
        this(id, testId);

        this.carryType = carryType;
    }

    public static boolean isCarryCategory(long id, long serverId) {
        for (IdList idList : values()) {
            if (idList.carryType != null && idList.getLocalId(serverId) == id) {
                return true;
            }
        }

        return false;
    }

    public static IdList getCarryCategory(long id, long serverId) {
        for (IdList idList : values()) {
            if (idList.carryType != null && idList.getLocalId(serverId) == id) {
                return idList;
            }
        }

        return null;
    }

    public CarryType getCarryType() {
        return carryType;
    }

    public long getLocalId(long serverId) {
        if (SERVER.id == serverId) {
            return id;
        } else {
            return testId;
        }
    }
}