package net.djvk.imageCleaner.inpaint

import net.djvk.imageCleaner.matching.ObjectMatch
import net.djvk.imageCleaner.util.OpenCvUtilities
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import java.awt.image.BufferedImage

private val BLACK = Scalar(0.0)
private val WHITE = Scalar(255.0)

private const val INPAINT_MODE = Photo.INPAINT_TELEA

class Inpainter(
    private val img: BufferedImage,
    private val mask: List<ObjectMatch>,
) {
    fun inpaint(): BufferedImage {
        val inputMat = OpenCvUtilities.bufferedImageToMat(img)

        val maskMat = Mat()
        maskMat.create(img.height, img.width, CvType.CV_8U)
        maskMat.setTo(BLACK)
        mask.forEach { rect ->
            Imgproc.rectangle(maskMat, rect.toOpenCvRect(), WHITE, -1)
        }
//        val maskPreview1 = OpenCvUtilities.matToBufferedImage(maskMat)
        val outMat = Mat(img.height, img.width, CvType.CV_8UC3)
//        val outPreview = OpenCvUtilities.matToBufferedImage(outMat)

        Photo.inpaint(
            inputMat,
            maskMat,
            outMat,
            1.0,
            INPAINT_MODE,
        )

//        val outPreview2 = OpenCvUtilities.matToBufferedImage(outMat)
        return OpenCvUtilities.matToBufferedImage(outMat)
    }
}