package com.ebooks.reader.ui.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.ebooks.reader.R

@Composable
fun DrawingToolbar(
    modifier: Modifier = Modifier,
    settings: DrawingSettings,
    onSettingsChanged: (DrawingSettings) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onClearPage: () -> Unit = {},
    onClearAll: () -> Unit = {},
    isDrawingEnabled: Boolean = true
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                selected = settings.tool == DrawingTool.PEN,
                onClick = { onSettingsChanged(settings.copy(tool = DrawingTool.PEN)) },
                label = stringResource(R.string.draw_tool_pen),
                enabled = isDrawingEnabled
            )
            ToolButton(
                selected = settings.tool == DrawingTool.HIGHLIGHT,
                onClick = { onSettingsChanged(settings.copy(tool = DrawingTool.HIGHLIGHT)) },
                label = stringResource(R.string.draw_tool_highlight),
                enabled = isDrawingEnabled
            )
            ToolButton(
                selected = settings.tool == DrawingTool.TEXT,
                onClick = { onSettingsChanged(settings.copy(tool = DrawingTool.TEXT)) },
                label = stringResource(R.string.draw_tool_text),
                enabled = isDrawingEnabled
            )
            ToolButton(
                selected = settings.tool == DrawingTool.RECTANGLE,
                onClick = { onSettingsChanged(settings.copy(tool = DrawingTool.RECTANGLE)) },
                label = stringResource(R.string.draw_tool_rect),
                enabled = isDrawingEnabled
            )
            ToolButton(
                selected = settings.tool == DrawingTool.CIRCLE,
                onClick = { onSettingsChanged(settings.copy(tool = DrawingTool.CIRCLE)) },
                label = stringResource(R.string.draw_tool_circle),
                enabled = isDrawingEnabled
            )

            Box(modifier = Modifier.weight(1f))

            IconButton(onClick = { showColorPicker = !showColorPicker }) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(settings.color))
                )
            }
        }

        if (showColorPicker) {
            ColorPicker(
                selectedColor = settings.color,
                onColorSelected = { newColor ->
                    onSettingsChanged(settings.copy(color = newColor))
                    showColorPicker = false
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                stringResource(R.string.draw_stroke_label, String.format("%.1f", settings.strokeWidth)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Slider(
                value = settings.strokeWidth,
                onValueChange = { onSettingsChanged(settings.copy(strokeWidth = it)) },
                valueRange = 0.5f..10f,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                stringResource(R.string.draw_opacity_label, (settings.opacity * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Slider(
                value = settings.opacity,
                onValueChange = { onSettingsChanged(settings.copy(opacity = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onClearPage, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.draw_clear_page))
            }
            IconButton(onClick = onClearAll, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.draw_clear_all))
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            // A visible outline keeps every chip legible against the toolbar,
            // including in the high-contrast E-ink and AMOLED display modes.
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    val colors = listOf(
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW,
        Color.MAGENTA,
        Color.CYAN,
        Color.BLACK,
        Color.GRAY,
        Color.LTGRAY,
        Color.WHITE,
        Color.parseColor("#FF6B35"),
        Color.parseColor("#004E89"),
        Color.parseColor("#F77F00"),
        Color.parseColor("#D62828"),
        Color.parseColor("#06A77D"),
        Color.parseColor("#9B2226")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(color))
                    .clickable { onColorSelected(color) }
            )
        }
    }
}
