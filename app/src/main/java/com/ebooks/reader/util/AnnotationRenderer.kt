package com.ebooks.reader.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ebooks.reader.data.db.entities.Annotation

/**
 * Renders a page's stored annotations to a standalone [Bitmap] so they can be
 * shared as an image. Pure `android.graphics` (no Compose), mirroring how
 * `DrawingCanvas` paints each annotation type on screen. Coordinates in the
 * stored data are normalized [0..1] and scaled to the bitmap size here.
 */
fun renderAnnotationsToBitmap(
    annotations: List<Annotation>,
    width: Int = 1080,
    height: Int = 1920,
    background: Int = Color.WHITE
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)

    for (annotation in annotations) {
        if (annotation.isDeleted) continue
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = (annotation.opacity * 255).toInt().coerceIn(0, 255)
            strokeWidth = annotation.strokeWidth
        }
        when (annotation.annotationType) {
            "freehand" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                val pts = parsePoints(annotation.points)
                for (i in 0 until pts.size - 1) {
                    canvas.drawLine(
                        pts[i].first * width, pts[i].second * height,
                        pts[i + 1].first * width, pts[i + 1].second * height, paint
                    )
                }
            }
            "highlight" -> parseBounds(annotation.boundingBox)?.let { b ->
                paint.style = Paint.Style.FILL
                paint.alpha = (annotation.opacity * 0.4f * 255).toInt().coerceIn(0, 255)
                canvas.drawRect(
                    b.getValue("x") * width, b.getValue("y") * height,
                    (b.getValue("x") + b.getValue("w")) * width,
                    (b.getValue("y") + b.getValue("h")) * height, paint
                )
            }
            "rectangle" -> parseBounds(annotation.boundingBox)?.let { b ->
                paint.style = Paint.Style.STROKE
                canvas.drawRect(
                    b.getValue("x") * width, b.getValue("y") * height,
                    (b.getValue("x") + b.getValue("w")) * width,
                    (b.getValue("y") + b.getValue("h")) * height, paint
                )
            }
            "circle" -> parseBounds(annotation.boundingBox)?.let { b ->
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(b.getValue("x") * width, b.getValue("y") * height, b.getValue("r") * width, paint)
            }
            "text" -> parseBounds(annotation.boundingBox)?.let { b ->
                if (annotation.textContent.isBlank()) return@let
                paint.style = Paint.Style.FILL
                paint.textSize = parseMetaFloat(annotation.metadata, "size") ?: 42f
                canvas.drawText(annotation.textContent, b.getValue("x") * width, b.getValue("y") * height + paint.textSize, paint)
            }
        }
    }
    return bitmap
}

private fun parsePoints(json: String): List<Pair<Float, Float>> {
    if (json.length < 2) return emptyList()
    val inner = json.trim().removeSurrounding("[", "]")
    if (inner.isEmpty()) return emptyList()
    return inner.split("],").mapNotNull { p ->
        val parts = p.trim().trim('[', ']').split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (parts.size >= 2) parts[0] to parts[1] else null
    }
}

private fun parseBounds(json: String): Map<String, Float>? {
    if (json.length < 2) return null
    return runCatching {
        json.trim().removeSurrounding("{", "}").split(",").associate { pair ->
            val (k, v) = pair.split(":").map { it.trim().trim('"') }
            k to (v.toFloatOrNull() ?: 0f)
        }
    }.getOrNull()
}

private fun parseMetaFloat(metadata: String, key: String): Float? =
    Regex("$key=([0-9.]+)").find(metadata)?.groupValues?.get(1)?.toFloatOrNull()
