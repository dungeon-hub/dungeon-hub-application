package net.dungeonhub.application.enums

import dev.kordex.core.i18n.toKey
import dev.kordex.i18n.Key
import net.dungeonhub.enums.RoleAction
import net.dungeonhub.enums.RoleRequirementComparison
import net.dungeonhub.enums.RoleRequirementType
import net.dungeonhub.enums.ScoreResetType
import net.dungeonhub.enums.ScoreType
import net.dungeonhub.enums.StaticMessageType
import net.dungeonhub.i18n.Translations
import net.dungeonhub.model.carry_type.CarryTypeModel
import java.util.Locale

// The readable names of the API enums, which used to be part of the API model.

val RoleAction.readableName: Key
    get() = when (this) {
        RoleAction.None -> Translations.RoleActions.None.readableName
        RoleAction.ApplyAndRemoveWhenVerified -> Translations.RoleActions.ApplyAndRemoveWhenVerified.readableName
        RoleAction.ApplyAndRemoveWhenUnverified -> Translations.RoleActions.ApplyAndRemoveWhenUnverified.readableName
        RoleAction.ApplyWhenVerified -> Translations.RoleActions.ApplyWhenVerified.readableName
        RoleAction.ApplyWhenUnverified -> Translations.RoleActions.ApplyWhenUnverified.readableName
        RoleAction.RemoveWhenVerified -> Translations.RoleActions.RemoveWhenVerified.readableName
        RoleAction.RemoveWhenUnverified -> Translations.RoleActions.RemoveWhenUnverified.readableName
        RoleAction.ApplyAlways -> Translations.RoleActions.ApplyAlways.readableName
    }

val ScoreType.readableName: Key
    get() = when (this) {
        ScoreType.Default -> Translations.ScoreType.Default.readableName
        ScoreType.Alltime -> Translations.ScoreType.Alltime.readableName
        ScoreType.Event -> Translations.ScoreType.Event.readableName
    }

val ScoreType.leaderboardSuffix: Key?
    get() = when (this) {
        ScoreType.Default -> null
        ScoreType.Alltime -> Translations.ScoreType.Alltime.suffix
        ScoreType.Event -> Translations.ScoreType.Event.suffix
    }

fun ScoreType.getLeaderboardTitle(carryType: CarryTypeModel?, locale: Locale? = Locale.ENGLISH): String {
    val suffix = leaderboardSuffix?.withLocale(locale)?.translate()

    return Translations.Leaderboard.title.withLocale(locale).translateNamed(
        "name" to (carryType?.displayName ?: Translations.Leaderboard.Title.total.withLocale(locale).translate()),
        "suffix" to if (suffix.isNullOrBlank()) "" else " $suffix"
    )
}

val ScoreResetType.readableName: Key
    get() = name.toKey()

val StaticMessageType.readableName: Key
    get() = name.splitWords().toKey()

val RoleRequirementType.readableName: Key
    get() = name.splitWords().toKey()

val RoleRequirementComparison.readableName: Key
    get() = when (this) {
        RoleRequirementComparison.Equal -> "="
        RoleRequirementComparison.GreaterOrEqual -> ">="
        RoleRequirementComparison.Greater -> ">"
        RoleRequirementComparison.LessOrEqual -> "<="
        RoleRequirementComparison.Less -> "<"
    }.toKey()

private fun String.splitWords() = replace(Regex("([A-Z])"), " $1").trim()
