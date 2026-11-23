package com.example.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.PortfolioRepository
import kotlinx.coroutines.withTimeoutOrNull

class RefreshValorisWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        ValorisWidgetUpdater.updateAllWidgets(
            context = appContext,
            isRefreshing = true,
            bumpDataRevision = false
        )
        try {
            val repo = PortfolioRepository.getInstance(appContext)
            withTimeoutOrNull(8_500L) {
                repo.refreshAllMarketData()
                repo.getWidgetSnapshotData(forceRefreshHistory = true)
            }
        } finally {
            ValorisWidgetUpdater.updateAllWidgets(
                context = appContext,
                isRefreshing = false,
                bumpDataRevision = true
            )
        }
    }
}

class ToggleWidgetPrivacyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        UserPreferencesRepository(appContext).toggleBalanceHidden()
        ValorisWidgetUpdater.updateAllWidgets(
            context = appContext,
            bumpDataRevision = false
        )
    }
}
