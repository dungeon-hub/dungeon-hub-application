package net.dungeonhub.application.misc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SuspendLazy<T>(private val block: suspend () -> T) {
    private var result: Any? = Unit
    private val mutex = Mutex()

    suspend fun get(): T {
        if (result != Unit) return result as T
        return mutex.withLock {
            if (result == Unit) result = block()
            @Suppress("UNCHECKED_CAST")
            result as T
        }
    }
}

fun <T> suspendLazy(initializer: suspend () -> T) = SuspendLazy(initializer)