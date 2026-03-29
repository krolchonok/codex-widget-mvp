package dev.ushagent.codexwidgetmvp

import android.util.Log

private const val TAG = "CodexWidgetMvp"

object DebugLog {
  fun d(message: String) {
    Log.d(TAG, message)
  }

  fun e(message: String, error: Throwable? = null) {
    Log.e(TAG, message, error)
  }
}
