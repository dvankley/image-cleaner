package net.djvk.imageCleaner.matching

import net.djvk.imageCleaner.constants.MODEL_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.MODEL_FILENAME
import net.djvk.imageCleaner.ui.ParentController
import net.djvk.imageCleaner.util.OpenCvUtilities
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfRect
import org.opencv.core.Size
import org.opencv.objdetect.CascadeClassifier
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Returns matches based on a HAAR Cascade Classifier.
 */
class HaarMatcher(
    private val workingDirectory: Path,
    private val trainedHeight: Double,
    private val trainedWidth: Double,
) : ObjectMatcher {
    override fun match(imagePath: Path, img: BufferedImage): List<ObjectMatch> {
        // Generate annotations from the object recognition model
        val classifier = CascadeClassifier(
            workingDirectory
                .resolve(MODEL_DIRECTORY_NAME)
                .resolve(MODEL_FILENAME)
                .pathString
        )

        val mat = OpenCvUtilities.bufferedImageToMat(img)
        val objects = MatOfRect()
        val rawRejectLevels = MatOfInt()
        val rawWeights = MatOfDouble()
        classifier.detectMultiScale3(
            mat,
            objects,
            rawRejectLevels,
            rawWeights,
            1.1,
            6,
            // Allegedly this param doesn't matter for "new" cascades
            0,
            Size(trainedWidth / 2, trainedHeight / 2),
            Size(trainedWidth * 2, trainedHeight * 2),
            true,
        )

        val rejectLevels = rawRejectLevels.toList()
        val weights = rawWeights.toList()

        return objects.toList().mapIndexed { index, rect ->
            ParentController.OpenCvAnnotation(
                rect.x.toDouble(),
                rect.y.toDouble(),
                rect.width.toDouble(),
                rect.height.toDouble(),
                rejectLevels[index],
                weights[index],
            )
        }
    }
}