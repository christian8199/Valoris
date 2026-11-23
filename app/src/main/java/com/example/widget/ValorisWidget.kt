package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.R
import com.example.data.repository.PortfolioRepository
import com.example.data.repository.WidgetSourceData
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetDisplayMetric
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ValorisWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ValorisWidget()
}

private data class WidgetChartRenderKey(
    val points: List<Double>,
    val isPositive: Boolean,
    val isNegative: Boolean,
    val showZeroLine: Boolean
)

class ValorisWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_BUCKETS)

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(RESPONSIVE_BUCKETS)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = PortfolioRepository.getInstance(context)
        // Load fast local Room/DataStore snapshot first so provideContent renders immediately
        // instead of stalling on network history before leaving the initial loading layout.
        val fastDashboard = runCatching { repository.getDashboardSnapshot() }
            .getOrElse { emptyWidgetDashboard() }

        // Hoist mutable state, sync lock, and rendered chart bitmap outside provideContent so
        // SizeMode.Responsive buckets share one snapshot and one ~220 KB RGB_565 Bitmap instance.
        val sharedSourceState = mutableStateOf(
            WidgetSourceData(
                dashboard = fastDashboard,
                movement = null
            )
        )
        val syncMutex = Mutex()
        var isMovementLoaded = false
        var syncedDataRevision = -1
        var syncedUiRevision = -1
        var cachedChartKey: WidgetChartRenderKey? = null
        var cachedBitmap: Bitmap? = null

        provideContent {
            val prefs = currentState<Preferences>()
            val dataRevision = prefs[KEY_REVISION] ?: 0
            val uiRevision = prefs[KEY_UI_REVISION] ?: 0
            val isRefreshing = prefs[KEY_IS_REFRESHING] ?: false

            LaunchedEffect(dataRevision, uiRevision) {
                syncMutex.withLock {
                    val needsFullReload = !isMovementLoaded || dataRevision != syncedDataRevision
                    if (needsFullReload) {
                        isMovementLoaded = true
                        syncedDataRevision = dataRevision
                        syncedUiRevision = uiRevision
                        val loaded = runCatching {
                            repository.getWidgetSnapshotData(forceRefreshHistory = false)
                        }.getOrNull()
                        if (loaded != null) {
                            sharedSourceState.value = loaded
                        }
                    } else if (uiRevision != syncedUiRevision) {
                        syncedUiRevision = uiRevision
                        val updatedDashboard = runCatching {
                            repository.getDashboardSnapshot()
                        }.getOrNull()
                        if (updatedDashboard != null) {
                            sharedSourceState.value =
                                sharedSourceState.value.copy(dashboard = updatedDashboard)
                        }
                    }
                }
            }

            val currentSource = sharedSourceState.value
            val snapshot = buildWidgetSnapshot(
                dashboard = currentSource.dashboard,
                movement = currentSource.movement
            )
            val chartKey = WidgetChartRenderKey(
                points = snapshot.chartPoints,
                isPositive = snapshot.isPositive,
                isNegative = snapshot.isNegative,
                showZeroLine = snapshot.metricMode == WidgetDisplayMetric.TOTAL_RETURN
            )
            val backgroundBitmap = cachedBitmap?.takeIf { cachedChartKey == chartKey }
                ?: ValorisWidgetChartRenderer.renderBackgroundChart(
                    points = snapshot.chartPoints,
                    isPositive = snapshot.isPositive,
                    isNegative = snapshot.isNegative,
                    showZeroLine = snapshot.metricMode == WidgetDisplayMetric.TOTAL_RETURN
                ).also { rendered ->
                    cachedBitmap = rendered
                    cachedChartKey = chartKey
                }
            val size = LocalSize.current

            GlanceTheme {
                ValorisMinimalGraphWidget(
                    context = context,
                    snapshot = snapshot,
                    backgroundBitmap = backgroundBitmap,
                    size = size,
                    isRefreshing = isRefreshing
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val repository = PortfolioRepository.getInstance(context)
        val previewSource = runCatching { repository.getWidgetSnapshotData() }
            .getOrElse {
                WidgetSourceData(
                    dashboard = runCatching { repository.getDashboardSnapshot() }
                        .getOrElse { emptyWidgetDashboard() },
                    movement = null
                )
            }
        val snapshot = buildWidgetSnapshot(
            dashboard = previewSource.dashboard,
            movement = previewSource.movement
        )
        val previewBitmap = ValorisWidgetChartRenderer.renderBackgroundChart(
            points = snapshot.chartPoints,
            isPositive = snapshot.isPositive,
            isNegative = snapshot.isNegative,
            showZeroLine = snapshot.metricMode == WidgetDisplayMetric.TOTAL_RETURN
        )

        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                ValorisMinimalGraphWidget(
                    context = context,
                    snapshot = snapshot,
                    backgroundBitmap = previewBitmap,
                    size = size,
                    isRefreshing = false
                )
            }
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fallback = RemoteViews(context.packageName, R.layout.widget_valoris_error).apply {
            setOnClickPendingIntent(R.id.widget_error_root, pendingIntent)
        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }

    companion object {
        val KEY_REVISION = intPreferencesKey("valoris_widget_revision")
        val KEY_UI_REVISION = intPreferencesKey("valoris_widget_ui_revision")
        val KEY_IS_REFRESHING = booleanPreferencesKey("valoris_widget_is_refreshing")

        private val COMPACT_SIZE = DpSize(120.dp, 100.dp)
        private val MEDIUM_SIZE = DpSize(240.dp, 110.dp)
        private val EXPANDED_SIZE = DpSize(240.dp, 200.dp)

        private val RESPONSIVE_BUCKETS = setOf(
            COMPACT_SIZE,
            MEDIUM_SIZE,
            EXPANDED_SIZE
        )

        private fun emptyWidgetDashboard(): PortfolioDashboardData = PortfolioDashboardData(
            displayCurrency = SupportedCurrency.IDR,
            isBalanceHidden = false,
            refreshInterval = RefreshInterval.FIVE_MINUTES,
            themeMode = ThemeMode.SYSTEM,
            useCompactNumbers = false,
            showPnlOnCards = true,
            totalValueDisplay = 0.0,
            dayChangeDisplay = 0.0,
            dayChangePercent = 0.0,
            totalUnrealizedGainDisplay = null,
            totalUnrealizedGainPercent = null,
            valuedHoldings = emptyList(),
            allocationSlices = emptyList(),
            fxRatesList = emptyList(),
            usdToDisplayRate = 1.0,
            trendValuesDisplay = emptyList(),
            lastSyncEpoch = null,
            fxSourceLabel = "Cached",
            hasStaleQuotes = false
        )
    }
}

private object WidgetColors {
    val onHero = ColorProvider(day = Color(0xFFF6F3EB), night = Color(0xFFF6F3EB))
    val onHeroMuted = ColorProvider(day = Color(0xFFB7C4BD), night = Color(0xFFB7C4BD))
    val goldAccent = ColorProvider(day = Color(0xFFD4B259), night = Color(0xFFD4B259))
    val gain = ColorProvider(day = Color(0xFF3CCB88), night = Color(0xFF3CCB88))
    val loss = ColorProvider(day = Color(0xFFF06E6E), night = Color(0xFFF06E6E))
    val chipBg = ColorProvider(day = Color(0x9911241E), night = Color(0x9911241E))
}

@Composable
private fun ValorisMinimalGraphWidget(
    context: Context,
    snapshot: ValorisWidgetSnapshot,
    backgroundBitmap: Bitmap,
    size: DpSize,
    isRefreshing: Boolean
) {
    val isCompact = size.width < 200.dp
    val isTall = size.height >= 185.dp && !isCompact

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(R.dimen.valoris_widget_corner_radius)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        // Full-bleed background chart bitmap
        Image(
            provider = ImageProvider(backgroundBitmap),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )

        // Foreground clean overlay
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(if (isCompact) 12.dp else 16.dp)
        ) {
            // Top bar: Brand + Range pill + Action buttons
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(WidgetColors.chipBg)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isCompact) {
                            snapshot.rangeLabel
                        } else {
                            "VALORIS · ${snapshot.rangeLabel}"
                        },
                        style = TextStyle(
                            color = WidgetColors.goldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }

                if (!isCompact) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = if (isRefreshing) "Syncing…" else snapshot.syncStatusLabel,
                        style = TextStyle(
                            color = WidgetColors.onHeroMuted,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }

                if (!isCompact) {
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .cornerRadius(20.dp)
                            .clickable(actionRunCallback<ToggleWidgetPrivacyAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(30.dp)
                                .cornerRadius(15.dp)
                                .background(WidgetColors.chipBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(
                                    if (snapshot.isBalanceHidden) {
                                        R.drawable.ic_widget_visibility_off
                                    } else {
                                        R.drawable.ic_widget_visibility
                                    }
                                ),
                                contentDescription = context.getString(R.string.cd_widget_privacy),
                                colorFilter = ColorFilter.tint(WidgetColors.onHero),
                                modifier = GlanceModifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.width(2.dp))
                }

                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .cornerRadius(20.dp)
                        .clickable(actionRunCallback<RefreshValorisWidgetAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(30.dp)
                            .cornerRadius(15.dp)
                            .background(WidgetColors.chipBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_refresh),
                            contentDescription = context.getString(R.string.cd_widget_refresh),
                            colorFilter = ColorFilter.tint(
                                if (isRefreshing) WidgetColors.goldAccent else WidgetColors.onHero
                            ),
                            modifier = GlanceModifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Bottom primary metric & delta over full-bleed chart
            Text(
                text = if (isCompact) {
                    if (snapshot.metricMode == WidgetDisplayMetric.ASSET_VALUE) {
                        "NET WORTH"
                    } else {
                        "RETURN"
                    }
                } else {
                    "${snapshot.eyebrowLabel} · ${snapshot.currencyCode}"
                },
                style = TextStyle(
                    color = WidgetColors.onHeroMuted,
                    fontSize = if (isCompact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = snapshot.formattedPrimaryValue,
                style = TextStyle(
                    color = WidgetColors.onHero,
                    fontSize = when {
                        isCompact -> 18.sp
                        isTall -> 28.sp
                        else -> 24.sp
                    },
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(3.dp))

            Text(
                text = if (isCompact) {
                    snapshot.formattedCompactDelta
                } else {
                    snapshot.formattedSecondaryDelta
                },
                style = TextStyle(
                    color = when {
                        snapshot.isPositive -> WidgetColors.gain
                        snapshot.isNegative -> WidgetColors.loss
                        else -> WidgetColors.onHeroMuted
                    },
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}
