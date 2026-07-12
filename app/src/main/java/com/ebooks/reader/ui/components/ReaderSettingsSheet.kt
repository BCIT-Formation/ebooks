package com.ebooks.reader.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebooks.reader.R
import com.ebooks.reader.viewmodel.FontFamily
import com.ebooks.reader.viewmodel.OrientationLock
import com.ebooks.reader.viewmodel.ReaderSettings
import com.ebooks.reader.viewmodel.ReaderThemeOption
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    customFonts: List<String> = emptyList(),
    onImportFont: (Uri) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(stringResource(R.string.reading_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            HorizontalDivider()

            // Theme
            SectionLabel(stringResource(R.string.theme))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeOption(stringResource(R.string.theme_light), Color.White, Color(0xFF222222), settings.themeOption == ReaderThemeOption.LIGHT) { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.LIGHT)) }
                ThemeOption(stringResource(R.string.theme_dark), Color(0xFF1a1a2e), Color(0xFFe0e0e0), settings.themeOption == ReaderThemeOption.DARK) { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.DARK)) }
                ThemeOption(stringResource(R.string.theme_sepia), Color(0xFFF3EAD3), Color(0xFF3b2f1e), settings.themeOption == ReaderThemeOption.SEPIA) { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.SEPIA)) }
                ThemeOption(stringResource(R.string.theme_night), Color(0xFF0d0d0d), Color(0xFFaaaaaa), settings.themeOption == ReaderThemeOption.NIGHT) { onSettingsChanged(settings.copy(themeOption = ReaderThemeOption.NIGHT)) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font size
            SectionLabel(stringResource(R.string.font_size))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onSettingsChanged(settings.copy(fontSize = (settings.fontSize - 1).coerceAtLeast(12))) }) {
                    Icon(Icons.Default.Remove, stringResource(R.string.decrease_font_size))
                }
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(fontSize = it.toInt())) },
                    valueRange = 12f..32f,
                    steps = 19,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onSettingsChanged(settings.copy(fontSize = (settings.fontSize + 1).coerceAtMost(32))) }) {
                    Icon(Icons.Default.Add, stringResource(R.string.increase_font_size))
                }
                Text("${settings.fontSize}px", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
            }

            // Line height
            SectionLabel(stringResource(R.string.line_spacing))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FormatLineSpacing, null, modifier = Modifier.size(20.dp))
                Slider(
                    value = settings.lineHeight,
                    onValueChange = { onSettingsChanged(settings.copy(lineHeight = it)) },
                    valueRange = 1.0f..3.0f,
                    steps = 19,
                    modifier = Modifier.weight(1f)
                )
                Text(String.format("%.1f", settings.lineHeight), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
            }

            // Font family — built-in families, then user-imported TTF/OTF fonts
            SectionLabel(stringResource(R.string.font))
            val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let(onImportFont)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FontFamily.entries.forEach { family ->
                    FilterChip(
                        selected = settings.customFontPath == null && settings.fontFamily == family,
                        onClick = { onSettingsChanged(settings.copy(fontFamily = family, customFontPath = null)) },
                        label = { Text(family.displayName, fontSize = 12.sp) }
                    )
                }
                customFonts.forEach { fontPath ->
                    FilterChip(
                        selected = settings.customFontPath == fontPath,
                        onClick = { onSettingsChanged(settings.copy(customFontPath = fontPath)) },
                        label = { Text(File(fontPath).nameWithoutExtension, fontSize = 12.sp) }
                    )
                }
                AssistChip(
                    // Document providers rarely report font MIME types reliably,
                    // so open with */* — the import path validates the extension.
                    onClick = { fontPicker.launch(arrayOf("*/*")) },
                    label = { Text(stringResource(R.string.add_font), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Paragraph indent
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatIndentIncrease, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.paragraph_indent))
                }
                Switch(checked = settings.paragraphIndent, onCheckedChange = { onSettingsChanged(settings.copy(paragraphIndent = it)) })
            }

            // Keep screen on
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LightMode, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.keep_screen_on))
                }
                Switch(checked = settings.keepScreenOn, onCheckedChange = { onSettingsChanged(settings.copy(keepScreenOn = it)) })
            }

            // Orientation lock
            SectionLabel(stringResource(R.string.screen_orientation))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    OrientationLock.UNSPECIFIED to stringResource(R.string.orientation_auto),
                    OrientationLock.PORTRAIT   to stringResource(R.string.orientation_portrait),
                    OrientationLock.LANDSCAPE  to stringResource(R.string.orientation_landscape)
                ).forEach { (lock, label) ->
                    FilterChip(
                        selected = settings.orientationLock == lock,
                        onClick = { onSettingsChanged(settings.copy(orientationLock = lock)) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Auto-scroll speed
            SectionLabel(stringResource(R.string.auto_scroll_speed))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SlowMotionVideo, null, modifier = Modifier.size(20.dp))
                Slider(
                    value = settings.autoScrollSpeed.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(autoScrollSpeed = it.toInt())) },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (settings.autoScrollSpeed == 0) stringResource(R.string.off) else "${settings.autoScrollSpeed}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(32.dp)
                )
            }

            // Tilt-to-scroll
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ScreenRotation, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tilt_to_scroll))
                }
                Switch(checked = settings.tiltScrollEnabled, onCheckedChange = { onSettingsChanged(settings.copy(tiltScrollEnabled = it)) })
            }

            // Sleep timer
            SectionLabel(stringResource(R.string.sleep_timer_auto_scroll))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to stringResource(R.string.off), 15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h").forEach { (mins, label) ->
                    FilterChip(
                        selected = settings.sleepTimerMinutes == mins,
                        onClick = { onSettingsChanged(settings.copy(sleepTimerMinutes = mins)) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Night light
            SectionLabel(stringResource(R.string.night_light))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WbIncandescent, null, modifier = Modifier.size(20.dp))
                Slider(
                    value = settings.nightLightAlpha,
                    onValueChange = { onSettingsChanged(settings.copy(nightLightAlpha = it)) },
                    valueRange = 0f..0.5f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (settings.nightLightAlpha == 0f) stringResource(R.string.off) else "${(settings.nightLightAlpha * 200).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun ThemeOption(label: String, bg: Color, text: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text("A", color = text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
