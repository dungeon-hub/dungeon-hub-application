package me.taubsie.dungeonhub.application.exceptions

import java.io.Serial

open class CommandExecutionWarning : IllegalArgumentException {
    constructor() : super()

    constructor(s: String?) : super(s)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    companion object {
        @Serial
        private val serialVersionUID: Long = 5914280245082820310L
    }
}