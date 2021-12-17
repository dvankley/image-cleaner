package net.djvk.imageCleaner.matching

import org.opencv.core.Rect

interface ObjectMatch {
    val x: Double
    val y: Double
    val width: Double
    val height: Double

    fun toOpenCvRect(): Rect {
        return Rect(
            x.toInt(),
            y.toInt(),
            width.toInt(),
            height.toInt(),
        )
    }
}
