package io.nekohasekai.sagernet.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import io.nekohasekai.sagernet.database.DataStore

/**
 * Centralized app-wide haptic feedback engine respecting [DataStore.hapticEnabled]
 * and [DataStore.hapticStrength] (`1 = Light`, `2 = Medium`, `3 = Strong`).
 */
object HapticHelper {

    enum class HapticKind {
        TAP,
        TOGGLE,
        SELECTION,
        HEAVY,
    }

    const val STRENGTH_LIGHT = 1
    const val STRENGTH_MEDIUM = 2
    const val STRENGTH_STRONG = 3

    private const val TAG_HAPTIC_KIND = 0x56415711

    @JvmStatic
    @JvmOverloads
    fun perform(
        context: Context?,
        view: View? = null,
        kind: HapticKind = HapticKind.TAP,
        forceStrength: Int? = null,
    ) {
        if (forceStrength == null && !DataStore.hapticEnabled) return
        val strength = (forceStrength ?: DataStore.hapticStrength).coerceIn(STRENGTH_LIGHT, STRENGTH_STRONG)
        val ctx = context ?: view?.context ?: return

        val (durationMs, amplitude) = resolvePulse(kind, strength)
        val vibrator = resolveVibrator(ctx)
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amp = if (vibrator.hasAmplitudeControl()) {
                        amplitude
                    } else {
                        VibrationEffect.DEFAULT_AMPLITUDE
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amp))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
                return
            } catch (_: SecurityException) {
                // Fall back to view haptic feedback if VIBRATE is unavailable.
            } catch (_: Exception) {
                // Ignore transient platform vibrator errors.
            }
        }
        performViewFallback(view, kind)
    }

    /**
     * Attaches a non-intrusive `ACTION_DOWN` haptic trigger without replacing existing click listeners.
     */
    @JvmStatic
    @JvmOverloads
    fun attachTapHaptic(view: View?, kind: HapticKind = HapticKind.TAP) {
        view ?: return
        if (view.getTag(TAG_HAPTIC_KIND) == kind) return
        view.setTag(TAG_HAPTIC_KIND, kind)
        view.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                perform(v.context, v, kind)
            }
            false
        }
    }

    private fun resolvePulse(kind: HapticKind, strength: Int): Pair<Long, Int> {
        val idx = strength - 1
        return when (kind) {
            HapticKind.SELECTION -> longArrayOf(8L, 14L, 22L)[idx] to intArrayOf(48, 96, 165)[idx]
            HapticKind.TAP -> longArrayOf(10L, 17L, 26L)[idx] to intArrayOf(58, 115, 190)[idx]
            HapticKind.TOGGLE -> longArrayOf(14L, 24L, 36L)[idx] to intArrayOf(75, 145, 225)[idx]
            HapticKind.HEAVY -> longArrayOf(20L, 34L, 52L)[idx] to intArrayOf(105, 185, 255)[idx]
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun performViewFallback(view: View?, kind: HapticKind) {
        view ?: return
        val constant = when (kind) {
            HapticKind.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
            HapticKind.TAP -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticKind.TOGGLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            HapticKind.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
