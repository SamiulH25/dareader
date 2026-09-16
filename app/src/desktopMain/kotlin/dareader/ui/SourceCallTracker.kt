package dareader.ui

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Counts in-flight source calls so handle close/uninstall can drain first:
 * a fetch must never observe a classloader closed underneath it, so closing
 * waits for [awaitIdle] instead of racing the fetch.
 */
internal class SourceCallTracker {
    private val lock = ReentrantLock()
    private val idle = lock.newCondition()
    private var active = 0

    fun enter() {
        lock.withLock { active++ }
    }

    fun exit() {
        lock.withLock {
            active--
            if (active == 0) idle.signalAll()
        }
    }

    /** Blocks until no calls are in flight; call off the UI thread. */
    fun awaitIdle() {
        lock.withLock {
            while (active > 0) idle.await()
        }
    }
}
