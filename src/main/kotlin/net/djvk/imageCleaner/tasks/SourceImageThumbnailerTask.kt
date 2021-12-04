package net.djvk.imageCleaner.tasks

import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import kotlinx.coroutines.sync.Mutex
import net.coobird.thumbnailator.Thumbnails
import net.djvk.imageCleaner.constants.SOURCE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.THUMBNAIL_HEIGHT
import net.djvk.imageCleaner.constants.THUMBNAIL_WIDTH
import net.djvk.imageCleaner.util.DsStoreFilenameFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * - Loads and parses source image files
 * - Converts them into thumbnails
 */
class SourceImageThumbnailerTask(
    private val workingDirectory: Path,
    private val mutex: Mutex,
) : Task<List<ThumbnailTaskResult>>() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val imageReadProgress = AtomicInteger(0)
    private val imageThumbnailProgress = AtomicInteger(0)

    override fun call(): List<ThumbnailTaskResult> {
        // Read file names
        val files = workingDirectory
            .resolve(SOURCE_DIRECTORY_NAME)
            .toFile()
            .listFiles(DsStoreFilenameFilter)
            ?: throw IllegalArgumentException("Source directory empty")

        val fileCount = files.size
        logger.debug("$fileCount source files to thumbnail")

        // Parallel load images from files
        return files
            .toList()
            .stream()
            .parallel()
            .map { file ->
                logger.debug("Loading source image from $file")
                val img = ImageIO.read(file)
                updateProgress(
                    (imageReadProgress.incrementAndGet() + imageThumbnailProgress.get()).toLong(),
                    (fileCount * 2).toLong()
                )
                Pair(img, file)
            }
            .map { (img, file) ->
                val iv = SwingFXUtils.toFXImage(
                    Thumbnails
                        .of(img)
                        .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                        .asBufferedImage(), null
                )
                updateProgress(
                    (imageReadProgress.get() + imageThumbnailProgress.incrementAndGet()).toLong(),
                    (fileCount * 2).toLong()
                )
                ThumbnailTaskResult(
                    iv,
                    Paths.get(file.path)
                )
            }
            .toList()
    }

    override fun succeeded() {
        super.succeeded()

        logger.info("Thumbnailer task succeeded")
        mutex.unlock()
    }

    override fun cancelled() {
        super.cancelled()

        logger.info("Thumbnailer task cancelled")
        mutex.unlock()
    }

    override fun failed() {
        super.failed()

        logger.error("Thumbnailer task failed", exception)
        mutex.unlock()
    }
}