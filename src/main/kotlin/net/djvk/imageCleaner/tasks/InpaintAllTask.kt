package net.djvk.imageCleaner.tasks

import javafx.concurrent.Task
import kotlinx.coroutines.sync.Mutex
import net.djvk.imageCleaner.constants.NEGATIVE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.OUTPUT_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.SOURCE_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.sep
import net.djvk.imageCleaner.inpaint.Inpainter
import net.djvk.imageCleaner.matching.ObjectMatcher
import net.djvk.imageCleaner.util.DsStoreFilenameFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.io.path.exists

private const val LOGGER_PREFIX = "Inpainter task"

/**
 * For each source file:
 *  - Determines matches based on passed in match mode
 *  - Executes inpaint operation on the matches from the previous step
 *  - Writes the result to the output directory
 */
class InpaintAllTask(
    private val workingDirectory: Path,
    private val mutex: Mutex,
    private val matcher: ObjectMatcher,
) : Task<Unit>() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val imageReadProgress = AtomicInteger(0)
    private val inpaintProgress = AtomicInteger(0)
    private val imageWriteProgress = AtomicInteger(0)

    /**
     * @return Returns a list of image file names. They will be stored in $workingDirectory/source.
     *  The order of the list reflects the order that the files were read from the input directory.
     */
    override fun call() {
        val outputDirectory = workingDirectory.resolve(OUTPUT_DIRECTORY_NAME)
        if (!outputDirectory.exists()) {
            outputDirectory.toFile().mkdir()
        }

        // Read file names
        val files = workingDirectory
            .resolve(SOURCE_DIRECTORY_NAME)
            .toFile()
            .listFiles(DsStoreFilenameFilter)
            ?: throw IllegalArgumentException("Source directory empty")

        val fileCount = files.size
        logger.debug("$fileCount source files to inpaint")

        // Parallel process images
        files
            .toList()
            .stream()
            .parallel()
            .map { file ->
                // Read source files into memory
                logger.debug("Loading source image from $file")
                val img = ImageIO.read(file)
                updateProgress(
                    (imageReadProgress.incrementAndGet() + inpaintProgress.get() +
                            imageWriteProgress.get()).toLong(),
                    (fileCount * 3).toLong()
                )
                Pair(img, file)
            }
            .map { (img, file) ->
                // Inpaint images
                val matches = matcher.match(file.toPath(), img)
                if (matches.isEmpty()) {
                    return@map Pair(img, file)
                }
                val inpainter = Inpainter(
                    img,
                    matches,
                )
                val inpaintedImage = inpainter.inpaint()

                updateProgress(
                    (imageReadProgress.get() + inpaintProgress.incrementAndGet() +
                            imageWriteProgress.get()).toLong(),
                    (fileCount * 3).toLong()
                )
                Pair(inpaintedImage, file)
            }
            .map { (inpaintedImage, file) ->
                // Write resulting images
                val outFullPath = workingDirectory
                    .resolve(OUTPUT_DIRECTORY_NAME)
                    .resolve(file.name)

                ImageIO.write(inpaintedImage, "jpg", outFullPath.toFile())

                updateProgress(
                    (imageReadProgress.get() + inpaintProgress.get() +
                            imageWriteProgress.incrementAndGet()).toLong(),
                    (fileCount * 3).toLong()
                )
            }.toList()
    }

    override fun succeeded() {
        super.succeeded()

        logger.info("$LOGGER_PREFIX succeeded")
        mutex.unlock()
    }

    override fun cancelled() {
        super.cancelled()

        logger.info("$LOGGER_PREFIX cancelled")
        mutex.unlock()
    }

    override fun failed() {
        super.failed()

        logger.error("$LOGGER_PREFIX failed", exception)
        mutex.unlock()
    }
}