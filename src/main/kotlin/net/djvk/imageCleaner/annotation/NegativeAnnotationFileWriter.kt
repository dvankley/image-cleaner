package net.djvk.imageCleaner.annotation

import net.djvk.imageCleaner.constants.NEGATIVE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.sep
import net.djvk.imageCleaner.ui.ParentController
import org.apache.commons.io.FilenameUtils
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.roundToInt

private const val NEGATIVE_ANNOTATION_FILENAME = "neg.txt"

class NegativeAnnotationFileWriter(
    workingDirectory: Path,
    imageFile: Path,
    image: BufferedImage,
) : AnnotationFileWriter(
    workingDirectory,
    imageFile,
    image,
) {
    /**
     * Write negative annotations to a file per https://docs.opencv.org/3.4/dc/d88/tutorial_traincascade.html
     */
    override fun writeAnnotations(annotations: List<ParentController.AnnotationSelection>) {
        val negativeImageDirectory = Paths.get("$workingDirectory$sep$NEGATIVE_DIRECTORY_NAME")
        if (!negativeImageDirectory.exists()) {
            negativeImageDirectory.toFile().mkdir()
        }

        val existingNegativeAnnotationFiles = negativeImageDirectory.toFile().listFiles { _, name ->
            name.startsWith(FilenameUtils.removeExtension(imageFile.name))
        }?.toList()
            ?: throw RuntimeException("Failed to list files in $negativeImageDirectory matching filename prefix $imageFile")

        val existingNegativeAnnotationFilenames = existingNegativeAnnotationFiles.map { it.name }.toSet()

        /**
         * Delete all the old annotation files
         * We can't diff them effectively so we're just going to delete and recreate them all
         */
        existingNegativeAnnotationFiles.forEach { it.delete() }

        val newFilenames = mutableListOf<String>()
        for (annotation in annotations) {
            val newFilename = cutAndWriteAnnotation(negativeImageDirectory, annotation)
            newFilenames.add(newFilename)
        }

        rewriteFile(workingDirectory.resolve(NEGATIVE_ANNOTATION_FILENAME), newFilenames) { line ->
            !existingNegativeAnnotationFilenames.contains(line)
        }
    }

    private fun cutAndWriteAnnotation(targetDirectory: Path, annotation: ParentController.AnnotationSelection): String {
        // Slice out the selected box from the main annotating image
        val sub = image.getSubimage(
            annotation.rect.x.roundToInt(),
            annotation.rect.y.roundToInt(),
            annotation.rect.width.roundToInt(),
            annotation.rect.height.roundToInt()
        )

        // Write it to a file
        val filename = "${imageFile.name}_${System.currentTimeMillis()}.jpg"
        ImageIO.write(sub, "jpg", File("${targetDirectory.pathString}$sep$filename"))
        return filename
    }
}