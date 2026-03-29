package dev.ushagent.codexwidgetmvp

import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ushagent.codexwidgetmvp.ui.CodexWidgetAppTheme
import dev.ushagent.codexwidgetmvp.widget.CodexUsageWidgetProvider
import dev.ushagent.codexwidgetmvp.widget.CodexUsageWidgetUpdater
import dev.ushagent.codexwidgetmvp.widget.CodexWidgetWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DebugLog.d("MainActivity created")
    CodexWidgetWorkScheduler.ensureScheduled(this)

    setContent {
      CodexWidgetAppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          CodexWidgetScreen()
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodexWidgetScreen() {
  val context = LocalContext.current
  val repository = rememberRepository()
  val coroutineScope = rememberCoroutineScope()
  var authJson by rememberSaveable { mutableStateOf("") }
  var statusText by rememberSaveable { mutableStateOf("Ready") }
  val hasSavedAuth = CodexPrefs.loadAuthJson(context).isNotBlank()
  val summaryText = if (hasSavedAuth) {
    stringResource(R.string.summary_auth_ready)
  } else {
    stringResource(R.string.summary_no_auth)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(padding)
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      HeroCard(summaryText = summaryText)

      SectionCard(
        title = stringResource(R.string.section_auth),
        subtitle = stringResource(R.string.section_auth_subtitle),
      ) {
        AuthEditor(
          value = authJson,
          onValueChange = { authJson = it },
        )
      }

      SectionCard(
        title = stringResource(R.string.section_actions),
        subtitle = null,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ActionRow(
            first = {
              ActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.action_load_saved),
                icon = null,
                onClick = {
                  val saved = CodexPrefs.loadAuthJson(context)
                  if (saved.isBlank()) {
                    Toast.makeText(context, "No saved auth.json", Toast.LENGTH_SHORT).show()
                  } else {
                    authJson = saved
                    statusText = "Loaded saved auth.json"
                  }
                },
              )
            },
            second = {
              ActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.action_paste),
                icon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                onClick = {
                  val text = readClipboard(context)
                  if (text.isBlank()) {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                  } else {
                    authJson = text
                  }
                },
              )
            },
          )

          ActionRow(
            first = {
              ActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.action_save),
                icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                onClick = {
                  val raw = authJson.trim()
                  if (raw.isBlank()) {
                    Toast.makeText(context, "auth.json is empty", Toast.LENGTH_SHORT).show()
                  } else {
                    CodexPrefs.saveAuthJson(context, raw)
                    CodexWidgetWorkScheduler.ensureScheduled(context)
                    statusText = "Saved auth.json"
                  }
                },
              )
            },
            second = {
              ActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.action_refresh),
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                onClick = {
                  val raw = authJson.trim()
                  if (raw.isBlank()) {
                    Toast.makeText(context, "Paste auth.json first", Toast.LENGTH_SHORT).show()
                    return@ActionButton
                  }
                  statusText = "Refreshing..."
                  coroutineScope.launch {
                    runRefresh(
                      context = context,
                      repository = repository,
                      raw = raw,
                      onSuccess = { resolved ->
                        authJson = resolved.updatedAuthJson
                        statusText = formatStatus(resolved)
                      },
                      onError = { statusText = "Error: ${it.message}" },
                    )
                  }
                },
              )
            },
          )

          PrimaryActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.action_paste_refresh),
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            onClick = {
              val text = readClipboard(context)
              if (text.isBlank()) {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                return@PrimaryActionButton
              }
              authJson = text
              CodexPrefs.saveAuthJson(context, text.trim())
              CodexWidgetWorkScheduler.ensureScheduled(context)
              statusText = "Refreshing..."
              coroutineScope.launch {
                runRefresh(
                  context = context,
                  repository = repository,
                  raw = text,
                  onSuccess = { resolved ->
                    authJson = resolved.updatedAuthJson
                    statusText = formatStatus(resolved)
                  },
                  onError = { statusText = "Error: ${it.message}" },
                )
              }
            },
          )

          ActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.action_pin_widget),
            icon = { Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, contentDescription = null) },
            onClick = {
              val manager = AppWidgetManager.getInstance(context)
              if (!manager.isRequestPinAppWidgetSupported) {
                Toast.makeText(context, R.string.toast_widget_pin_not_supported, Toast.LENGTH_SHORT).show()
              } else {
                val provider = ComponentName(context, CodexUsageWidgetProvider::class.java)
                manager.requestPinAppWidget(provider, null, null)
                Toast.makeText(context, R.string.toast_widget_pinned, Toast.LENGTH_SHORT).show()
              }
            },
          )
        }
      }

      SectionCard(
        title = stringResource(R.string.section_status),
        subtitle = null,
      ) {
        Text(
          text = statusText,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .background(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
        )
      }
    }
  }
}

