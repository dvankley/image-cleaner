package net.djvk.imageCleaner.util

import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

fun buildAnnotationRectangle(strokeColor: Color): Rectangle {
    val rect = Rectangle()
    rect.fill = Color.TRANSPARENT
    rect.stroke = strokeColor
    rect.strokeWidth = 5.0
    return rect
}