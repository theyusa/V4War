package moe.matsuri.nb4a.net

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.Logs
import io.throneproj.mobile.ExchangeContext
import io.throneproj.mobile.LocalDNSTransport
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The core reads the answer once these calls return (each runs on a goroutine of its own, bounded by the query
 * context), so every call blocks until the resolver answers or the core cancels the query.
 */
object LocalResolverImpl : LocalDNSTransport {

    private const val RCODE_NXDOMAIN = 3
    internal const val EXCHANGE_TIMEOUT_MS = 15_000L

    // Dedicated bounded executor so heavy Dispatchers.IO consumers cannot starve core DNS callbacks.
    internal val dnsCallbackExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
        4,
        8,
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(256),
        object : ThreadFactory {
            private val nextId = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread =
                Thread(r, "LocalDnsCallback-${nextId.getAndIncrement()}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY + 1
                }
        },
        ThreadPoolExecutor.CallerRunsPolicy(),
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        val signal = CancellationSignal()
        awaitRawExchange(
            timeoutMs = EXCHANGE_TIMEOUT_MS,
            executor = dnsCallbackExecutor,
            registerCancel = { onCancel -> ctx.onCancel(onCancel) },
            cancelSignal = { signal.cancel() },
            onSuccess = { answer -> ctx.rawSuccess(answer) },
            onErrno = { errno -> ctx.errnoCode(errno) },
        ) { executor, completeSuccess, completeErrno ->
            val callback = object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    completeSuccess(answer)
                }

                override fun onError(error: DnsResolver.DnsException) {
                    val cause = error.cause
                    if (cause is ErrnoException) {
                        completeErrno(cause.errno)
                    } else {
                        Logs.w(error)
                        completeErrno(114514)
                    }
                }
            }
            DnsResolver.getInstance().rawQuery(
                SagerNet.underlyingNetwork,
                message,
                DnsResolver.FLAG_NO_RETRY,
                executor,
                signal,
                callback,
            )
        }
    }

    internal inline fun awaitRawExchange(
        timeoutMs: Long,
        executor: Executor,
        registerCancel: (() -> Unit) -> Unit,
        crossinline cancelSignal: () -> Unit,
        crossinline onSuccess: (ByteArray) -> Unit,
        crossinline onErrno: (Int) -> Unit,
        startQuery: (Executor, (ByteArray) -> Unit, (Int) -> Unit) -> Unit,
    ) {
        val done = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        // A cancelled DnsResolver query never calls back, so the cancellation releases the wait itself.
        registerCancel {
            if (completed.compareAndSet(false, true)) {
                runCatching { cancelSignal() }
                done.countDown()
            }
        }
        try {
            startQuery(
                executor,
                { answer ->
                    if (completed.compareAndSet(false, true)) {
                        try {
                            onSuccess(answer)
                        } finally {
                            done.countDown()
                        }
                    }
                },
                { errno ->
                    if (completed.compareAndSet(false, true)) {
                        try {
                            onErrno(errno)
                        } finally {
                            done.countDown()
                        }
                    }
                },
            )
        } catch (t: Throwable) {
            if (completed.compareAndSet(false, true)) {
                Logs.w(t)
                runCatching { onErrno(114514) }
                done.countDown()
            }
            return
        }
        val finished = try {
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished && completed.compareAndSet(false, true)) {
            runCatching { cancelSignal() }
            runCatching { onErrno(OsConstants.ETIMEDOUT) }
        }
    }

    /** Only below API 29, where [raw] is false. */
    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        try {
            val answer = try {
                SagerNet.underlyingNetwork?.getAllByName(domain)
            } catch (e: UnknownHostException) {
                null
            } ?: InetAddress.getAllByName(domain)
            ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n"))
        } catch (e: UnknownHostException) {
            ctx.errorCode(RCODE_NXDOMAIN)
        } catch (e: Exception) {
            Logs.w(e)
            ctx.errnoCode(114514)
        }
    }

}
