package net.djvk.imageCleaner.annotation.write

import net.djvk.imageCleaner.ui.ParentController
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

abstract class AnnotationFileWriter(
    val workingDirectory: Path,
    val imageFile: Path,
    val image: BufferedImage,
) {
    abstract fun writeAnnotations(annotations: List<ParentController.AnnotationSelection>)

    /**
     * @param lineFilterFunction Returns true to keep a line, false to remove it
     */
    protected fun rewriteFile(
        path: Path,
        linesToAdd: List<String>,
        lineFilterFunction: (String) -> Boolean,
    ) {
        val outputFileContent = run {
            try {
                Files.readAllLines(path)
                    .filter(lineFilterFunction)
                    .toMutableList()
            } catch (e: IOException) {
                return@run mutableListOf()
            }
        }
        outputFileContent.addAll(linesToAdd)

        Files.write(path, outputFileContent)
    }
}