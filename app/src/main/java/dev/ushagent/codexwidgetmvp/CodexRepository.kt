package dev.ushagent.codexwidgetmvp

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CodexRepository(
  private val api: CodexRemoteApi = CodexRemoteApi(),
) {
  private fun sanitizeCredential(value: String?): String? =
    value
      ?.replace("\\s+".toRegex(), "")
      ?.trim()
      ?.ifEmpty { null }

  suspend fun resolveUsage(rawAuthJson: String): ResolvedUsage {
    DebugLog.d("Resolving usage from auth.json: length=${rawAuthJson.length}")
    val parsed = parseAuthJson(rawAuthJson)
    DebugLog.d("Parsed auth.json: accountId=${parsed.accountId}, hasRefresh=${parsed.refreshToken != null}")
    val effective = if (shouldRefresh(parsed)) {
      DebugLog.d("Auth token is stale, refreshing")
      api.refreshAuth(parsed)
    } else {
      DebugLog.d("Auth token is fresh, skipping refresh")
      parsed
    }
    val usage = api.fetchUsage(effective)
    DebugLog.d(
      "Usage resolved: ok=${usage.ok}, primary=${usage.primaryRemainingPercent}, " +
        "secondary=${usage.secondaryRemainingPercent}, error=${usage.error}"
    )
    return ResolvedUsage(
      updatedAuthJson = effective.raw,
      refreshed = effective.raw != parsed.raw,
      usage = usage,
    )
  }

  fun parseAuthJson(rawAuthJson: String): ParsedAuthJson {
    DebugLog.d("Parsing auth.json payload")
    val root = JSONObject(rawAuthJson)
    val tokens = root.optJSONObject("tokens") ?: throw IllegalArgumentException("auth.json is missing tokens")
    val accessToken = sanitizeCredential(tokens.optString("access_token"))
      ?: throw IllegalArgumentException("auth.json is missing tokens.access_token")
    val idToken = sanitizeCredential(tokens.optString("id_token"))
    val accountId = sanitizeCredential(tokens.optString("account_id")) ?: extractAccountId(idToken)

    return ParsedAuthJson(
      raw = root.toString(2),
      accessToken = accessToken,
      refreshToken = sanitizeCredential(tokens.optString("refresh_token")),
      accountId = accountId,
      idToken = idToken,
      lastRefreshIso = root.optString("last_refresh").trim().ifEmpty { null },
    )
  }

  fun shouldRefresh(auth: ParsedAuthJson, now: Long = System.currentTimeMillis()): Boolean {
    val expiration = parseJwtExpirationMillis(auth.accessToken)
    if (expiration != null && expiration <= now) {
      DebugLog.d("Access token expired at $expiration, refresh required")
      return true
    }

    val lastRefresh = auth.lastRefreshIso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
      ?: return false
    val shouldRefresh = now - lastRefresh >= TimeUnit.DAYS.toMillis(8)
    DebugLog.d("Refresh interval check: shouldRefresh=$shouldRefresh")
    return shouldRefresh
  }

  fun buildWidgetSnapshot(snapshot: UsageSnapshot): WidgetSnapshot {
    val primaryText = snapshot.primaryRemainingPercent?.let { "$it%" } ?: "--%"
    val primaryCaption = when {
      !snapshot.ok -> snapshot.error ?: "Usage unavailable"
      snapshot.primaryLabel != null && snapshot.primaryResetInText != null -> {
        "${snapshot.primaryLabel} • ${snapshot.primaryRemainingPercent ?: "--"}% • reset ${snapshot.primaryResetInText}"
      }
      snapshot.primaryLabel != null -> "${snapshot.primaryLabel} • ${snapshot.primaryRemainingPercent ?: "--"}%"
      snapshot.primaryResetInText != null -> "${snapshot.primaryRemainingPercent ?: "--"}% • reset ${snapshot.primaryResetInText}"
      else -> "Primary limit"
    }

    val secondaryText = snapshot.secondaryRemainingPercent?.let { "$it%" }
    val secondaryCaption = when {
      secondaryText == null && snapshot.planType != null -> "Plan ${snapshot.planType}"
      secondaryText == null -> null
      snapshot.secondaryLabel != null && snapshot.secondaryResetInText != null -> {
        "${snapshot.secondaryLabel} • ${snapshot.secondaryRemainingPercent ?: "--"}% • reset ${snapshot.secondaryResetInText}"
      }
      snapshot.secondaryLabel != null -> "${snapshot.secondaryLabel} • ${snapshot.secondaryRemainingPercent ?: "--"}%"
      snapshot.secondaryResetInText != null -> "${snapshot.secondaryRemainingPercent ?: "--"}% • reset ${snapshot.secondaryResetInText}"
      else -> "Secondary limit"
    }

    val updated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    return WidgetSnapshot(
      headerText = snapshot.planType?.let { "Codex $it" } ?: "Codex limits",
      primaryValueText = primaryText,
      primaryPercent = snapshot.primaryRemainingPercent,
      primaryCaptionText = primaryCaption,
      secondaryValueText = secondaryText,
      secondaryPercent = snapshot.secondaryRemainingPercent,
      secondaryCaptionText = secondaryCaption,
      updatedText = "Updated $updated",
      isRefreshing = false,
    )
  }

  fun buildRefreshingWidgetSnapshot(current: WidgetSnapshot): WidgetSnapshot =
    current.copy(
      updatedText = "Updating...",
      isRefreshing = true,
    )

  fun buildErrorWidgetSnapshot(current: WidgetSnapshot, message: String): WidgetSnapshot =
    current.copy(
      primaryCaptionText = message,
      secondaryCaptionText = current.secondaryCaptionText ?: current.secondaryValueText ?: "Update failed",
      updatedText = "Update failed",
      isRefreshing = false,
    )

  private fun parseJwtExpirationMillis(jwt: String): Long? {
    val payload = decodeJwtPayload(jwt) ?: return null
    val exp = payload.optLong("exp", 0L)
    return if (exp > 0L) exp * 1000 else null
  }

  private fun extractAccountId(idToken: String?): String? {
    val payload = idToken?.let { decodeJwtPayload(it) } ?: return null
    val auth = payload.optJSONObject("https://api.openai.com/auth") ?: return null
    return auth.optString("chatgpt_account_id").trim().ifEmpty { null }
  }

  private fun decodeJwtPayload(jwt: String): JSONObject? {
    val parts = jwt.split(".")
    if (parts.size != 3) {
      return null
    }
    return runCatching {
      val bytes = java.util.Base64.getUrlDecoder().decode(parts[1])
      JSONObject(String(bytes, Charsets.UTF_8))
    }.getOrNull()
  }
}
