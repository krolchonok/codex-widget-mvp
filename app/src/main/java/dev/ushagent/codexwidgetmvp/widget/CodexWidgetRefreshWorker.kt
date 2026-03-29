package dev.ushagent.codexwidgetmvp.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ushagent.codexwidgetmvp.CodexPrefs
import dev.ushagent.codexwidgetmvp.CodexRepository
import dev.ushagent.codexwidgetmvp.DebugLog
import kotlinx.coroutines.delay

class CodexWidgetRefreshWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    DebugLog.d("WorkManager refresh started")
    val authJson = CodexPrefs.loadAuthJson(applicationContext)
    if (authJson.isBlank()) {
      DebugLog.d("WorkManager refresh skipped: auth.json empty")
      return Result.success()
    }

    return runCatching {
      val repository = CodexRepository()
      val loadingSnapshot = repository.buildRefreshingWidgetSnapshot(CodexPrefs.loadWidgetSnapshot(applicationContext))
      CodexPrefs.saveWidgetSnapshot(applicationContext, loadingSnapshot)
      CodexUsageWidgetUpdater.updateAll(applicationContext, loadingSnapshot)
      val startedAt = System.currentTimeMillis()
      val resolved = repository.resolveUsage(authJson)
      val elapsed = System.currentTimeMillis() - startedAt
      if (elapsed < 900L) {
        delay(900L - elapsed)
      }
      CodexPrefs.saveAuthJson(applicationContext, resolved.updatedAuthJson)
      val snapshot = repository.buildWidgetSnapshot(resolved.usage)
      CodexPrefs.saveWidgetSnapshot(applicationContext, snapshot)
      CodexUsageWidgetUpdater.updateAll(applicationContext, snapshot)
      DebugLog.d("WorkManager refresh finished successfully")
    }.fold(
      onSuccess = { Result.success() },
      onFailure = {
        DebugLog.e("WorkManager refresh failed", it)
        val repository = CodexRepository()
        val errorSnapshot = repository.buildErrorWidgetSnapshot(
          current = CodexPrefs.loadWidgetSnapshot(applicationContext),
          message = it.message ?: "Update failed",
        )
        CodexPrefs.saveWidgetSnapshot(applicationContext, errorSnapshot)
        kotlinx.coroutines.runBlocking {
          CodexUsageWidgetUpdater.updateAll(applicationContext, errorSnapshot)
        }
        Result.retry()
      },
    )
  }
}
