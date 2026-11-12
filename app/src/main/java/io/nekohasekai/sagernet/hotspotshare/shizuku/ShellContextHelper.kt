package io.nekohasekai.sagernet.hotspotshare.shizuku

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import java.lang.reflect.Field

/**
 * Creates and prepares a Context identifying as "com.android.shell" with UID 2000,
 * fixing TetheringService / ConnectivityService package-name & AttributionSource validation
 * on Android 12+ (API 31+).
 *
 * Rebases existing AttributionSource via `withPackageName("com.android.shell")` (as in shizzi)
 * so the system binder token on ContextImpl is preserved.
 * Fails fast with explicit IllegalStateException if reflection fails (no silent drops).
 */
object ShellContextHelper {

    const val SHELL_PACKAGE = "com.android.shell"
    const val SHELL_UID = 2000

    @JvmStatic
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    fun acquireSystemContext(): Context {
        val atClass = Class.forName("android.app.ActivityThread")
        val systemMainMethod = atClass.getMethod("systemMain")
        val activityThread = systemMainMethod.invoke(null)
            ?: throw IllegalStateException("ActivityThread.systemMain() returned null")
        val getSystemContextMethod = atClass.getMethod("getSystemContext")
        return (getSystemContextMethod.invoke(activityThread) as? Context)
            ?: throw IllegalStateException("ActivityThread.getSystemContext() returned null")
    }

    @JvmStatic
    fun createShellContext(baseContext: Context? = null): Context {
        val sysContext = baseContext ?: acquireSystemContext()
        val rebased: Context = try {
            sysContext.createPackageContext(SHELL_PACKAGE, 0) ?: sysContext
        } catch (_: Throwable) {
            sysContext
        }

        patchContextFields(rebased)
        return wrapContext(rebased)
    }

    @JvmStatic
    fun patchContextFields(context: Context) {
        var opPackagePatched = false
        var attributionSourcePatched = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

        var cls: Class<*>? = context.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                val f: Field = cls.getDeclaredField("mOpPackageName")
                f.isAccessible = true
                f.set(context, SHELL_PACKAGE)
                opPackagePatched = true
            } catch (_: NoSuchFieldException) {
            } catch (t: Throwable) {
                throw IllegalStateException("Failed to set mOpPackageName on ${cls.name}: ${t.message}", t)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val f: Field = cls.getDeclaredField("mAttributionSource")
                    f.isAccessible = true
                    val current = f.get(context)
                    val rebasedAttribution = if (current != null) {
                        try {
                            current.javaClass
                                .getMethod("withPackageName", String::class.java)
                                .invoke(current, SHELL_PACKAGE)
                        } catch (_: Throwable) {
                            AttributionSource.Builder(Process.myUid())
                                .setPackageName(SHELL_PACKAGE)
                                .build()
                        }
                    } else {
                        AttributionSource.Builder(Process.myUid())
                            .setPackageName(SHELL_PACKAGE)
                            .build()
                    }
                    f.set(context, rebasedAttribution)
                    attributionSourcePatched = true
                } catch (_: NoSuchFieldException) {
                } catch (t: Throwable) {
                    throw IllegalStateException("Failed to rebase mAttributionSource on ${cls.name}: ${t.message}", t)
                }
            }

            cls = cls.superclass
        }

        if (!opPackagePatched) {
            throw IllegalStateException("mOpPackageName field was not found in Context hierarchy: ${context.javaClass.name}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !attributionSourcePatched) {
            throw IllegalStateException("mAttributionSource field was not found in Context hierarchy: ${context.javaClass.name}")
        }
    }

    @JvmStatic
    fun wrapContext(base: Context): ContextWrapper {
        return object : ContextWrapper(base) {
            override fun getPackageName(): String = SHELL_PACKAGE
            override fun getOpPackageName(): String = SHELL_PACKAGE
            override fun getAttributionTag(): String? = null

            override fun getApplicationInfo(): ApplicationInfo {
                return ApplicationInfo(super.getApplicationInfo()).apply {
                    packageName = SHELL_PACKAGE
                    uid = Process.myUid()
                }
            }

            override fun getAttributionSource(): AttributionSource {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val current = super.getAttributionSource()
                        (current.javaClass
                            .getMethod("withPackageName", String::class.java)
                            .invoke(current, SHELL_PACKAGE) as? AttributionSource) ?: current
                    } catch (_: Throwable) {
                        AttributionSource.Builder(Process.myUid())
                            .setPackageName(SHELL_PACKAGE)
                            .build()
                    }
                } else {
                    super.getAttributionSource()
                }
            }
        }
    }

    @JvmStatic
    fun injectContextIntoTetheringManager(tetheringManager: Any, shellContext: Context) {
        var injected = false
        var cls: Class<*>? = tetheringManager.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                val f = cls.getDeclaredField("mContext")
                f.isAccessible = true
                f.set(tetheringManager, shellContext)
                injected = true
                break
            } catch (_: NoSuchFieldException) {
            } catch (t: Throwable) {
                throw IllegalStateException("Failed to inject mContext into ${cls.name}: ${t.message}", t)
            }
            cls = cls.superclass
        }
        if (!injected) {
            throw IllegalStateException("mContext field was not found in TetheringManager hierarchy (${tetheringManager.javaClass.name})")
        }
    }
}
