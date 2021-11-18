package net.djvk.imageCleaner.tasks

import javafx.concurrent.Task
import javafx.scene.control.ProgressBar
import kotlinx.coroutines.sync.Mutex
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import javax.imageio.ImageIO
import kotlin.io.path.extension


class InputImageLoaderTask(
    private val inputDirectory: Path,
    private val mutex: Mutex,
    private val progressBar: ProgressBar,
) : Task<List<BufferedImage>>() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val progress = AtomicInteger(0)

    override fun call(): List<BufferedImage> {
        // Read file names
        val files = Files.list(inputDirectory).toList()
        val fileCount = files.size
        logger.debug("$fileCount input files to read")
//            .toFlux()

        // Parallel load images from files
        return files
            .stream()
            .parallel()
            .filter {
                // Filter out goofy files like .DS_Store
                !it.fileName.toString().startsWith(".")
            }
            .flatMap { path ->
                logger.debug("Loading input image from $path")
                val img = readImages(path.toFile())
                val prog = progress.incrementAndGet()
                updateProgress(prog.toLong(), fileCount.toLong())
                img
            }
            .toList()

//        return runBlocking {
//            files.map { path ->
//                logger.debug("Loading input image from $path")
//                val img = readImages(path.toFile())
//                val prog = progress.incrementAndGet()
//                updateProgress(prog.toLong(), fileCount)
//                img
//            }
//        }
//            .flatMap {  }
//            .toList()
    }

    override fun succeeded() {
        super.succeeded()

        logger.info("Input file loader task succeeded")
        mutex.unlock()
        progressBar.isVisible = false
    }

    override fun cancelled() {
        super.cancelled()

        logger.info("Input file loader task cancelled")
        mutex.unlock()
        progressBar.isVisible = false
    }

    override fun failed() {
        super.failed()

        logger.error("Input file loader task failed", exception)
        mutex.unlock()
        progressBar.isVisible = false
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