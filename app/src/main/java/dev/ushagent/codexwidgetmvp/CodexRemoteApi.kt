package dev.ushagent.codexwidgetmvp

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CodexRemoteApi(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(20, TimeUnit.SECONDS)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build(),
) {
  private data class UsageWindow(
    val label: String?,
    val remainingPercent: Int?,
    val resetInText: String?,
  )

  suspend fun refreshAuth(auth: ParsedAuthJson): ParsedAuthJson {
    DebugLog.d("Refreshing auth via oauth/token")
    val refreshToken = auth.refreshToken ?: throw IllegalArgumentException("auth.json is missing tokens.refresh_token")
    val body = JSONObject()
      .put("client_id", "app_EMoamEEZ73f0CkXaXp7hrann")
      .put("grant_type", "refresh_token")
      .put("refresh_token", refreshToken)
      .toString()
      .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
      .url("https://auth.openai.com/oauth/token")
      .post(body)
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        DebugLog.e("Token refresh failed with HTTP ${response.code}")
        throw IllegalStateException("Token refresh failed: HTTP ${response.code}")
      }

      val payload = JSONObject(response.body?.string().orEmpty())
      val accessToken = payload.optString("access_token").trim()
      val nextRefreshToken = payload.optString("refresh_token").trim()
      val idToken = payload.optString("id_token").trim().ifEmpty { auth.idToken }
      val accountId = CodexRepository().parseAuthJson(
        JSONObject()
          .put("tokens", JSONObject().put("id_token", idToken).put("access_token", accessToken).put("refresh_token", nextRefreshToken))
          .toString()
      ).accountId ?: auth.accountId

      val root = JSONObject(auth.raw)
      val tokens = root.optJSONObject("tokens") ?: JSONObject()
      tokens.put("access_token", accessToken)
      tokens.put("refresh_token", nextRefreshToken)
      if (!idToken.isNullOrEmpty()) {
        tokens.put("id_token", idToken)
      }
      if (!accountId.isNullOrEmpty()) {
        tokens.put("account_id", accountId)
      }
      root.put("tokens", tokens)
      root.put("last_refresh", java.time.Instant.now().toString())

      DebugLog.d("Token refresh succeeded: access=${accessToken.isNotEmpty()}, refresh=${nextRefreshToken.isNotEmpty()}")
      return ParsedAuthJson(
        raw = root.toString(2),
        accessToken = accessToken,
        refreshToken = nextRefreshToken,
        accountId = accountId,
        idToken = idToken,
        lastRefreshIso = root.optString("last_refresh"),
      )
    }
  }

  suspend fun fetchUsage(auth: ParsedAuthJson): UsageSnapshot {
    DebugLog.d("Fetching usage from wham/usage for accountId=${auth.accountId}")
    val accountId = auth.accountId ?: throw IllegalArgumentException("auth.json is missing tokens.account_id")
    val request = Request.Builder()
      .url("https://chatgpt.com/backend-api/wham/usage")
      .get()
      .header("Authorization", "Bearer ${auth.accessToken}")
      .header("ChatGPT-Account-Id", accountId)
      .header("Accept", "application/json")
      .header("User-Agent", "codex-cli")
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        DebugLog.e("Usage request failed with HTTP ${response.code}")
        return UsageSnapshot(
          ok = false,
          planType = null,
          primaryLabel = null,
          primaryRemainingPercent = null,
          primaryResetInText = null,
          secondaryLabel = null,
          secondaryRemainingPercent = null,
          secondaryResetInText = null,
          error = "Usage failed: HTTP ${response.code}",
        )
      }

      val payload = JSONObject(response.body?.string().orEmpty())
      val rateLimit = payload.optJSONObject("rate_limit")
      val primaryWindow = parseUsageWindow(rateLimit?.optJSONObject("primary_window"))
      val secondaryWindow = parseUsageWindow(rateLimit?.optJSONObject("secondary_window"))

      DebugLog.d(
        "Usage request succeeded: plan=${payload.optString("plan_type")}, " +
          "primary=${primaryWindow.remainingPercent}, secondary=${secondaryWindow.remainingPercent}"
      )
      return UsageSnapshot(
        ok = true,
        planType = payload.optString("plan_type").trim().ifEmpty { null },
        primaryLabel = primaryWindow.label,
        primaryRemainingPercent = primaryWindow.remainingPercent,
        primaryResetInText = primaryWindow.resetInText,
        secondaryLabel = secondaryWindow.label,
        secondaryRemainingPercent = secondaryWindow.remainingPercent,
        secondaryResetInText = secondaryWindow.resetInText,
        error = null,
      )
    }
  }

  private fun parseUsageWindow(window: JSONObject?): UsageWindow {
    if (window == null) {
      return UsageWindow(null, null, null)
    }

    val usedPercent = window.optDouble("used_percent", Double.NaN)
    val remainingPercent = if (!usedPercent.isNaN()) {
      (100 - usedPercent).toInt().coerceAtLeast(0)
    } else {
      null
    }
    val resetAfterSeconds = window.optLong("reset_after_seconds", -1L).takeIf { it >= 0L }
    val limitWindowSeconds = window.optLong("limit_window_seconds", -1L).takeIf { it >= 0L }

    return UsageWindow(
      label = formatWindowLabel(limitWindowSeconds),
      remainingPercent = remainingPercent,
      resetInText = resetAfterSeconds?.let { formatDuration(it) },
    )
  }

  private fun formatWindowLabel(seconds: Long?): String? =
    when (seconds) {
      null -> null
      18_000L -> "5h"
      86_400L -> "day"
      604_800L -> "week"
      else -> formatDuration(seconds)
    }

  private fun formatDuration(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60

    return when {
      days > 0 -> "${days}d ${hours}h"
      hours > 0 -> "${hours}h ${minutes}m"
      else -> "${minutes}m"
    }
  }
}
