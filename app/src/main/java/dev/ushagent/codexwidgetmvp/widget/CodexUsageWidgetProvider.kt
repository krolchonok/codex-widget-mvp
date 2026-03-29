package dev.ushagent.codexwidgetmvp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.ushagent.codexwidgetmvp.CodexPrefs
import dev.ushagent.codexwidgetmvp.CodexRepository
import dev.ushagent.codexwidgetmvp.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CodexUsageWidgetProvider : AppWidgetProvider() {
  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH_WIDGET) {
      DebugLog.d("Widget provider refresh broadcast received")
      refreshNow(context)
    }
  }

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    CodexWidgetWorkScheduler.ensureScheduled(context)
    refreshNow(context)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    CoroutineScope(Dispatchers.IO).launch {
      CodexUsageWidgetUpdater.updateAll(context.applicationContext, CodexPrefs.loadWidgetSnapshot(context.applicationContext))
    }
  }

  private fun refreshNow(context: Context) {
    val appContext = context.applicationContext
    val currentSnapshot = CodexPrefs.loadWidgetSnapshot(appContext)
    if (currentSnapshot.isRefreshing) {
      DebugLog.d("Ignoring widget tap while refresh is already in progress")
      return
    }

    val authJson = CodexPrefs.loadAuthJson(appContext)
    if (authJson.isBlank()) {
      CoroutineScope(Dispatchers.IO).launch {
        CodexUsageWidgetUpdater.updateAll(appContext, currentSnapshot)
      }
      return
    }

    CoroutineScope(Dispatchers.IO).launch {
      val repository = CodexRepository()
      try {
        val loadingSnapshot = repository.buildRefreshingWidgetSnapshot(currentSnapshot)
        CodexPrefs.saveWidgetSnapshot(appContext, loadingSnapshot)
        CodexUsageWidgetUpdater.updateAll(appContext, loadingSnapshot)

        val resolved = repository.resolveUsage(authJson)
        CodexPrefs.saveAuthJson(appContext, resolved.updatedAuthJson)
        val snapshot = repository.buildWidgetSnapshot(resolved.usage)
        CodexPrefs.saveWidgetSnapshot(appContext, snapshot)
        CodexUsageWidgetUpdater.updateAll(appContext, snapshot)
      } catch (t: Throwable) {
        DebugLog.e("Widget provider refresh failed", t)
        val errorSnapshot = repository.buildErrorWidgetSnapshot(
          current = CodexPrefs.loadWidgetSnapshot(appContext),
          message = t.message ?: "Update failed",
        )
        CodexPrefs.saveWidgetSnapshot(appContext, errorSnapshot)
        CodexUsageWidgetUpdater.updateAll(appContext, errorSnapshot)
      }
    }
  }

  companion object {
    const val ACTION_REFRESH_WIDGET = "dev.ushagent.codexwidgetmvp.action.REFRESH_WIDGET"
  }
}
