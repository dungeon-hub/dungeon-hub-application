package me.taubsie.dungeonhub.kord.application.exceptions

import lombok.NoArgsConstructor
import me.taubsie.dungeonhub.common.exceptions.ProgramException
import java.io.Serial

@NoArgsConstructor
class CommandExecutionException : ProgramException {
    constructor(s: String?) : super(s)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    companion object {
        @Serial
        private val serialVersionUID = 6707538877645992492L
    }
}