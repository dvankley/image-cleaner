package net.djvk.imageCleaner.tasks

import javafx.concurrent.Task
import kotlinx.coroutines.sync.Mutex
import net.djvk.imageCleaner.constants.InputTaskResult
import net.djvk.imageCleaner.constants.SOURCE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.sep
import net.djvk.imageCleaner.util.DsStoreFilenameFilter
import org.apache.commons.io.FilenameUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import javax.imageio.ImageIO

/**
 * - Loads and parses input images from their source files
 * - Outputs them in a consistent format to the working directory
 * - Stores their metadata in memory for later access
 */
class InputImageLoaderTask(
    private val inputDirectory: Path,
    private val workingDirectory: Path,
    private val mutex: Mutex,
) : Task<List<InputTaskResult>>() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val imageReadProgress = AtomicInteger(0)
    private val imageWriteProgress = AtomicInteger(0)

    private data class IntermediateImage(
        val img: BufferedImage,
        val inputFilename: String,
        val index: Int,
    )

    /**
     * @return Returns a list of image file names. They will be stored in $workingDirectory/source.
     *  The order of the list reflects the order that the files were read from the input directory.
     */
    override fun call(): List<InputTaskResult> {
        // Read file names
        val files = inputDirectory.toFile().listFiles(DsStoreFilenameFilter)
            ?: throw IllegalArgumentException("Input directory empty")
        val fileCount = files.size
        logger.debug("$fileCount input files to read")
//            .toFlux()

        // Parallel load images from files
        return files
            .toList()
            .stream()
            .parallel()
            .flatMap { file ->
                logger.debug("Loading input image from $file")
                val imgStream = readImages(file)
                val prog = imageReadProgress.incrementAndGet()
                updateProgress(prog.toLong(), (fileCount * 2).toLong())
                var index = 0
                imgStream.map { img ->
                    IntermediateImage(
                        img, file.name
                            .toString()
                            .replace(' ', '_'), index++
                    )
                }
            }
            .map { img ->
                val filename = "${FilenameUtils.removeExtension(img.inputFilename)}-${img.index}.jpg"
                logger.debug("Writing input image $filename to source directory")
                ImageIO.write(
                    img.img,
                    "jpg",
                    File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME$sep$filename")
                )
                val prog = imageWriteProgress.incrementAndGet()
                updateProgress((fileCount + prog).toLong(), (fileCount * 2).toLong())
                filename
            }
            .toList()
    }

    override fun succeeded() {
        super.succeeded()

        logger.info("Input file loader task succeeded")
        mutex.unlock()
    }

    override fun cancelled() {
        super.cancelled()

        logger.info("Input file loader task cancelled")
        mutex.unlock()
    }

    override fun failed() {
        super.failed()

        logger.error("Input file loader task failed", exception)
        mutex.unlock()
    }

    private fun readImages(file: File): Stream<BufferedImage> {
        when (file.extension) {
            "jpg", "jpeg", "png", "bmp" -> {
                return listOf(ImageIO.read(file)).stream()
            }
            "pdf" -> {
                PDDocument.load(file).use { document ->
                    val images: MutableList<BufferedImage> = ArrayList()
                    for (page in document.pages) {
                        images.addAll(getImagesFromPdfResources(page.resources))
                    }
                    return images.stream()
                }
            }
            else -> throw IllegalArgumentException("Invalid extension for file $file")
        }
    }

    private fun getImagesFromPdfResources(resources: PDResources): List<BufferedImage> {
        val images: MutableList<BufferedImage> = ArrayList()
        for (xObjectName in resources.xObjectNames) {
            val xObject = resources.getXObject(xObjectName)
            if (xObject is PDFormXObject) {
                images.addAll(getImagesFromPdfResources(xObject.resources))
            } else if (xObject is PDImageXObject) {
                images.add(xObject.image)
            }
        }
        return images
    }
}