package dev.ushagent.codexwidgetmvp.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.ushagent.codexwidgetmvp.DebugLog
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "codex_widget_periodic_refresh"
private const val IMMEDIATE_WORK_NAME = "codex_widget_immediate_refresh"

object CodexWidgetWorkScheduler {
  fun ensureScheduled(context: Context) {
    DebugLog.d("Scheduling periodic widget refresh")
    val request = PeriodicWorkRequestBuilder<CodexWidgetRefreshWorker>(30, TimeUnit.MINUTES)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun enqueueImmediateRefresh(context: Context) {
    DebugLog.d("Scheduling immediate widget refresh")
    val request = OneTimeWorkRequestBuilder<CodexWidgetRefreshWorker>().build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      IMMEDIATE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }
}
