package me.taubsie.dungeonhub.application.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.rest.request.KtorRequestException
import kotlinx.coroutines.flow.toList
import me.taubsie.dungeonhub.application.connection.getMutualServers
import me.taubsie.dungeonhub.application.exceptions.*
import me.taubsie.dungeonhub.application.misc.PlayerInformation
import net.dungeonhub.connection.DiscordRoleConnection
import net.dungeonhub.connection.DiscordUserConnection
import net.dungeonhub.hypixel.connection.HypixelApiConnection
import net.dungeonhub.model.discord_role.DiscordRoleModel
import net.dungeonhub.model.discord_user.DiscordUserModel
import net.dungeonhub.model.discord_user.DiscordUserUpdateModel
import net.dungeonhub.mojang.connection.MojangConnection
import org.jetbrains.annotations.Contract
import java.util.*
import java.util.function.Function
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.stream.Collector
import java.util.stream.Collectors

/**
 * Service class for managing nicknames.
 *
 *
 * The `NicknameService` class provides a singleton instance through the [.getInstance] method.
 * It ensures that only one instance of the service is created and returned.
 *
 *
 * Example usage:
 *
 * <pre>
 * `NicknameService nicknameService = NicknameService.getInstance();
` *
</pre> *
 */
object NicknameService {
    @Throws(CommandExecutionWarning::class)
    fun linkToIgn(ign: String, user: User): UUID {
        val uuid = MojangConnection.getUUIDByName(ign)

        //TODO test if cache expiration 1 actually works
        val hypixelName = HypixelApiConnection().withCacheExpiration(1).getHypixelLinkedDiscord(uuid)
        val username = user.tag

        if (hypixelName == null) {
            throw InvalidOptionWarning(
                "ign", """
     Please add the correct discord-account (`${user.username}`) to your hypixel social menu.
     To learn more about how to do this, use `/help verification`.
     """.trimIndent()
            )
        }

        if (!hypixelName.equals(username, ignoreCase = true)) {
            throw HypixelLinkedToOtherWarning(
                ign,
                hypixelName,
                user.username
            )
        }

        DiscordUserConnection.findUserByUuid(uuid)?.let {
            val updateModel = it.getUpdateModel()
            updateModel.minecraftId = null
            DiscordUserConnection.updateUser(it.id, updateModel)

            MassSyncService.usersToSync += Snowflake(it.id)
        }

        val updateModel = DiscordUserUpdateModel(uuid)

        val userModel = DiscordUserConnection.updateUser(user.id.value.toLong(), updateModel)
            ?: throw CommandExecutionException("Couldn't update your user data.")

        return userModel.minecraftId!!
    }

    /**
     * Updates the nickname of the specified user on all mutual servers.
     *
     *
     * The `updateNickname` method without the server parameter iterates over all mutual servers of the user and calls
     * the [.updateNickname] method for each server.
     *
     * @param user the Discord user for whom to update the nickname on all mutual servers
     * @throws NoNameSchemaWarning if no valid role with a non-blank name schema is found while updating the nickname
     */
    suspend fun updateNickname(user: User, roles: Map<Long, List<Role>>, cacheExpiration: Int = 60 * 3) {
        user.getMutualServers().collect { member: Member ->
            val serverRoles = roles.getOrDefault(member.guild.id.value.toLong(), null)
            try {
                updateNickname(member, serverRoles, cacheExpiration)
            } catch (ignored: NoNameSchemaWarning) {
                //ignored, just don't set a username
            }
        }
    }

    /**
     * Updates the nickname of the specified user on the given server.
     *
     *
     * The `updateNickname` method with the server parameter retrieves the Discord user model from the connection,
     * validates the user's link status, and then calls the [.updateNickname] method.
     *
     * @param member  the discord server member for whom to update the nickname
     * @throws NoNameSchemaWarning if no valid role with a non-blank name schema is found while updating the nickname
     * @throws NotLinkedException    if the user is not linked to a Minecraft account
     */
    @Throws(NoNameSchemaWarning::class, NotLinkedException::class)
    suspend fun updateNickname(member: Member, serverRoles: List<Role>?, cacheExpiration: Int = 60 * 3) {
        val discordUserModel = DiscordUserConnection.getById(member.id.value.toLong())
            ?: DiscordUserConnection.updateUser(member.id.value.toLong(), DiscordUserUpdateModel(null)) //TODO use the method "getByIdOrAdd" from DiscordUserConnection directly
            ?: throw CommandExecutionWarning("Couldn't get that users data!")

        updateNickname(member, discordUserModel, serverRoles, cacheExpiration)
    }

