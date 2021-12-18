package net.djvk.imageCleaner.painting

import java.awt.Color
import java.awt.Font
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

private const val PAGE_NUMBER_BOX_SIDE_OFFSET_PIXELS = 30
private const val PAGE_NUMBER_BOX_BOTTOM_OFFSET_PIXELS = 30
private const val PAGE_NUMBER_BOX_SIDE_INSET_PIXELS = 20
private const val PAGE_NUMBER_BOX_BOTTOM_INSET_PIXELS = 20
private const val PAGE_NUMBER_FONT_SIZE_POINT = 50

class PageNumberer(
) {
    /**
     * @param pageIndex The 0-based index of this page. The page number written will be this + 1.
     */
    fun addPageNumber(
        img: BufferedImage,
        pageIndex: Int,
    ) {
        val g2d = img.createGraphics()
        try {
            g2d.paint = Color.BLACK
            g2d.font = Font("Serif", Font.BOLD, PAGE_NUMBER_FONT_SIZE_POINT)
            val pageNumberString = (pageIndex + 1).toString()
            val fm = g2d.fontMetrics
            val boxX = if (pageIndex % 2 == 0) {
                // Odd page numbers (not indexes) are in the bottom right
                img.width - fm.stringWidth(pageNumberString) - (PAGE_NUMBER_BOX_SIDE_INSET_PIXELS * 2) -
                        PAGE_NUMBER_BOX_SIDE_OFFSET_PIXELS
            } else {
                // Even page numbers (not indexes) are in the bottom left
                PAGE_NUMBER_BOX_SIDE_OFFSET_PIXELS
            }
            val boxY = img.height - fm.height - (PAGE_NUMBER_BOX_BOTTOM_INSET_PIXELS * 2) -
                    PAGE_NUMBER_BOX_BOTTOM_OFFSET_PIXELS
            val boxWidth = fm.stringWidth(pageNumberString) + (PAGE_NUMBER_BOX_SIDE_INSET_PIXELS * 2)
            val boxHeight = fm.height + (PAGE_NUMBER_BOX_BOTTOM_INSET_PIXELS * 2)
            g2d.paint = Color.WHITE
            g2d.fill(
                RoundRectangle2D.Double(
                    boxX.toDouble(),
                    boxY.toDouble(),
                    boxWidth.toDouble(),
                    boxHeight.toDouble(),
                    PAGE_NUMBER_BOX_SIDE_INSET_PIXELS.toDouble(),
                    PAGE_NUMBER_BOX_BOTTOM_INSET_PIXELS.toDouble(),
                )
            )

            val numberX = boxX + PAGE_NUMBER_BOX_SIDE_INSET_PIXELS
            // Note that this is the baseline for the text, not the top left as with most other drawing operations
            val numberY = boxY + PAGE_NUMBER_BOX_BOTTOM_INSET_PIXELS + fm.ascent
            g2d.paint = Color.BLACK
            g2d.drawString(pageNumberString, numberX, numberY)
        } finally {
            g2d.dispose()
        }
    }
}