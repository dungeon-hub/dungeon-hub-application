package me.taubsie.carrylogs.application.enums;

import lombok.Getter;
import me.taubsie.carrylogs.CarryRole;

public enum IdList {
    SERVER(693263712626278553L, 1023684107877761196L),
    TRANSCRIPTS_CHANNEL(796770232340578365L, 1036375112619937792L),
    APPROVING_CHANNEL(1062481082500522095L, 1036387847000825877L),
    DUNGEON_LOGS_CHANNEL(1021543535834583090L, 1043328648641523825L),
    SLAYER_LOGS_CHANNEL(1048235346422407168L, 1043328648641523825L),
    KUUDRA_LOGS_CHANNEL(0L /*TODO*/, 0L /*TODO*/),
    DUNGEON_LEADERBOARD_CHANNEL(1063987051986440262L, 1063900837291774032L),
    SLAYER_LEADERBOARD_CHANNEL(1063987003806453781L, 1063900854974955590L),
    KUUDRA_LEADERBOARD_CHANNEL(0L /*TODO*/, 0L /*TODO*/),
    SCORE_LOGS_CHANNEL(1068265390263767152L, 1067118963831615609L),
    MODERATION_LOGS_CHANNEL(996151183519514814L, 1067118963831615609L),
    F4_ROLE(CarryRole.F4, 793521662678794250L, 1061116185132933240L),
    F5_ROLE(CarryRole.F5, 793521664737935361L, 1061116152245395596L),
    F6_ROLE(CarryRole.F6, 793197838661451799L, 1061116212265898034L),
    F7_ROLE(CarryRole.F7, 791348459801804850L, 1061116224697794671L),
    MASTER_ROLE(CarryRole.MASTER_MODE, 842840236312100885L, 1061116258269016134L),
    EMAN_T3_ROLE(CarryRole.EMAN_T3, 992914655901138994L, 1061116287666888805L),
    EMAN_T4_ROLE(CarryRole.EMAN_T4, 1004517546076143737L, 1061116299700351076L),
    BLAZE_T2_ROLE(CarryRole.BLAZE_T2, 793521667116367932L, 1061116379874476112L),
    BLAZE_T3_ROLE(CarryRole.BLAZE_T3, 1004510869662748802L, 1061116390632861827L),
    BLAZE_T4_ROLE(CarryRole.BLAZE_T4, 1004510847105765467L, 1061116398866268160L),
    VERIFIED_ROLE(700494113614594179L, 1036373005720358972L),
    ALT_VERIFIED_ROLE(792206452348682251L, 1036373005720358972L),
    F4_CATEGORY(CarryType.F4, 805834037108670464L, 1026291896336793631L),
    F5_CATEGORY(CarryType.F5, 805833843633815664L, 1026291912157696000L),
    F6_CATEGORY(CarryType.F6, 805833672678309908L, 1026291924216336395L),
    F7_CATEGORY(CarryType.F7, 805833601748828200L, 1026291937440960615L),
    MASTER_CATEGORY(CarryType.MASTER_MODE, 842840550704939053L, 1026291957594587167L),
    EMAN_CATEGORY(CarryType.EMAN, 992922867857641594L, 1026291971872018432L),
    BLAZE_CATEGORY(CarryType.BLAZE, 992922801075912764L, 1026291986090704968L),
    KUUDRA_CATEGORY(CarryType.KUUDRA, 0L /*TODO*/, 0L /*TODO*/);

    @Getter
    private final long id;
    @Getter
    private final long testId;

    private CarryType carryType;
    private CarryRole carryRole;

    IdList(long id, long testId) {
        this.id = id;
        this.testId = testId;
    }

    IdList(CarryType carryType, long id, long testId) {
        this(id, testId);

        this.carryType = carryType;
    }

    IdList(CarryRole carryRole, long id, long testId) {
        this(id, testId);

        this.carryRole = carryRole;
    }

    public static boolean isCarryCategory(long id, long serverId) {
        for(IdList idList : values()) {
            if(idList.carryType != null && idList.getLocalId(serverId) == id) {
                return true;
            }
        }

        return false;
    }

    public static IdList getCarryCategory(long id, long serverId) {
        for(IdList idList : values()) {
            if(idList.carryType != null && idList.getLocalId(serverId) == id) {
                return idList;
            }
        }

        return null;
    }

    public CarryType getCarryType() {
        return carryType;
    }

    public CarryRole getCarryRole() {
        return carryRole;
    }

    public long getLocalId(long serverId) {
        if(SERVER.id == serverId) {
            return id;
        } else {
            return testId;
        }
    }

    public IdList[] getSlayerCarryRoles() {
        return new IdList[]{
                EMAN_T3_ROLE,
                EMAN_T4_ROLE,
                BLAZE_T2_ROLE,
                BLAZE_T3_ROLE,
                BLAZE_T4_ROLE
        };
    }

    public IdList[] getDungeonCarryRoles() {
        return new IdList[]{
                F4_ROLE,
                F5_ROLE,
                F6_ROLE,
                F7_ROLE,
                MASTER_ROLE
        };
    }

    public IdList[] getKuudraCarryRoles() {
        //TODO add missing roles
        return new IdList[]{};
    }

    public IdList[] getCarryRoles() {
        IdList[] result = new IdList[getDungeonCarryRoles().length + getSlayerCarryRoles().length];

        System.arraycopy(getDungeonCarryRoles(), 0, result, 0, getDungeonCarryRoles().length);
        System.arraycopy(getSlayerCarryRoles(), 0, result, getDungeonCarryRoles().length, getSlayerCarryRoles().length);

        return result;
    }
}