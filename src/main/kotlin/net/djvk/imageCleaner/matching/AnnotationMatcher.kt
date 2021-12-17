package net.djvk.imageCleaner.matching

import net.djvk.imageCleaner.annotation.read.PositiveAnnotationFileReader
import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * A fake-ish matcher that returns matches based on manual positive annotations.
 */
class AnnotationMatcher(
    private val workingDirectory: Path,
) : ObjectMatcher {
    override fun match(imagePath: Path, img: BufferedImage): List<ObjectMatch> {
        val reader = PositiveAnnotationFileReader(workingDirectory, imagePath)
        return reader.readAnnotations().map { annotationSelection ->
            AnnotationMatch(
                annotationSelection.rect.x,
                annotationSelection.rect.y,
                annotationSelection.rect.width,
                annotationSelection.rect.height,
            )
        }
    }
}