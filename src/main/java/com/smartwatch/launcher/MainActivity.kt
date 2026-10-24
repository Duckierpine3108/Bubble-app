package com.smartwatch.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.smartwatch.launcher.model.AppInfo
import com.smartwatch.launcher.model.BitmapUtils
import com.smartwatch.launcher.ui.BubbleCanvasView
import com.smartwatch.launcher.ui.WatchFaceContainerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var bubbleCanvasView: BubbleCanvasView
    private lateinit var watchFaceContainerView: WatchFaceContainerView

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadInstalledApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureFullScreenWatchMode()
        setContentView(R.layout.activity_main)

        bubbleCanvasView = findViewById(R.id.bubbleCanvasView)
        watchFaceContainerView = findViewById(R.id.watchFaceContainerView)

        // Ensure Watch Face starts in front
        watchFaceContainerView.bringToFront()
        watchFaceContainerView.visibility = View.VISIBLE

        // Hardware Back Button: Exits customizer mode or returns to clock
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    watchFaceContainerView.isCustomizerMode -> {
                        watchFaceContainerView.exitCustomizerMode(save = false)
                    }
                    watchFaceContainerView.visibility == View.GONE -> {
                        watchFaceContainerView.showClock()
                    }
                    else -> {
                        bubbleCanvasView.resetToCenter(animated = true)
                    }
                }
            }
        })

        registerPackageReceiver()
        loadInstalledApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Hardware Home/Crown Button returns to Watch Face
        if (watchFaceContainerView.isCustomizerMode) {
            watchFaceContainerView.exitCustomizerMode(save = false)
        }
        watchFaceContainerView.showClock()
        bubbleCanvasView.resetToCenter(animated = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageChangeReceiver)
    }

    private fun configureFullScreenWatchMode() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, filter)
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.Default) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }

            val iconPixelSize = (resources.displayMetrics.density * 64).toInt().coerceAtLeast(96)

            val appList = resolveInfos
                .filter { it.activityInfo.packageName != packageName }
                .map { resolveInfo ->
                    val label = resolveInfo.loadLabel(pm).toString()
                    val pkgName = resolveInfo.activityInfo.packageName
                    val actName = resolveInfo.activityInfo.name
                    val rawDrawable = resolveInfo.loadIcon(pm)
                    val circularIcon = BitmapUtils.drawableToCircularBitmap(rawDrawable, iconPixelSize)

                    AppInfo(
                        label = label,
                        packageName = pkgName,
                        activityName = actName,
                        iconBitmap = circularIcon
                    )
                }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                bubbleCanvasView.setApps(appList)
            }
        }
    }
}