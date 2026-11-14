package net.dungeonhub.application.misc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SuspendLazy<T>(private val block: suspend () -> T) {
    private object Uninitialized

    @Volatile
    private var result: Any? = Uninitialized
    private val mutex = Mutex()

    suspend fun get(): T {
        if (result !== Uninitialized) return result as T
        return mutex.withLock {
            if (result === Uninitialized) result = block()
            @Suppress("UNCHECKED_CAST")
            result as T
        }
    }
}

fun <T> suspendLazy(initializer: suspend () -> T) = SuspendLazy(initializer)