private suspend fun runRefresh(
  context: Context,
  repository: CodexRepository,
  raw: String,
  onSuccess: (ResolvedUsage) -> Unit,
  onError: (Throwable) -> Unit,
) {
  DebugLog.d("Compose refresh requested: length=${raw.length}")
  val result = runCatching {
    withContext(Dispatchers.IO) { repository.resolveUsage(raw.trim()) }
  }

  result.onSuccess { resolved ->
    DebugLog.d("Compose refresh completed: refreshed=${resolved.refreshed}, ok=${resolved.usage.ok}")
    CodexPrefs.saveAuthJson(context, resolved.updatedAuthJson)
    val snapshot = repository.buildWidgetSnapshot(resolved.usage)
    CodexPrefs.saveWidgetSnapshot(context, snapshot)
    CodexUsageWidgetUpdater.updateAll(context, snapshot)
    onSuccess(resolved)
  }.onFailure {
    DebugLog.e("Compose refresh failed", it)
    onError(it)
  }
}

private fun formatStatus(resolved: ResolvedUsage): String = buildString {
  append("Plan: ${resolved.usage.planType ?: "unknown"}\n")
  append("${resolved.usage.primaryLabel ?: "primary"}: ${resolved.usage.primaryRemainingPercent ?: "?"}%")
  if (resolved.usage.primaryResetInText != null) {
    append(", reset ${resolved.usage.primaryResetInText}")
  }
  append("\n")
  append("${resolved.usage.secondaryLabel ?: "secondary"}: ${resolved.usage.secondaryRemainingPercent ?: "?"}%")
  if (resolved.usage.secondaryResetInText != null) {
    append(", reset ${resolved.usage.secondaryResetInText}")
  }
  append("\n")
  append("Refreshed token: ${if (resolved.refreshed) "yes" else "no"}")
}

@Composable
private fun rememberRepository(): CodexRepository = androidx.compose.runtime.remember { CodexRepository() }

@Composable
private fun HeroCard(summaryText: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    shape = RoundedCornerShape(28.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.title_main),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = stringResource(R.string.subtitle_main),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
      )
      Box(
        modifier = Modifier
          .background(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        Text(
          text = summaryText,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }

      Text(
        text = stringResource(R.string.summary_field_lazy_load),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
      )
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  subtitle: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      content = {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        content()
      },
    )
  }
}

@Composable
private fun AuthEditor(
  value: String,
  onValueChange: (String) -> Unit,
) {
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    textStyle = MaterialTheme.typography.bodyMedium.copy(
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 15.sp,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .height(240.dp)
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
      )
      .padding(14.dp),
    decorationBox = { inner ->
      if (value.isBlank()) {
        Text(
          text = stringResource(R.string.hint_auth_json),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      inner()
    },
  )
}

@Composable
private fun ActionButton(
  modifier: Modifier = Modifier,
  label: String,
  icon: (@Composable () -> Unit)?,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    modifier = modifier.height(56.dp),
    shape = RoundedCornerShape(18.dp),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
      }
      Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 16.sp,
      )
    }
  }
}

@Composable
private fun ActionRow(
  first: @Composable () -> Unit,
  second: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.weight(1f)) {
      first()
    }
    Box(modifier = Modifier.weight(1f)) {
      second()
    }
  }
}

@Composable
private fun PrimaryActionButton(
  modifier: Modifier = Modifier,
  label: String,
  icon: @Composable () -> Unit,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    modifier = modifier.height(56.dp),
    shape = RoundedCornerShape(20.dp),
    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.tertiary,
      contentColor = MaterialTheme.colorScheme.onTertiary,
    ),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      icon()
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

private fun readClipboard(context: Context): String {
  DebugLog.d("Paste from clipboard requested")
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}
