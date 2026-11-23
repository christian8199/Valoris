package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetDisplayMetric
import com.example.ui.theme.ValorisTheme
import kotlinx.coroutines.launch

class ValorisWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)
        val prefsRepo = UserPreferencesRepository(this)

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsState(initial = UserPreferences())
            val useDark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var selectedMetric by remember(prefs.widgetDisplayMetric) {
                mutableStateOf(prefs.widgetDisplayMetric)
            }
            var selectedTimeframe by remember(prefs.widgetChartTimeframe) {
                mutableStateOf(prefs.widgetChartTimeframe)
            }
            var isSaving by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            ValorisTheme(darkTheme = useDark) {
                WidgetConfigScreen(
                    selectedMetric = selectedMetric,
                    selectedTimeframe = selectedTimeframe,
                    isSaving = isSaving,
                    onSelectMetric = { selectedMetric = it },
                    onSelectTimeframe = { selectedTimeframe = it },
                    onSave = {
                        if (isSaving) return@WidgetConfigScreen
                        isSaving = true
                        scope.launch {
                            prefsRepo.setWidgetDisplayMetric(selectedMetric)
                            prefsRepo.setWidgetChartTimeframe(selectedTimeframe)
                            ValorisWidgetUpdater.updateAllWidgets(applicationContext)
                            ValorisWidgetUpdater.refreshGeneratedPreviewIfNeeded(applicationContext)
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                val resultIntent = Intent().putExtra(
                                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                                    appWidgetId
                                )
                                setResult(RESULT_OK, resultIntent)
                            } else {
                                setResult(RESULT_OK)
                            }
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigScreen(
    selectedMetric: WidgetDisplayMetric,
    selectedTimeframe: ChartTimeframe,
    isSaving: Boolean,
    onSelectMetric: (WidgetDisplayMetric) -> Unit,
    onSelectTimeframe: (ChartTimeframe) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Widget Settings",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Choose the primary metric and chart range shown on your Home Screen widget.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Displayed Metric",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetDisplayMetric.entries.forEach { metric ->
                            val selected = selectedMetric == metric
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectMetric(metric) },
                                label = { Text(metric.label) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("widget_config_metric_${metric.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Graph Range",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChartTimeframe.entries.forEach { timeframe ->
                            val selected = selectedTimeframe == timeframe
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTimeframe(timeframe) },
                                label = { Text(timeframe.label) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("widget_config_range_${timeframe.label.lowercase()}")
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(48.dp)
                    .testTag("widget_config_save_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (isSaving) "Applying…" else "Save Widget Settings")
            }
        }
    }
}
