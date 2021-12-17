package net.djvk.imageCleaner.matching

import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * A fake-ish matcher that returns matches based on manual positive annotations.
 */
class AnnotationMatcher : ObjectMatcher {
    override fun match(imagePath: Path, img: BufferedImage): List<ObjectMatch> {
        TODO("Not yet implemented")
    }
}