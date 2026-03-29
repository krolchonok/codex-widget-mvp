package dev.ushagent.codexwidgetmvp

data class ParsedAuthJson(
  val raw: String,
  val accessToken: String,
  val refreshToken: String?,
  val accountId: String?,
  val idToken: String?,
  val lastRefreshIso: String?,
)

data class UsageSnapshot(
  val ok: Boolean,
  val planType: String?,
  val primaryLabel: String?,
  val primaryRemainingPercent: Int?,
  val primaryResetInText: String?,
  val secondaryLabel: String?,
  val secondaryRemainingPercent: Int?,
  val secondaryResetInText: String?,
  val error: String?,
)

data class WidgetSnapshot(
  val headerText: String,
  val primaryValueText: String,
  val primaryPercent: Int?,
  val primaryCaptionText: String,
  val secondaryValueText: String?,
  val secondaryPercent: Int?,
  val secondaryCaptionText: String?,
  val updatedText: String,
  val isRefreshing: Boolean,
)

data class ResolvedUsage(
  val updatedAuthJson: String,
  val refreshed: Boolean,
  val usage: UsageSnapshot,
)
