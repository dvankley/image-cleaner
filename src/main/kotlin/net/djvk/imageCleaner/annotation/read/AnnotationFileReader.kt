package net.djvk.imageCleaner.annotation.read

import net.djvk.imageCleaner.ui.ParentController
import java.nio.file.Path

abstract class AnnotationFileReader(
    val workingDirectory: Path,
    val imageFile: Path,
) {
    abstract fun readAnnotations(): List<ParentController.AnnotationSelection>
}