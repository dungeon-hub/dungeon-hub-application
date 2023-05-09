package me.taubsie.carrylogs.application.classes;

import me.taubsie.dungeonhub.common.config.Nameable;

import java.util.Arrays;
import java.util.Optional;

public enum ServerProperty implements Nameable {
    PROFILE_MODERATION_BAN_MESSAGE("profile_moderation_message", "You got banned from `%server%` because of a" +
            " suspicious user profile.\nIf you think this might be a mistake, please click the button or appeal at: " +
            "%form%"),
    UNBAN_FORM("unban_form", "https://dyno.gg/form/ee627bf6"),

    SCORE_ENABLED("score_enabled", ServerPropertyType.BOOLEAN, "false"),

    MODERATION_LOGS_CHANNEL("id_moderation_logs_channel", ServerPropertyType.CHANNEL, "996151183519514814"),
    STRIKES_LOGS_CHANNEL("id_strikes_logs_channel", ServerPropertyType.CHANNEL, ""),

    F4_ROLE("id_f4_role", ServerPropertyType.ROLE, "793521662678794250"),
    F5_ROLE("id_f5_role", ServerPropertyType.ROLE, "793521664737935361"),
    F6_ROLE("id_f6_role", ServerPropertyType.ROLE, "793197838661451799"),
    F7_ROLE("id_f7_role", ServerPropertyType.ROLE, "791348459801804850"),
    MASTER_ROLE("id_master_mode_role", ServerPropertyType.ROLE, "842840236312100885"),
    EMAN_T3_ROLE("id_eman_t3_role", ServerPropertyType.ROLE, "992914655901138994"),
    EMAN_T4_ROLE("id_eman_t4_role", ServerPropertyType.ROLE, "1004517546076143737"),
    BLAZE_T2_ROLE("id_blaze_t2_role", ServerPropertyType.ROLE, "793521667116367932"),
    BLAZE_T3_ROLE("id_blaze_t3_role", ServerPropertyType.ROLE, "1004510869662748802"),
    BLAZE_T4_ROLE("id_blaze_t4_role", ServerPropertyType.ROLE, "1004510847105765467"),
    KUUDRA_BASIC_ROLE("id_kuudra_basic_role", ServerPropertyType.ROLE, "1078684025218146344"),
    KUUDRA_HOT_ROLE("id_kuudra_hot_role", ServerPropertyType.ROLE, "1078684026019258379"),
    KUUDRA_BURNING_ROLE("id_kuudra_burning_role", ServerPropertyType.ROLE, "1078684027961229332"),
    KUUDRA_FIERY_ROLE("id_kuudra_fiery_role", ServerPropertyType.ROLE, "1078684029081092137"),
    KUUDRA_INFERNAL_ROLE("id_kuudra_infernal_role", ServerPropertyType.ROLE, "1078684030125490296");

    private final String name;
    private final ServerPropertyType propertyType;
    private final String defaultValue;
    private final boolean enabled;

    ServerProperty(String name, String defaultValue) {
        this(name, ServerPropertyType.STRING, defaultValue);
    }

    ServerProperty(String name, ServerPropertyType propertyType, String defaultValue) {
        this(name, propertyType, defaultValue, true);
    }

    ServerProperty(String name, ServerPropertyType propertyType, String defaultValue, boolean enabled) {
        this.name = name;
        this.propertyType = propertyType;
        this.defaultValue = defaultValue;
        this.enabled = enabled;
    }

    public static Optional<ServerProperty> getPropertyByName(String name) {
        return Arrays.stream(ServerProperty.values())
                .filter(serverProperty -> serverProperty.getName().equalsIgnoreCase(name))
                .findAny();
    }

    @Override
    public String getName() {
        return name;
    }

    public ServerPropertyType getPropertyType() {
        return propertyType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    //TODO maybe implement logic here too
    public boolean isEnabled() {
        return enabled;
    }
}