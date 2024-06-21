package me.taubsie.dungeonhub.kord.application.exceptions

open class FailedToLoadException : RuntimeException {
    constructor(throwable: Throwable?) : super(throwable)

    constructor(message: String?) : super(message)
}