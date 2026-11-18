package moe.matsuri.nb4a.net

import android.system.OsConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LocalResolverImplTest {

    @Test
    fun dedicatedExecutorRunsOnLocalDnsCallbackThreads() {
        val threadName = AtomicReference<String>()
        val latch = CountDownLatch(1)
        LocalResolverImpl.dnsCallbackExecutor.execute {
            threadName.set(Thread.currentThread().name)
            latch.countDown()
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(
            "Expected dedicated LocalDnsCallback thread, got ${threadName.get()}",
            threadName.get().orEmpty().startsWith("LocalDnsCallback-"),
        )
    }

    @Test
    fun awaitRawExchangeCompletesOnceWithAnswerOnDedicatedExecutor() {
        val successCount = AtomicInteger(0)
        val errnoCount = AtomicInteger(0)
        val payload = AtomicReference<ByteArray>()

        LocalResolverImpl.awaitRawExchange(
            timeoutMs = 1000L,
            executor = LocalResolverImpl.dnsCallbackExecutor,
            registerCancel = {},
            cancelSignal = {},
            onSuccess = {
                successCount.incrementAndGet()
                payload.set(it)
            },
            onErrno = { errnoCount.incrementAndGet() },
        ) { executor, completeSuccess, _ ->
            executor.execute {
                completeSuccess(byteArrayOf(1, 2, 3))
                completeSuccess(byteArrayOf(4, 5, 6))
            }
        }

        assertEquals(1, successCount.get())
        assertEquals(0, errnoCount.get())
        assertArrayEquals(byteArrayOf(1, 2, 3), payload.get())
    }

    @Test
    fun awaitRawExchangeCancelSuppressesLateAnswer() {
        val successCount = AtomicInteger(0)
        val errnoCount = AtomicInteger(0)
        val cancelCalls = AtomicInteger(0)
        val cancelRef = AtomicReference<() -> Unit>()

        LocalResolverImpl.awaitRawExchange(
            timeoutMs = 1000L,
            executor = LocalResolverImpl.dnsCallbackExecutor,
            registerCancel = { cancelRef.set(it) },
            cancelSignal = { cancelCalls.incrementAndGet() },
            onSuccess = { successCount.incrementAndGet() },
            onErrno = { errnoCount.incrementAndGet() },
        ) { executor, completeSuccess, _ ->
            cancelRef.get().invoke()
            executor.execute { completeSuccess(byteArrayOf(9)) }
        }

        assertEquals(1, cancelCalls.get())
        assertEquals(0, successCount.get())
        assertEquals(0, errnoCount.get())
    }

    @Test
    fun awaitRawExchangeTimesOutWhenResolverNeverResponds() {
        val cancelCalls = AtomicInteger(0)
        val errno = AtomicInteger(0)

        LocalResolverImpl.awaitRawExchange(
            timeoutMs = 30L,
            executor = LocalResolverImpl.dnsCallbackExecutor,
            registerCancel = {},
            cancelSignal = { cancelCalls.incrementAndGet() },
            onSuccess = {},
            onErrno = { errno.set(it) },
        ) { _, _, _ -> }

        assertEquals(1, cancelCalls.get())
        assertEquals(OsConstants.ETIMEDOUT, errno.get())
    }

    @Test
    fun awaitRawExchangeHandlesSynchronousStartFailure() {
        val errno = AtomicInteger(0)

        LocalResolverImpl.awaitRawExchange(
            timeoutMs = 1000L,
            executor = LocalResolverImpl.dnsCallbackExecutor,
            registerCancel = {},
            cancelSignal = {},
            onSuccess = {},
            onErrno = { errno.set(it) },
        ) { _, _, _ ->
            throw IllegalStateException("boom")
        }

        assertEquals(114514, errno.get())
    }
}
