package me.taubsie.dungeonhub.application.exceptions

import me.taubsie.dungeonhub.common.exceptions.ProgramException
import java.io.Serial

open class CommandExecutionException : ProgramException {
    constructor() : super()

    constructor(s: String?) : super(s)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    companion object {
        @Serial
        private val serialVersionUID = 6707538877645992492L
    }
}