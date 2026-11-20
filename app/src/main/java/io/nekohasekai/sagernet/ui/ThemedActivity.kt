package io.nekohasekai.sagernet.ui

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.applyTopInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class ThemedActivity : AppCompatActivity {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    companion object {
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }

    var themeResId = 0
    var uiMode = 0
    open val isDialog = false

    private var lastUseSystemTheme: Boolean = false
    private var lastWallpaperColor: Int? = null
    private var lastAppTheme: Int = 0
    private var lastCustomThemeColor: Int = 0
    private var lastNightTheme: Int = 0
    private var lastAmoledTheme: Boolean = false
    private var lastUsingNight: Boolean = false
    private var wallpaperCheckJob: kotlinx.coroutines.Job? = null

    var lightStatusBar = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.applyNightTheme()
        lastUseSystemTheme = Theme.useSystemTheme()
        lastWallpaperColor = null
        lastAppTheme = Theme.appTheme()
        lastCustomThemeColor = Theme.customThemeColor()
        lastNightTheme = Theme.nightTheme()
        lastAmoledTheme = Theme.amoledTheme()
        lastUsingNight = Theme.usingNightMode(this)

        if (!isDialog) {
            Theme.apply(this)
        } else {
            Theme.applyDialog(this)
        }
        if (!isDialog) enableEdgeToEdge(statusBarStyle(), navigationBarStyle())

        super.onCreate(savedInstanceState)

        uiMode = resources.configuration.uiMode
    }

    private fun statusBarStyle(): SystemBarStyle {
        val isDark = Theme.isBlackTheme(this)
        val isLightSurface = !isDark && ColorUtils.calculateLuminance(getColorAttr(com.google.android.material.R.attr.colorSurface)) > 0.45
        lightStatusBar = isLightSurface
        return if (lightStatusBar) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
    }

    private fun navigationBarStyle(): SystemBarStyle {
        return if (Theme.isBlackTheme(this)) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { Theme.usingNightMode(this) }
        }
    }

    override fun onResume() {
        super.onResume()
        val currentUseSystemTheme = Theme.useSystemTheme()
        val currentAppTheme = Theme.appTheme()
        val currentCustomColor = Theme.customThemeColor()
        val currentNight = Theme.nightTheme()
        val currentAmoled = Theme.amoledTheme()
        val currentUsingNight = Theme.usingNightMode(this)
        if (lastUseSystemTheme != currentUseSystemTheme ||
            (!currentUseSystemTheme && lastAppTheme != currentAppTheme) ||
            (!currentUseSystemTheme && currentAppTheme == Theme.CUSTOM && lastCustomThemeColor != currentCustomColor) ||
            lastNightTheme != currentNight ||
            lastAmoledTheme != currentAmoled ||
            lastUsingNight != currentUsingNight
        ) {
            ActivityCompat.recreate(this)
            return
        }
        if (currentUseSystemTheme) {
            wallpaperCheckJob?.cancel()
            wallpaperCheckJob = lifecycleScope.launch {
                val currentWallpaperColor = withContext(Dispatchers.IO) {
                    Theme.getSystemWallpaperColor(applicationContext)
                }
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                    isFinishing ||
                    isDestroyed ||
                    !Theme.useSystemTheme()
                ) {
                    return@launch
                }
                val expectedWallpaper = lastWallpaperColor
                if (expectedWallpaper == null) {
                    lastWallpaperColor = currentWallpaperColor
                } else if (expectedWallpaper != currentWallpaperColor) {
                    ActivityCompat.recreate(this@ThemedActivity)
                }
            }
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        findViewById<AppBarLayout>(R.id.appbar)?.applyTopInset()
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        themeResId = resId
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode
            ActivityCompat.recreate(this)
        }
    }

    fun snackbar(@StringRes resId: Int): Snackbar = snackbar("").setText(resId)
    fun snackbar(text: CharSequence): Snackbar = snackbarInternal(text).apply {
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            maxLines = 10
        }
        if (Theme.isAnyAmoled(this@ThemedActivity) || Theme.isMonochromeBlack(this@ThemedActivity)) {
            val density = resources.displayMetrics.density
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(
                    if (Theme.isMonochromeBlack(this@ThemedActivity)) Color.parseColor("#121212")
                    else Theme.resolveNestedBoxBackground(this@ThemedActivity),
                )
                setStroke(
                    Theme.resolveCardStrokeWidthPx(this@ThemedActivity, selected = false).toInt().coerceAtLeast(1),
                    Theme.resolveCardStrokeColor(this@ThemedActivity, selected = false),
                )
            }
            view.background = bg
            setTextColor(Color.WHITE)
            setActionTextColor(
                if (Theme.isMonochromeBlack(this@ThemedActivity)) Color.WHITE
                else Theme.getSeedPrimaryColor(this@ThemedActivity),
            )
        }
    }

    internal open fun snackbarInternal(text: CharSequence): Snackbar =
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)

}
