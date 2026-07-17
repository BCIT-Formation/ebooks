package com.ebooks.reader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ebooks.reader.R
import com.ebooks.reader.data.settings.EInkSettings
import com.ebooks.reader.ui.theme.EInkMode
import kotlinx.coroutines.launch

/**
 * E-Ink mode settings section for the reader settings sheet
 * Can be added to ReaderSettingsSheet when running on e-reader devices
 */
@Composable
fun EInkSettingsSection(
    modifier: Modifier = Modifier,
    einkSettings: EInkSettings? = null
) {
    if (einkSettings == null) return

    val scope = rememberCoroutineScope()

    val isEInkEnabled by einkSettings.isEInkEnabled.collectAsState(initial = false)
    val disableAnimations by einkSettings.isAnimationsDisabled.collectAsState(initial = true)
    val forceGrayscale by einkSettings.isGrayscaleForced.collectAsState(initial = true)
    val volumeKeyNav by einkSettings.isVolumeKeyNavigationEnabled.collectAsState(initial = true)
    val increaseContrast by einkSettings.isContrastIncreased.collectAsState(initial = true)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("E-Ink Display")

        // E-Ink Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E-Ink Mode",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Optimized for e-reader devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEInkEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        einkSettings.setEInkEnabled(enabled)
                    }
                }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Disable Animations
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Disable Animations",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Reduces e-ink refresh cycles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = disableAnimations,
                onCheckedChange = { disable ->
                    scope.launch {
                        einkSettings.setDisableAnimations(disable)
                    }
                }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Force Grayscale
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grayscale",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Convert colors to grayscale",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = forceGrayscale,
                onCheckedChange = { force ->
                    scope.launch {
                        einkSettings.setForceGrayscale(force)
                    }
                }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Volume Key Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Volume Key Navigation",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Use volume buttons for page turns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = volumeKeyNav,
                onCheckedChange = { enabled ->
                    scope.launch {
                        einkSettings.setVolumeKeyNavigation(enabled)
                    }
                }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Increase Contrast
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "High Contrast",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Increase text/background contrast",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = increaseContrast,
                onCheckedChange = { increase ->
                    scope.launch {
                        einkSettings.setIncreaseContrast(increase)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
