package net.dungeonhub.application.enums

import dev.kord.common.entity.Snowflake
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.i18n.toKey
import dev.kordex.i18n.Key
import net.dungeonhub.application.service.ServerService.getActualServerProperty
import java.util.*

//TODO implement related properties
//TODO does it make sense to make relatedProperty object instead of array?
/**
 * This enum acts as a list of all properties that can be set on each server individually.
 *
 * Please try to use this instead of hardcoding values, as the bot should be able to be used on any server it is
 * added to.
 */
enum class ServerProperty(
    override val readableName: Key,
    val propertyType: ServerPropertyType = ServerPropertyType.STRING,
    val enabled: Boolean = true,
    val relatedProperties: Array<ServerProperty> = arrayOf()
) : ChoiceEnum {
    BAN_MESSAGE("ban_message"),
    UNBAN_FORM("unban_form"),

    SCORE_ENABLED("score_enabled", ServerPropertyType.BOOLEAN, false),
    COMPACT_LEADERBOARD("compact_leaderboard", ServerPropertyType.BOOLEAN),
    TOTAL_SCORE_EVENT("total_score_event", ServerPropertyType.BOOLEAN),

    MODERATION_LOGS_CHANNEL("id_moderation_logs_channel", ServerPropertyType.CHANNEL),
    SCORE_LOGS_CHANNEL("id_score_logs_channel", ServerPropertyType.CHANNEL),
    STRIKES_LOGS_CHANNEL("id_strikes_logs_channel", ServerPropertyType.CHANNEL),
    LOG_APPROVING_CHANNEL("id_log_approving_channel", ServerPropertyType.CHANNEL),
    TRANSCRIPTS_CHANNEL("id_transcripts_channel", ServerPropertyType.CHANNEL),
    TICKET_LOGS_CHANNEL("id_ticket_logs_channel", ServerPropertyType.CHANNEL),
    SERVICE_TEAM_RULES_CHANNEL("id_service_team_rules_channel", ServerPropertyType.CHANNEL),
    CNT_MESSAGES_CHANNEL("cnt_messages_channel", ServerPropertyType.CHANNEL),
    CNT_INFORMATION_CHANNEL("cnt_information_channel", ServerPropertyType.CHANNEL),

    CNT_PING_ROLE("id_cnt_ping_role", ServerPropertyType.ROLE),
    SCORE_MANAGEMENT_ROLE("id_score_management_score", ServerPropertyType.ROLE),
    PURGE_IMMUNITY_ROLE("id_purge_immunity_role", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_UNDER_THREE("id_cnt_role_requirement_<3", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_THREE_TO_FIVE("id_cnt_role_requirement_3-5", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_FIVE_TO_TEN("id_cnt_role_requirement_5-10", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_TEN_TO_FIFTEEN("id_cnt_role_requirement_10-15", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_FIFTEEN_TO_TWENTY("id_cnt_role_requirement_15-20", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_TWENTY_TO_TWENTYFIVE("id_cnt_role_requirement_20-25", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_TWENTYFIVE_TO_FIFTY("id_cnt_role_requirement_25-50", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_FIFTY_TO_HUNDRED("id_cnt_role_requirement_50-100", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_HUNDRED_TO_TWOHUNDRED("id_cnt_role_requirement_100-200", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_TWOHUNDRED_TO_FOURHUNDRED("id_cnt_role_requirement_200-400", ServerPropertyType.ROLE),
    CNT_ROLE_REQUIREMENT_OVER_FOURHUNDRED("id_cnt_role_requirement_400+", ServerPropertyType.ROLE),

    VERIFIED_GROUP_ROLE("server_verified_group_role", ServerPropertyType.ROLE),
    STAFF_GROUP_ROLE("server_staff_group_role", ServerPropertyType.ROLE),
    ADMINISTRATION_GROUP_ROLE("server_administration_group_role", ServerPropertyType.ROLE),
    MANAGEMENT_GROUP_ROLE("server_management_group_role", ServerPropertyType.ROLE),
    VC_CREATE_CATEGORY("vc_create_category", ServerPropertyType.CATEGORY);




    constructor(
        readableName: String,
        propertyType: ServerPropertyType = ServerPropertyType.STRING,
        enabled: Boolean = true,
        relatedProperties: Array<ServerProperty> = arrayOf()
    ) : this(readableName.toKey(), propertyType, enabled, relatedProperties)

    constructor(name: String, enabled: Boolean) : this(name.toKey(), ServerPropertyType.STRING, enabled)

    constructor(name: String, propertyType: ServerPropertyType, relatedProperties: Array<ServerProperty>) : this(
        name.toKey(),
        propertyType,
        true,
        relatedProperties
    )

    fun getValue(serverId: Long): String? {
        return getActualServerProperty(serverId, this)
    }

    fun getValue(serverId: Snowflake): String? {
        return getValue(serverId.value.toLong())
    }

    fun canAccept(value: String?): Boolean {
        return propertyType.canAccept(value)
    }

    fun isEnabled(serverId: Long): Boolean {
        return enabled &&
                relatedProperties.map { it.getValue(serverId) }.all { it.equals("true", ignoreCase = true) } &&
                relatedProperties.all { it.isEnabled(serverId) }
    }

    companion object {
        fun getPropertyByName(name: String?): Optional<ServerProperty> {
            return Arrays.stream(entries.toTypedArray())
                .filter { serverProperty: ServerProperty -> serverProperty.name.equals(name, ignoreCase = true) }
                .findAny()
        }
    }
}