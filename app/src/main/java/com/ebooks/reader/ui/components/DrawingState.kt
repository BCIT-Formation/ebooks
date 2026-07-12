package com.ebooks.reader.ui.components

import android.graphics.Color

enum class DrawingTool {
    PEN, TEXT, HIGHLIGHT, RECTANGLE, CIRCLE, ARROW, ERASER
}

data class DrawingSettings(
    val tool: DrawingTool = DrawingTool.PEN,
    val color: Int = Color.YELLOW,
    val strokeWidth: Float = 3f,
    val opacity: Float = 1f,
    val fontSize: Float = 16f
)

data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f)

data class PendingStroke(
    val points: List<StrokePoint> = emptyList(),
    val tool: DrawingTool = DrawingTool.PEN,
    val color: Int = Color.BLACK,
    val strokeWidth: Float = 3f
)
