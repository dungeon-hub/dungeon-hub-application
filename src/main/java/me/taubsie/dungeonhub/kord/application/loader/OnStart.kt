package me.taubsie.dungeonhub.kord.application.loader

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class OnStart(val priority: Int = 100)
