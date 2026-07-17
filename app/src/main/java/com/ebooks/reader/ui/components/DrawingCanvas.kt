package com.ebooks.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.Annotation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// A gesture shorter than this (normalized diagonal) is treated as a tap, not a drag.
private const val MIN_SHAPE_SIZE = 0.02f

@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    annotations: List<Annotation> = emptyList(),
    isEnabled: Boolean = true,
    settings: DrawingSettings = DrawingSettings(),
    onStrokeCompleted: (Annotation) -> Unit = {},
    onAnnotationDeleted: (Annotation) -> Unit = {},
    onClear: () -> Unit = {}
) {
    var pendingStroke by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }
    // Position (normalized) where a TEXT annotation is being placed; drives the input dialog.
    var pendingTextAt by remember { mutableStateOf<StrokePoint?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Key on settings too: changing tool/color mid-session must restart the
                // gesture handler so the new tool is used (it captures settings by value).
                .pointerInput(isEnabled, settings) {
                    if (!isEnabled) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        val start = StrokePoint(down.position.x / canvasWidth, down.position.y / canvasHeight, down.pressure)
                        pendingStroke = listOf(start)
                        var lastPoint = start

                        while (true) {
                            val event = awaitPointerEvent()
                            val added = event.changes.flatMap { change ->
                                val point = StrokePoint(change.position.x / canvasWidth, change.position.y / canvasHeight, change.pressure)
                                if (distanceBetween(lastPoint, point) > 0.001f) {
                                    lastPoint = point
                                    listOf(point)
                                } else emptyList()
                            }
                            if (added.isNotEmpty()) pendingStroke = pendingStroke + added
                            event.changes.forEach { it.consume() }
                            if (event.changes.any { !it.pressed }) break
                        }

                        val stroke = pendingStroke
                        pendingStroke = emptyList()
                        onGestureFinished(stroke, settings, onStrokeCompleted) { pendingTextAt = it }
                    }
                }
        ) {
            canvasWidth = size.width
            canvasHeight = size.height

            drawRect(Color.Transparent)

            annotations.forEach { annotation ->
                if (annotation.isDeleted) return@forEach
                when (annotation.annotationType) {
                    "freehand" -> drawFreehandStroke(annotation)
                    "highlight" -> drawHighlight(annotation)
                    "rectangle" -> drawRectangleShape(annotation)
                    "circle" -> drawCircleShape(annotation)
                    "text" -> drawTextAnnotation(annotation)
                }
            }

            if (pendingStroke.isNotEmpty()) drawPreview(pendingStroke, settings)
        }

        pendingTextAt?.let { at ->
            TextAnnotationDialog(
                onConfirm = { text ->
                    onStrokeCompleted(buildTextAnnotation(at, text, settings))
                    pendingTextAt = null
                },
                onDismiss = { pendingTextAt = null }
            )
        }
    }
}

// ── Gesture → annotation ──────────────────────────────────────────────────────

private fun onGestureFinished(
    points: List<StrokePoint>,
    settings: DrawingSettings,
    onStrokeCompleted: (Annotation) -> Unit,
    onTextRequested: (StrokePoint) -> Unit
) {
    if (points.isEmpty()) return
    when (settings.tool) {
        DrawingTool.TEXT -> onTextRequested(points.first())
        DrawingTool.PEN, DrawingTool.ERASER -> {
            if (points.size >= 2) onStrokeCompleted(freehandAnnotation(points, settings))
        }
        DrawingTool.HIGHLIGHT -> boundsAnnotation(points, settings, "highlight")?.let(onStrokeCompleted)
        DrawingTool.RECTANGLE, DrawingTool.ARROW -> boundsAnnotation(points, settings, "rectangle")?.let(onStrokeCompleted)
        DrawingTool.CIRCLE -> circleAnnotation(points, settings)?.let(onStrokeCompleted)
    }
}

private fun freehandAnnotation(points: List<StrokePoint>, s: DrawingSettings) = baseAnnotation(s).copy(
    annotationType = "freehand",
    points = serializePoints(points)
)

