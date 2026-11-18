package io.nekohasekai.sagernet.group

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteListenableWorker
import androidx.work.multiprocess.RemoteWorkManager
import androidx.work.multiprocess.RemoteWorkerService
import com.google.common.util.concurrent.ListenableFuture
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupRepo
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class PeriodicScheduleAction {
    SKIP_CANCEL,
    CANCEL,
    ENQUEUE_KEEP,
    ENQUEUE_UPDATE,
}

internal data class PeriodicWorkSnapshot(
    val state: WorkInfo.State,
    val tags: Set<String>,
)

/**
 * Decides whether a unique periodic WorkManager job should be kept, updated, or cancelled based on a stable
 * configuration signature tag rather than volatile startup initialDelay values.
 */
internal object PeriodicSchedulePolicy {
    private const val SIGNATURE_TAG_PREFIX = "sig:v1:"

    fun signatureTag(periodSeconds: Long, networkType: NetworkType = NetworkType.CONNECTED): String =
        "${SIGNATURE_TAG_PREFIX}period=$periodSeconds:net=${networkType.name}"

    fun decide(
        signatureTag: String?,
        existingWork: List<PeriodicWorkSnapshot>,
    ): PeriodicScheduleAction {
        val active = existingWork.filter { !it.state.isFinished }
        if (signatureTag == null) {
            return if (active.isEmpty()) PeriodicScheduleAction.SKIP_CANCEL else PeriodicScheduleAction.CANCEL
        }
        if (active.isEmpty()) {
            return PeriodicScheduleAction.ENQUEUE_KEEP
        }
        return if (active.all { signatureTag in it.tags }) {
            PeriodicScheduleAction.ENQUEUE_KEEP
        } else {
            PeriodicScheduleAction.ENQUEUE_UPDATE
        }
    }
}

/**
 * The automatic subscription update (GroupUpdater::CheckAutoUpdate on PeriodicRunner, mainwindow_setup.cpp): each
 * group follows its own [interval] and is due that long after its last successful update or automatic attempt.
 * WorkManager polls at the common period of those intervals and the worker queues the due groups in :bg. Anything
 * [interval] reads changing calls [schedule]; a poll that finds the plan changed reschedules itself.
 *
 * WorkManager honours the initial delay only on the first enqueue, so the polls keep their own grid while manual
 * refreshes move the due times off it: a poll takes every group due before the middle of the next period, which never
 * skips a cycle and starts a group at most half a period early.
 */
object SubscriptionScheduler {

    const val MIN_INTERVAL_MINUTES = 30

    private const val WORK_NAME = "SubscriptionScheduler"

    /** The per-group updater of v11 and older, whose worker class is gone. */
    private const val LEGACY_WORK_NAME = "SubscriptionUpdater"

    private const val KEY_PERIOD = "period"

    /** autoAttempts (GroupUpdater.hpp): in :bg memory, so a new :bg process retries a failing group once. */
    private val attempts = ConcurrentHashMap<Long, Long>()
    private val scheduleMutex = Mutex()
    private val legacyCleanupPending = AtomicBoolean(true)
    private val activeUpdateTasks = AtomicInteger(0)

    /**
     * ResolveAutoUpdate (GroupUpdater.cpp:429-440) in seconds, 0 = not updated automatically: the server's
     * profile-update-interval when followed and sent, else the group's interval, else sub_auto_update. Nothing is
     * automatic while sub_auto_update is off.
     */
    fun interval(
        group: ProxyGroup,
        global: Int = DataStore.subAutoUpdate,
        respectServer: Boolean = DataStore.subRespectServerInterval,
    ): Long {
        if (global < MIN_INTERVAL_MINUTES || group.url.isEmpty() || group.archive || group.skipAutoUpdate) return 0L
        val options = group.subOptions
        val serverHours = group.subInfo.serverInterval
        if ((options.respectServerInterval ?: respectServer) && serverHours > 0) return serverHours * 3600L
        if (options.updateInterval > 0) return maxOf(options.updateInterval, MIN_INTERVAL_MINUTES) * 60L
        return global * 60L
    }

    /** Whether [group]'s automatic update follows the server's interval when it sends one. */
    fun followsServerInterval(group: ProxyGroup): Boolean =
        group.subOptions.respectServerInterval ?: DataStore.subRespectServerInterval

    /** autoUpdateDue (GroupUpdater.cpp:519-523): a failed attempt waits the interval too. */
    private fun due(group: ProxyGroup, interval: Long): Long {
        val last = maxOf(group.subLastUpdate, attempts[group.id] ?: 0L)
        return if (last <= 0) 0 else last + interval
    }

    private class Plan(val period: Long, val initialDelay: Long)

