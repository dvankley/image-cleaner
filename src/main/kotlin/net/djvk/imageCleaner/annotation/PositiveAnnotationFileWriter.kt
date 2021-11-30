package net.djvk.imageCleaner.annotation

import net.djvk.imageCleaner.ui.ParentController
import java.awt.image.BufferedImage
import java.nio.file.Path

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

    }
}