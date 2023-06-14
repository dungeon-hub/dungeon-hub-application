package me.taubsie.carrylogs.application.classes;

import me.taubsie.carrylogs.application.service.ServerService;
import me.taubsie.dungeonhub.common.config.Nameable;

import java.util.Arrays;
import java.util.Optional;

//TODO implement related properties
//TODO does it make sense to make relatedProperty object instead of array?

/**
 * This enum acts as a list of all properties that can be set on each server individually.
 * It is still left to be expanded as there are a few properties missing (mostly from
 * {@link me.taubsie.carrylogs.application.enums.IdList}).
 * <p>
 * Please try to use this instead of hardcoding values, as the bot should be able to be used on any server it is
 * added to.
 */
public enum ServerProperty implements Nameable {
    PROFILE_MODERATION_BAN_MESSAGE("profile_moderation_message"),
    BAN_MESSAGE("ban_message"),
    UNBAN_FORM("unban_form"),

    SCORE_ENABLED("score_enabled", ServerPropertyType.BOOLEAN, false),

    MODERATION_LOGS_CHANNEL("id_moderation_logs_channel", ServerPropertyType.CHANNEL),
    STRIKES_LOGS_CHANNEL("id_strikes_logs_channel", ServerPropertyType.CHANNEL),
    DUNGEON_PRICE_CHANNEL("dungeon_price_channel", ServerPropertyType.CHANNEL),
    ENDERMAN_PRICE_CHANNEL("enderman_price_channel", ServerPropertyType.CHANNEL),
    BLAZE_PRICE_CHANNEL("blaze_price_channel", ServerPropertyType.CHANNEL),
    KUUDRA_PRICE_CHANNEL("kuudra_price_channel", ServerPropertyType.CHANNEL),

    F4_ROLE("id_f4_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    F5_ROLE("id_f5_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    F6_ROLE("id_f6_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    F7_ROLE("id_f7_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    MASTER_ROLE("id_master_mode_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    EMAN_T3_ROLE("id_eman_t3_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    EMAN_T4_ROLE("id_eman_t4_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    BLAZE_T2_ROLE("id_blaze_t2_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    BLAZE_T3_ROLE("id_blaze_t3_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    BLAZE_T4_ROLE("id_blaze_t4_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    KUUDRA_BASIC_ROLE("id_kuudra_basic_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    KUUDRA_HOT_ROLE("id_kuudra_hot_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    KUUDRA_BURNING_ROLE("id_kuudra_burning_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    KUUDRA_FIERY_ROLE("id_kuudra_fiery_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED}),
    KUUDRA_INFERNAL_ROLE("id_kuudra_infernal_role", ServerPropertyType.ROLE, new ServerProperty[]{SCORE_ENABLED});

    private final String name;
    private final ServerPropertyType propertyType;
    private final boolean enabled;
    private final ServerProperty[] relatedProperties;

    ServerProperty(String name) {
        this(name, ServerPropertyType.STRING);
    }

    ServerProperty(String name, boolean enabled) {
        this(name, ServerPropertyType.STRING, enabled);
    }

    ServerProperty(String name, ServerPropertyType propertyType) {
        this(name, propertyType, true);
    }

    ServerProperty(String name, ServerPropertyType propertyType, ServerProperty[] relatedProperties) {
        this(name, propertyType, true, relatedProperties);
    }

    ServerProperty(String name, ServerPropertyType propertyType, boolean enabled) {
        this(name, propertyType, enabled, new ServerProperty[]{});
    }

    ServerProperty(String name, ServerPropertyType propertyType, boolean enabled, ServerProperty[] relatedProperties) {
        this.name = name;
        this.propertyType = propertyType;
        this.enabled = enabled;
        this.relatedProperties = relatedProperties;
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

    public Optional<String> getValue(long serverId) {
        return ServerService.getInstance().getActualServerProperty(serverId, this);
    }

    public boolean canAccept(String value) {
        return propertyType.canAccept(value);
    }

    public boolean isEnabled(long serverId) {
        return enabled
                && Arrays.stream(relatedProperties)
                .flatMap(serverProperty -> serverProperty.getValue(serverId).stream())
                .allMatch(s -> s.equalsIgnoreCase("true"))
                && Arrays.stream(relatedProperties)
                .allMatch(serverProperty -> serverProperty.isEnabled(serverId));
    }

    public boolean isEnabled() {
        return enabled;
    }
}