    /**
     * Updates the nickname of the specified user on the given server based on the Discord user model.
     *
     *
     * The `updateNickname` method takes a Discord user, a Discord user model, and a server as input. It retrieves
     * the roles of the user on the server, sorts them by position in descending order, and then determines the appropriate
     * Discord role model using the [.getRoleModel] method. Finally, it updates the user's nickname on
     * the server by loading the username from the role's name schema and the provided Discord user model.
     *
     * @param member           the discord server member for whom to update the nickname
     * @param discordUserModel the Discord user model providing additional information
     * @throws NoNameSchemaWarning if no valid role with a non-blank name schema is found while determining the role model
     */
    @Throws(NoNameSchemaWarning::class)
    suspend fun updateNickname(
        member: Member,
        discordUserModel: DiscordUserModel,
        serverRoles: List<Role>?,
        cacheExpiration: Int = 60 * 3
    ) {
        val roles: List<Role> = serverRoles ?: member.roles.toList()
        val sortedRoles = roles.sortedWith(
            Comparator.comparingInt { obj: Role -> obj.rawPosition }.reversed()
        ).toList()

        val role = getRoleModel(member, sortedRoles)

        val nickname =
            loadUsername(role.nameSchema!!, PlayerInformation(member.asUser(), discordUserModel, cacheExpiration))

        if (nickname.isBlank()) {
            return
        }

        try {
            member.edit {
                this@edit.nickname = nickname
            }
        } catch (ktor: KtorRequestException) {
            if (ktor.status.code == 403) {
                throw CommandExecutionWarning(
                    "Couldn't update the nickname due to permission problems.\n" +
                            "I tried to set it to:\n```\n$nickname\n```"
                )
            }
        }
    }

    fun loadUsername(nameSchema: String, playerInformation: PlayerInformation): String {
        val replacements = playerInformation.replacements

        val regex = "(\\{[^}]+})"
        val usernameBuilder = StringBuilder()
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(nameSchema)

        while (matcher.find()) {
            val argument = matcher.group(1)

            val repString = replacements[argument.substring(1, argument.length - 1)]?.invoke()
            if (repString != null) {
                matcher.appendReplacement(usernameBuilder, repString)
            }
        }
        matcher.appendTail(usernameBuilder)

        return usernameBuilder.toString().trim()
    }

    /**
     * Validates a Discord role using the provided map of Discord role models.
     *
     *
     * The `validateRole` method returns a [Predicate] that can be used to check if a given [Role] is valid
     * based on the information stored in the provided map of Discord role models. The validation checks include ensuring that
     * the role is present in the map, the associated DiscordRoleModel has a non-null name schema, and the name schema is not blank.
     *
     * @param discordRoles the map of Discord role models to validate roles against
     * @return a [Predicate] that can be used to validate roles
     * @throws NullPointerException if the specified map of Discord role models is `null`
     */
    @Contract(pure = true, value = "_ -> new")
    private fun validateRole(discordRoles: Map<Long, DiscordRoleModel>): Predicate<Role> {
        return Predicate { role: Role ->
            val obj = discordRoles[role.id.value.toLong()]
            Objects.nonNull(obj) && Objects.nonNull(obj!!.nameSchema) && obj.nameSchema!!.isNotBlank()
        }
    }

    /**
     * Retrieves the Discord role model for the first valid role in the provided list based on the server's role models.
     *
     *
     * The `getRoleModel` method takes a Discord server and a list of roles as input, retrieves the associated role models
     * for the server, and finds the first valid role in the list using the [.validateRole] predicate. If a valid role is
     * found, the corresponding DiscordRoleModel is returned; otherwise, a [NoNameSchemaWarning] is thrown.
     *
     * @param member the discord server member for which to retrieve role models and validate roles
     * @param roles  the list of roles to be validated and for which to find the corresponding role model
     * @return the DiscordRoleModel of the first valid role in the list
     * @throws NoNameSchemaWarning if no valid role with a non-blank name schema is found
     * @throws NullPointerException  if the server or roles are `null`
     */
    @Contract(pure = true)
    private fun getRoleModel(member: Member, roles: List<Role>): DiscordRoleModel {
        val discordRoles = getRoleModels(member.guild)
        val toModel = Function { role: Role -> discordRoles[role.id.value.toLong()] }
        val roleOptional = roles.parallelStream().filter(validateRole(discordRoles)).findFirst()
        return roleOptional.map(toModel).orElseThrow { NoNameSchemaWarning() }!!
    }

    /**
     * Retrieves the Discord role models for the specified server and converts them into a map.
     *
     *
     * The `getRoleModels` method retrieves the Discord role models associated with the provided server
     * using the [DiscordRoleConnection] and converts them into a map using the role IDs as keys.
     *
     * @param guild the Discord server for which to retrieve role models
     * @return a map of Discord role models with role IDs as keys
     * @throws NullPointerException if the specified server is `null`
     */
    @Contract(pure = true)
    private fun getRoleModels(guild: GuildBehavior): Map<Long, DiscordRoleModel> {
        return (DiscordRoleConnection[guild.id.value.toLong()].allRoles ?: emptyList())
            .stream().collect(toMap())
    }

    /**
     * Collects Discord role models into a map using role IDs as keys.
     *
     *
     * The `toMap` method returns a [Collector] that can be used to collect a stream of
     * [DiscordRoleModel] instances into a map using their role IDs as keys.
     *
     * @return a [Collector] that collects Discord role models into a map
     */
    @Contract(pure = true, value = "-> new")
    private fun toMap(): Collector<DiscordRoleModel, *, Map<Long, DiscordRoleModel>> {
        return Collectors.toMap(
            { obj: DiscordRoleModel -> obj.id },
            { discordRoleModel: DiscordRoleModel -> discordRoleModel }
        )
    }
}

//TODO move to connection?
fun User.getUUIDOrNull(): UUID? {
    return DiscordUserConnection.getById(id.value.toLong())?.minecraftId
}

@Throws(NotLinkedException::class)
fun User.getUUID(): UUID {
    return getUUIDOrNull() ?: throw NotLinkedException()
}

fun UUID.fetchIgn(): String {
    return MojangConnection.getNameByUUID(this)
}