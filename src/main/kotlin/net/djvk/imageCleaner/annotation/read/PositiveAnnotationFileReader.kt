package net.djvk.imageCleaner.annotation.read

import javafx.scene.shape.Rectangle
import net.djvk.imageCleaner.constants.POSITIVE_ANNOTATION_FILENAME
import net.djvk.imageCleaner.constants.SOURCE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.sep
import net.djvk.imageCleaner.ui.ParentController
import net.djvk.imageCleaner.util.buildAnnotationRectangle
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

private const val POSITIVE_ANNOTATION_PART_COUNT = 4

class PositiveAnnotationFileReader(
    workingDirectory: Path,
    imageFile: Path,
) : AnnotationFileReader(
    workingDirectory,
    imageFile,
) {
    /**
     * Reads positive annotations from a file per https://docs.opencv.org/3.4/dc/d88/tutorial_traincascade.html
     */
    override fun readAnnotations(): List<ParentController.AnnotationSelection> {
        val positiveLine = run {
            try {
                Files.readAllLines(workingDirectory.resolve(POSITIVE_ANNOTATION_FILENAME))
                    .find { it.startsWith("${SOURCE_DIRECTORY_NAME}$sep${imageFile.name}") }
            } catch (e: IOException) {
                return listOf()
            }
        }
            ?: return listOf()

        val lineParts = positiveLine.split("  ")
        val annotationCount = lineParts[1].toInt(10)
        val annotationChunks = lineParts
            .subList(2, lineParts.size)
//            .chunked(POSITIVE_ANNOTATION_PART_COUNT)
        if (annotationChunks.size != annotationCount) {
            throw IllegalArgumentException("Failed to read positive annotations for $imageFile. File declared " +
            "$annotationCount annotations but ${annotationChunks.size} were found.")
        }
        return annotationChunks.map { chunk ->
            val (x, y, width, height) = chunk.split(' ')
            val rect = buildAnnotationRectangle(ParentController.AnnotationType.POSITIVE.color)
            rect.x = x.toDouble()
            rect.y = y.toDouble()
            rect.width = width.toDouble()
            rect.height = height.toDouble()
            ParentController.AnnotationSelection(
                ParentController.AnnotationType.POSITIVE,
                rect
            )
        }
    }
}