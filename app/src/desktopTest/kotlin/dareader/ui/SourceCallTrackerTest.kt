package dareader.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceCallTrackerTest {

    @Test
    fun `awaitIdle blocks until the last in-flight call exits`() {
        val tracker = SourceCallTracker()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val waiterDone = CountDownLatch(1)

        val worker =
            Thread {
                tracker.enter()
                entered.countDown()
                release.await()
                tracker.exit()
            }
        val waiter =
            Thread {
                tracker.awaitIdle()
                waiterDone.countDown()
            }
        worker.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        waiter.start()

        assertFalse(waiterDone.await(200, TimeUnit.MILLISECONDS), "drain must wait for the in-flight call")

        release.countDown()
        assertTrue(waiterDone.await(5, TimeUnit.SECONDS), "drain must finish once the call exits")
        worker.join()
        waiter.join()
    }

    @Test
    fun `awaitIdle returns immediately with no calls in flight`() {
        val tracker = SourceCallTracker()
        tracker.awaitIdle()
    }
}
