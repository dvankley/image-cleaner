package net.djvk.imageCleaner.matching

import java.awt.image.BufferedImage
import java.nio.file.Path

interface ObjectMatcher {
    fun match(imagePath: Path, img: BufferedImage): List<ObjectMatch>
}