private fun boundsAnnotation(points: List<StrokePoint>, s: DrawingSettings, type: String): Annotation? {
    val (x, y, w, h) = boundingBox(points) ?: return null
    if (max(w, h) < MIN_SHAPE_SIZE) return null
    return baseAnnotation(s).copy(annotationType = type, boundingBox = serializeBoundingBox(x, y, w, h))
}

private fun circleAnnotation(points: List<StrokePoint>, s: DrawingSettings): Annotation? {
    val (x, y, w, h) = boundingBox(points) ?: return null
    if (max(w, h) < MIN_SHAPE_SIZE) return null
    val radius = max(w, h) / 2f
    return baseAnnotation(s).copy(
        annotationType = "circle",
        boundingBox = serializeBoundingBoxCircle(x + w / 2f, y + h / 2f, radius)
    )
}

private fun buildTextAnnotation(at: StrokePoint, text: String, s: DrawingSettings) = baseAnnotation(s).copy(
    annotationType = "text",
    boundingBox = serializeBoundingBox(at.x, at.y, 0f, 0f),
    textContent = text,
    metadata = "size=${textSizePx(s)}"
)

private fun baseAnnotation(s: DrawingSettings) = Annotation(
    id = java.util.UUID.randomUUID().toString(),
    bookId = "",
    pageIdentifier = "",
    pageIndex = 0,
    annotationType = "freehand",
    color = s.color,
    strokeWidth = s.strokeWidth,
    opacity = s.opacity,
    createdAt = System.currentTimeMillis(),
    modifiedAt = System.currentTimeMillis()
)

/** Text size in pixels, derived from the stroke slider so it stays user-adjustable. */
private fun textSizePx(s: DrawingSettings): Float = 24f + s.strokeWidth * 6f

// ── Text input ────────────────────────────────────────────────────────────────

