package net.dungeonhub.application.loader

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class OnStart(val order: Int = 100, val priority: StartPriority = StartPriority.DEFAULT)
