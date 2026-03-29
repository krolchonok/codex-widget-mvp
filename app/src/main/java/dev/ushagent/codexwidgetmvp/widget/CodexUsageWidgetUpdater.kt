package dev.ushagent.codexwidgetmvp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import dev.ushagent.codexwidgetmvp.DebugLog
import dev.ushagent.codexwidgetmvp.R
import dev.ushagent.codexwidgetmvp.WidgetSnapshot

object CodexUsageWidgetUpdater {
  suspend fun updateAll(context: Context, snapshot: WidgetSnapshot) {
    DebugLog.d(
      "Updating widget UI: primary=${snapshot.primaryValueText}, " +
        "secondary=${snapshot.secondaryValueText}, refreshing=${snapshot.isRefreshing}, updated=${snapshot.updatedText}"
    )

    val refreshIntent = Intent(context, CodexUsageWidgetProvider::class.java).apply {
      action = CodexUsageWidgetProvider.ACTION_REFRESH_WIDGET
    }
    val refreshPendingIntent = PendingIntent.getBroadcast(
      context,
      1001,
      refreshIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, CodexUsageWidgetProvider::class.java)
    val appWidgetIds = manager.getAppWidgetIds(component)
    appWidgetIds.forEach { appWidgetId ->
      val views = RemoteViews(
        context.packageName,
        selectLayout(manager.getAppWidgetOptions(appWidgetId)),
      ).apply {
        setTextViewText(R.id.widgetPrimaryCaption, snapshot.primaryCaptionText)
        setTextViewText(
          R.id.widgetSecondaryCaption,
          snapshot.secondaryCaptionText ?: snapshot.secondaryValueText ?: "Secondary limit",
        )
        setTextViewText(R.id.widgetUpdated, snapshot.updatedText)

        setProgressBar(
          R.id.widgetPrimaryProgress,
          100,
          (snapshot.primaryPercent ?: 0).coerceIn(0, 100),
          snapshot.isRefreshing,
        )
        setProgressBar(
          R.id.widgetSecondaryProgress,
          100,
          (snapshot.secondaryPercent ?: 0).coerceIn(0, 100),
          snapshot.isRefreshing,
        )

        setOnClickPendingIntent(R.id.widgetRoot, refreshPendingIntent)
      }
      manager.updateAppWidget(appWidgetId, views)
    }
  }

  private fun selectLayout(options: Bundle): Int {
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    return when {
      minWidth < 180 || minHeight < 120 -> R.layout.codex_usage_widget_compact
      minWidth >= 280 && minHeight >= 130 -> R.layout.codex_usage_widget_wide
      else -> R.layout.codex_usage_widget
    }
  }
}
