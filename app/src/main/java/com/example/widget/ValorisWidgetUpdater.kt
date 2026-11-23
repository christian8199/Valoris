package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

object ValorisWidgetUpdater {
    private const val PREVIEW_REFRESH_MIN_INTERVAL_MS = 35L * 60L * 1000L

    @Volatile
    private var lastPreviewPublishElapsedMs: Long = 0L

    suspend fun updateAllWidgets(
        context: Context,
        isRefreshing: Boolean? = null,
        bumpDataRevision: Boolean = true
    ) {
        runCatching {
            val appContext = context.applicationContext
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(ValorisWidget::class.java)
            if (glanceIds.isEmpty()) return
            for (glanceId in glanceIds) {
                updateAppWidgetState(appContext, glanceId) { prefs ->
                    val nextUiRevision = (prefs[ValorisWidget.KEY_UI_REVISION] ?: 0) + 1
                    prefs[ValorisWidget.KEY_UI_REVISION] = nextUiRevision
                    if (bumpDataRevision) {
                        val nextDataRevision = (prefs[ValorisWidget.KEY_REVISION] ?: 0) + 1
                        prefs[ValorisWidget.KEY_REVISION] = nextDataRevision
                    }
                    if (isRefreshing != null) {
                        prefs[ValorisWidget.KEY_IS_REFRESHING] = isRefreshing
                    }
                }
            }
            ValorisWidget().updateAll(appContext)
        }
    }

    suspend fun refreshGeneratedPreviewIfNeeded(
        context: Context,
        force: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastPreviewPublishElapsedMs > 0L &&
            (now - lastPreviewPublishElapsedMs) < PREVIEW_REFRESH_MIN_INTERVAL_MS
        ) {
            return
        }
        runCatching {
            GlanceAppWidgetManager(context.applicationContext)
                .setWidgetPreviews(ValorisWidgetReceiver::class)
            lastPreviewPublishElapsedMs = now
        }
    }

    fun getPlacedWidgetCount(context: Context): Int {
        return runCatching {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            val provider = ComponentName(context.applicationContext, ValorisWidgetReceiver::class.java)
            manager.getAppWidgetIds(provider).size
        }.getOrDefault(0)
    }

    fun isPinWidgetSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            AppWidgetManager.getInstance(context.applicationContext).isRequestPinAppWidgetSupported
        }.getOrDefault(false)
    }

    fun requestPinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context.applicationContext, ValorisWidgetReceiver::class.java)
            manager.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
    }
}