@Composable
private fun TextAnnotationDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.draw_add_text_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                placeholder = { Text(stringResource(R.string.draw_text_note_hint)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ── Rendering ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawFreehandStroke(annotation: Annotation) {
    val points = deserializePoints(annotation.points)
    if (points.size < 2) return
    val color = Color(annotation.color).copy(alpha = annotation.opacity)
    for (i in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = Offset(points[i].x * size.width, points[i].y * size.height),
            end = Offset(points[i + 1].x * size.width, points[i + 1].y * size.height),
            strokeWidth = annotation.strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawHighlight(annotation: Annotation) {
    val b = deserializeBoundingBox(annotation.boundingBox) ?: return
    val x = b["x"] ?: return; val y = b["y"] ?: return
    val w = b["w"] ?: return; val h = b["h"] ?: return
    drawRect(
        color = Color(annotation.color).copy(alpha = annotation.opacity * 0.4f),
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(w * size.width, h * size.height)
    )
}

private fun DrawScope.drawRectangleShape(annotation: Annotation) {
    val b = deserializeBoundingBox(annotation.boundingBox) ?: return
    val x = b["x"] ?: return; val y = b["y"] ?: return
    val w = b["w"] ?: return; val h = b["h"] ?: return
    drawRect(
        color = Color(annotation.color).copy(alpha = annotation.opacity),
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(w * size.width, h * size.height),
        style = Stroke(width = annotation.strokeWidth, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawCircleShape(annotation: Annotation) {
    val b = deserializeBoundingBox(annotation.boundingBox) ?: return
    val x = b["x"] ?: return; val y = b["y"] ?: return; val r = b["r"] ?: return
    drawCircle(
        color = Color(annotation.color).copy(alpha = annotation.opacity),
        center = Offset(x * size.width, y * size.height),
        radius = r * size.width,
        style = Stroke(width = annotation.strokeWidth)
    )
}

private fun DrawScope.drawTextAnnotation(annotation: Annotation) {
    if (annotation.textContent.isBlank()) return
    val b = deserializeBoundingBox(annotation.boundingBox) ?: return
    val x = b["x"] ?: return; val y = b["y"] ?: return
    val sizePx = parseMetaFloat(annotation.metadata, "size") ?: 42f
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = annotation.color
        alpha = (annotation.opacity * 255).toInt().coerceIn(0, 255)
        textSize = sizePx
    }
    // Draw baseline a little below the tap point so the text sits where tapped.
    drawContext.canvas.nativeCanvas.drawText(
        annotation.textContent,
        x * size.width,
        y * size.height + sizePx,
        paint
    )
}

private fun DrawScope.drawPreview(points: List<StrokePoint>, settings: DrawingSettings) {
    val color = Color(settings.color).copy(alpha = settings.opacity)
    when (settings.tool) {
        DrawingTool.HIGHLIGHT -> boundingBox(points)?.let { (x, y, w, h) ->
            drawRect(
                color = Color(settings.color).copy(alpha = settings.opacity * 0.4f),
                topLeft = Offset(x * size.width, y * size.height),
                size = Size(w * size.width, h * size.height)
            )
        }
        DrawingTool.RECTANGLE, DrawingTool.ARROW -> boundingBox(points)?.let { (x, y, w, h) ->
            drawRect(
                color = color,
                topLeft = Offset(x * size.width, y * size.height),
                size = Size(w * size.width, h * size.height),
                style = Stroke(width = settings.strokeWidth)
            )
        }
        DrawingTool.CIRCLE -> boundingBox(points)?.let { (x, y, w, h) ->
            drawCircle(
                color = color,
                center = Offset((x + w / 2f) * size.width, (y + h / 2f) * size.height),
                radius = max(w, h) / 2f * size.width,
                style = Stroke(width = settings.strokeWidth)
            )
        }
        else -> {
            if (points.size < 2) return
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = Offset(points[i].x * size.width, points[i].y * size.height),
                    end = Offset(points[i + 1].x * size.width, points[i + 1].y * size.height),
                    strokeWidth = settings.strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// ── Geometry / serialization ──────────────────────────────────────────────────

private data class Bounds(val x: Float, val y: Float, val w: Float, val h: Float)

private fun boundingBox(points: List<StrokePoint>): Bounds? {
    if (points.isEmpty()) return null
    val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
    return Bounds(minX, minY, abs(maxX - minX), abs(maxY - minY))
}

private fun distanceBetween(p1: StrokePoint, p2: StrokePoint): Float {
    val dx = p2.x - p1.x; val dy = p2.y - p1.y
    return sqrt(dx * dx + dy * dy)
}

private fun serializePoints(points: List<StrokePoint>): String =
    "[${points.joinToString(",") { "[${it.x},${it.y},${it.pressure}]" }}]"

private fun deserializePoints(json: String): List<StrokePoint> {
    if (json.isEmpty()) return emptyList()
    return try {
        val clean = json.trim()
        if (!clean.startsWith("[") || !clean.endsWith("]")) return emptyList()
        val inner = clean.substring(1, clean.length - 1)
        if (inner.isEmpty()) return emptyList()
        inner.split("],").mapNotNull { pointStr ->
            val parts = pointStr.trim().trim('[').trim(']').split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size >= 2) StrokePoint(parts[0], parts[1], if (parts.size > 2) parts[2] else 1f) else null
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun serializeBoundingBox(x: Float, y: Float, width: Float, height: Float): String =
    "{\"x\":$x,\"y\":$y,\"w\":$width,\"h\":$height}"

private fun serializeBoundingBoxCircle(x: Float, y: Float, radius: Float): String =
    "{\"x\":$x,\"y\":$y,\"r\":$radius}"

private fun deserializeBoundingBox(json: String): Map<String, Float>? {
    if (json.isEmpty()) return null
    return try {
        val clean = json.trim()
        if (!clean.startsWith("{") || !clean.endsWith("}")) return null
        clean.substring(1, clean.length - 1).split(",").associate { pair ->
            val (key, value) = pair.split(":").map { it.trim().trim('"') }
            key to (value.toFloatOrNull() ?: 0f)
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseMetaFloat(metadata: String, key: String): Float? =
    Regex("$key=([0-9.]+)").find(metadata)?.groupValues?.get(1)?.toFloatOrNull()
