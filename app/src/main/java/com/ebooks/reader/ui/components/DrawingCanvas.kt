package com.ebooks.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.ebooks.reader.data.db.entities.Annotation
import kotlin.math.sqrt

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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val startX = down.position.x / canvasWidth
                    val startY = down.position.y / canvasHeight
                    val pressure = down.pressure

                    pendingStroke = listOf(StrokePoint(startX, startY, pressure))

                    var lastPoint = StrokePoint(startX, startY, pressure)

                    while (true) {
                        val event = awaitPointerEvent()
                        val allPoints = event.changes.flatMap { change ->
                            val normalizedX = change.position.x / canvasWidth
                            val normalizedY = change.position.y / canvasHeight
                            val point = StrokePoint(normalizedX, normalizedY, change.pressure)

                            if (distanceBetween(lastPoint, point) > 0.001f) {
                                lastPoint = point
                                listOf(point)
                            } else {
                                emptyList()
                            }
                        }

                        if (allPoints.isNotEmpty()) {
                            pendingStroke = pendingStroke + allPoints
                        }

                        event.changes.forEach { it.consume() }

                        if (event.changes.any { !it.pressed }) {
                            break
                        }
                    }

                    if (pendingStroke.isNotEmpty()) {
                        val annotation = Annotation(
                            id = java.util.UUID.randomUUID().toString(),
                            bookId = "",
                            pageIdentifier = "",
                            pageIndex = 0,
                            annotationType = "freehand",
                            color = settings.color,
                            strokeWidth = settings.strokeWidth,
                            opacity = settings.opacity,
                            points = serializePoints(pendingStroke),
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = System.currentTimeMillis()
                        )
                        onStrokeCompleted(annotation)
                    }

                    pendingStroke = emptyList()
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
            }
        }

        if (pendingStroke.isNotEmpty()) {
            drawPendingStroke(pendingStroke, settings)
        }
    }
}

private fun DrawScope.drawFreehandStroke(annotation: Annotation) {
    val points = deserializePoints(annotation.points)
    if (points.size < 2) return

    val color = Color(annotation.color)
    val stroke = Stroke(
        width = annotation.strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        drawLine(
            color = color.copy(alpha = annotation.opacity),
            start = Offset(p1.x * size.width, p1.y * size.height),
            end = Offset(p2.x * size.width, p2.y * size.height),
            strokeWidth = annotation.strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawHighlight(annotation: Annotation) {
    val bounds = deserializeBoundingBox(annotation.boundingBox)
    if (bounds == null) return

    val x = bounds["x"] as? Float ?: return
    val y = bounds["y"] as? Float ?: return
    val w = bounds["w"] as? Float ?: return
    val h = bounds["h"] as? Float ?: return

    val baseColor = Color(annotation.color)
    val highlightAlpha = (annotation.opacity * 0.7f).coerceAtLeast(0.3f)

    drawRect(
        color = baseColor.copy(alpha = highlightAlpha),
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(w * size.width, h * size.height)
    )
}

private fun DrawScope.drawRectangleShape(annotation: Annotation) {
    val bounds = deserializeBoundingBox(annotation.boundingBox)
    if (bounds == null) return

    val x = bounds["x"] as? Float ?: return
    val y = bounds["y"] as? Float ?: return
    val w = bounds["w"] as? Float ?: return
    val h = bounds["h"] as? Float ?: return

    val color = Color(annotation.color).copy(alpha = annotation.opacity)
    val strokeWidth = annotation.strokeWidth.coerceAtLeast(2f)

    drawRect(
        color = color,
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(w * size.width, h * size.height),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawCircleShape(annotation: Annotation) {
    val bounds = deserializeBoundingBox(annotation.boundingBox)
    if (bounds == null) return

    val x = bounds["x"] as? Float ?: return
    val y = bounds["y"] as? Float ?: return
    val r = bounds["r"] as? Float ?: return

    val color = Color(annotation.color).copy(alpha = annotation.opacity)
    val strokeWidth = annotation.strokeWidth.coerceAtLeast(2f)

    drawCircle(
        color = color,
        center = Offset(x * size.width, y * size.height),
        radius = r * size.width,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawPendingStroke(points: List<StrokePoint>, settings: DrawingSettings) {
    if (points.size < 2) return

    val color = Color(settings.color)

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        drawLine(
            color = color.copy(alpha = settings.opacity),
            start = Offset(p1.x * size.width, p1.y * size.height),
            end = Offset(p2.x * size.width, p2.y * size.height),
            strokeWidth = settings.strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun distanceBetween(p1: StrokePoint, p2: StrokePoint): Float {
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    return sqrt(dx * dx + dy * dy)
}

private fun serializePoints(points: List<StrokePoint>): String {
    val items = points.map { "[${it.x},${it.y},${it.pressure}]" }
    return "[${items.joinToString(",")}]"
}

private fun deserializePoints(json: String): List<StrokePoint> {
    if (json.isEmpty()) return emptyList()
    return try {
        val cleanJson = json.trim()
        if (!cleanJson.startsWith("[") || !cleanJson.endsWith("]")) return emptyList()

        val inner = cleanJson.substring(1, cleanJson.length - 1)
        if (inner.isEmpty()) return emptyList()

        inner.split("],").map { pointStr ->
            val trimmed = pointStr.trim().trim('[').trim(']')
            val parts = trimmed.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size >= 2) {
                StrokePoint(parts[0], parts[1], if (parts.size > 2) parts[2] else 1f)
            } else {
                null
            }
        }.filterNotNull()
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
        val cleanJson = json.trim()
        if (!cleanJson.startsWith("{") || !cleanJson.endsWith("}")) return null

        val inner = cleanJson.substring(1, cleanJson.length - 1)
        val pairs = inner.split(",").map { pair ->
            val (key, value) = pair.split(":").map { it.trim().trim('"') }
            key to value.toFloatOrNull()
        }
        pairs.associate { it.first to (it.second ?: 0f) }
    } catch (_: Exception) {
        null
    }
}
