package net.djvk.imageCleaner.util

import org.opencv.core.CvType
import org.opencv.core.Mat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte

/**
 * Swiped from https://github.com/virgantara/opencv-java/blob/master/src/myopencv/utils/Utils.java
 */
object OpenCvUtilities {
//    private fun getSpace(mat: Mat): BufferedImage {
//        var type = 0
//        if (mat.channels() == 1) {
//            type = BufferedImage.TYPE_BYTE_GRAY
//        } else if (mat.channels() == 3) {
//            type = BufferedImage.TYPE_3BYTE_BGR
//        }
//        val w = mat.cols()
//        val h = mat.rows()
//        return BufferedImage(w, h, type)
//    }
//
//    fun getImage(mat: Mat): BufferedImage {
//        val img = getSpace(mat)
//        val raster = img.raster
//        val dataBuffer = raster.dataBuffer as DataBufferByte
//        val data = dataBuffer.data
//        mat.get(0, 0, data)
//        return img
//    }

    /**
     * @param original the [Mat] object in BGR or grayscale
     * @return the corresponding [BufferedImage]
     */
    fun matToBufferedImage(original: Mat): BufferedImage {
        // init
        val image: BufferedImage
        val width = original.width()
        val height = original.height()
        val channels = original.channels()
        val sourcePixels = ByteArray(width * height * channels)
        original.get(0, 0, sourcePixels)
        image = if (original.channels() > 1) {
            BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        }
        val targetPixels = (image.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.size)
        return image
    }

    fun bufferedImageToMat(bi: BufferedImage): Mat {
        val mat = Mat(bi.height, bi.width, CvType.CV_8UC3)
        val data = (bi.raster.dataBuffer as DataBufferByte).data
        mat.put(0, 0, data)
        return mat
    }
}