    /** The poll period is the intervals' greatest common divisor (at least [MIN_INTERVAL_MINUTES]); null = no poll. */
    private fun plan(now: Long): Plan? {
        val global = DataStore.subAutoUpdate
        if (global < MIN_INTERVAL_MINUTES) return null
        val respectServer = DataStore.subRespectServerInterval
        var period = 0L
        var next = Long.MAX_VALUE
        for (group in GroupRepo.all()) {
            val interval = interval(group, global, respectServer)
            if (interval <= 0) continue
            period = gcd(period, interval)
            next = minOf(next, due(group, interval))
        }
        if (period <= 0) return null
        period = period.coerceAtLeast(MIN_INTERVAL_MINUTES * 60L)
        return Plan(period, (next - now).coerceIn(0L, period))
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    /**
     * Plans the periodic poll from the groups and the settings, or cancels it while nothing is updated automatically.
     * Keeps an active periodic job untouched when its period/constraints signature has not changed, and updates it
     * only when the effective schedule changes.
     */
    fun schedule() {
        if (activeUpdateTasks.get() > 0) return
        runOnDefaultDispatcher {
            if (activeUpdateTasks.get() > 0) return@runOnDefaultDispatcher
            scheduleMutex.withLock {
                if (activeUpdateTasks.get() > 0) return@withLock
                try {
                    val workManager = RemoteWorkManager.getInstance(app)
                    if (legacyCleanupPending.compareAndSet(true, false)) {
                        val legacyInfos = workManager.getWorkInfos(
                            WorkQuery.fromUniqueWorkNames(LEGACY_WORK_NAME)
                        ).await()
                        if (legacyInfos.any { !it.state.isFinished }) {
                            workManager.cancelUniqueWork(LEGACY_WORK_NAME).await()
                        }
                    }
                    val plan = plan(nowSeconds())
                    val signatureTag = plan?.let { PeriodicSchedulePolicy.signatureTag(it.period, NetworkType.CONNECTED) }
                    val existing = workManager.getWorkInfos(WorkQuery.fromUniqueWorkNames(WORK_NAME)).await()
                        .map { PeriodicWorkSnapshot(it.state, it.tags) }
                    val action = PeriodicSchedulePolicy.decide(signatureTag, existing)
                    when (action) {
                        PeriodicScheduleAction.SKIP_CANCEL -> return@withLock
                        PeriodicScheduleAction.CANCEL -> {
                            workManager.cancelUniqueWork(WORK_NAME).await()
                        }
                        PeriodicScheduleAction.ENQUEUE_KEEP,
                        PeriodicScheduleAction.ENQUEUE_UPDATE -> {
                            val activePlan = plan!!
                            val tag = signatureTag!!
                            val request = PeriodicWorkRequest.Builder(UpdateTask::class.java, activePlan.period, TimeUnit.SECONDS)
                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .setInitialDelay(activePlan.initialDelay, TimeUnit.SECONDS)
                                .addTag(tag)
                                .setInputData(
                                    // The worker runs in the :bg process, where the queue lives.
                                    Data.Builder()
                                        .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, app.packageName)
                                        .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, RemoteWorkerService::class.java.name)
                                        .putLong(KEY_PERIOD, activePlan.period)
                                        .build()
                                )
                                .build()
                            val policy = if (action == PeriodicScheduleAction.ENQUEUE_KEEP) {
                                ExistingPeriodicWorkPolicy.KEEP
                            } else {
                                ExistingPeriodicWorkPolicy.UPDATE
                            }
                            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request).await()
                        }
                    }
                } catch (e: Throwable) {
                    Logs.w("SubscriptionScheduler: scheduling failed", e)
                }
            }
        }
    }

    /**
     * CheckAutoUpdate (GroupUpdater.cpp:525-535) for a poll every [period] seconds: queues every group due before the
     * middle of the next period in tab order; returns how many.
     */
    private fun checkAutoUpdate(period: Long): Int {
        val now = nowSeconds()
        val horizon = now + period / 2
        val global = DataStore.subAutoUpdate
        val respectServer = DataStore.subRespectServerInterval
        var queued = 0
        for (group in GroupRepo.all()) {
            val interval = interval(group, global, respectServer)
            if (interval <= 0 || due(group, interval) > horizon) continue
            attempts[group.id] = now
            Logs.i(app.getString(R.string.subs_auto_update_running, group.displayName()))
            // Failures go to the log only (notifyErrors is off for a background refresh).
            SubscriptionQueue.refreshGroup(group.id, showDiff = false, foreground = false)
            queued++
        }
        return queued
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, { it.run() })
    }

    class UpdateTask(appContext: Context, params: WorkerParameters) : RemoteCoroutineWorker(appContext, params) {
        override suspend fun doRemoteWork(): Result {
            val period = inputData.getLong(KEY_PERIOD, 0L)
            activeUpdateTasks.incrementAndGet()
            try {
                if (checkAutoUpdate(period.takeIf { it > 0 } ?: (MIN_INTERVAL_MINUTES * 60L)) > 0) {
                    SubscriptionQueue.awaitIdle()
                }
            } finally {
                activeUpdateTasks.decrementAndGet()
            }
            // The plan moved without a reschedule (a restored backup, a server interval, a removed group): follow it.
            if (plan(nowSeconds())?.period != period) schedule()
            return Result.success()
        }
    }
}
