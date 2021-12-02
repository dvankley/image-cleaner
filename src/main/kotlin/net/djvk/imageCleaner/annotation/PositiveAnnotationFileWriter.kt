package net.djvk.imageCleaner.annotation

import net.djvk.imageCleaner.constants.SOURCE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.sep
import net.djvk.imageCleaner.ui.ParentController
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.name

private const val POSITIVE_ANNOTATION_FILENAME = "pos.txt"

class PositiveAnnotationFileWriter(
    workingDirectory: Path,
    imageFile: Path,
    image: BufferedImage,
) : AnnotationFileWriter(
    workingDirectory,
    imageFile,
    image,
) {
    /**
     * Write positive annotations to a file per https://docs.opencv.org/3.4/dc/d88/tutorial_traincascade.html
     */
    override fun writeAnnotations(annotations: List<ParentController.AnnotationSelection>) {
        val linesToWrite = mutableListOf<String>()
        if (annotations.isNotEmpty()) {
            val out = StringBuilder("${SOURCE_DIRECTORY_NAME}$sep${imageFile.name}  ${annotations.size}")
            for (annotation in annotations) {
                out.append("  ${annotation.rect.x.toInt()} ${annotation.rect.y.toInt()} ${annotation.rect.width.toInt()} ${annotation.rect.height.toInt()} ")
            }
            linesToWrite.add(out.toString())
        }

        rewriteFile(workingDirectory.resolve(POSITIVE_ANNOTATION_FILENAME), linesToWrite) { line ->
            !line.startsWith("${SOURCE_DIRECTORY_NAME}$sep${imageFile.name}")
        }
    }
}