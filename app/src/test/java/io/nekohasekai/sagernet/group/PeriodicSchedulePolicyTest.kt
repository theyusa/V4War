package io.nekohasekai.sagernet.group

import androidx.work.NetworkType
import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PeriodicSchedulePolicyTest {

    @Test
    fun disabledScheduleSkipsCancelWhenNoActiveWorkAndCancelsActiveWork() {
        assertEquals(
            PeriodicScheduleAction.SKIP_CANCEL,
            PeriodicSchedulePolicy.decide(null, emptyList()),
        )
        assertEquals(
            PeriodicScheduleAction.SKIP_CANCEL,
            PeriodicSchedulePolicy.decide(
                null,
                listOf(
                    PeriodicWorkSnapshot(WorkInfo.State.CANCELLED, setOf("sig:v1:period=1800:net=CONNECTED")),
                    PeriodicWorkSnapshot(WorkInfo.State.SUCCEEDED, emptySet()),
                ),
            ),
        )
        assertEquals(
            PeriodicScheduleAction.CANCEL,
            PeriodicSchedulePolicy.decide(
                null,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.ENQUEUED, emptySet())),
            ),
        )
        assertEquals(
            PeriodicScheduleAction.CANCEL,
            PeriodicSchedulePolicy.decide(
                null,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.RUNNING, emptySet())),
            ),
        )
    }

    @Test
    fun matchingActiveWorkUsesKeepWhileChangedOrLegacyWorkUsesUpdate() {
        val sig30 = PeriodicSchedulePolicy.signatureTag(1800L, NetworkType.CONNECTED)
        val sig60 = PeriodicSchedulePolicy.signatureTag(3600L, NetworkType.CONNECTED)
        assertNotEquals(sig30, sig60)

        // Fresh enqueue (no active work) uses KEEP.
        assertEquals(
            PeriodicScheduleAction.ENQUEUE_KEEP,
            PeriodicSchedulePolicy.decide(sig30, emptyList()),
        )
        assertEquals(
            PeriodicScheduleAction.ENQUEUE_KEEP,
            PeriodicSchedulePolicy.decide(
                sig30,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.CANCELLED, setOf(sig60))),
            ),
        )

        // Existing ENQUEUED or RUNNING work with matching signature keeps the schedule untouched.
        assertEquals(
            PeriodicScheduleAction.ENQUEUE_KEEP,
            PeriodicSchedulePolicy.decide(
                sig30,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.ENQUEUED, setOf("SubscriptionScheduler", sig30))),
            ),
        )
        assertEquals(
            PeriodicScheduleAction.ENQUEUE_KEEP,
            PeriodicSchedulePolicy.decide(
                sig30,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.RUNNING, setOf("SubscriptionScheduler", sig30))),
            ),
        )

        // Interval/constraint mismatch or untagged legacy work triggers UPDATE.
        assertEquals(
            PeriodicScheduleAction.ENQUEUE_UPDATE,
            PeriodicSchedulePolicy.decide(
                sig60,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.ENQUEUED, setOf("SubscriptionScheduler", sig30))),
            ),
        )
        assertEquals(
            PeriodicScheduleAction.ENQUEUE_UPDATE,
            PeriodicSchedulePolicy.decide(
                sig30,
                listOf(PeriodicWorkSnapshot(WorkInfo.State.ENQUEUED, setOf("SubscriptionScheduler"))),
            ),
        )
    }
}
