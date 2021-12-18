package net.djvk.imageCleaner.tasks

import javafx.concurrent.Task
import javafx.scene.input.KeyEvent
import kotlinx.coroutines.sync.Mutex
import net.djvk.imageCleaner.constants.OUTPUT_DIRECTORY_NAME
import net.djvk.imageCleaner.constants.SOURCE_DIRECTORY_NAME
import net.djvk.imageCleaner.matching.ObjectMatcher
import net.djvk.imageCleaner.painting.Inpainter
import net.djvk.imageCleaner.painting.PageNumberer
import net.djvk.imageCleaner.util.DsStoreFilenameFilter
import org.apache.commons.io.FilenameUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
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
    private val addPageNumbers: Boolean,
) : Task<Unit>() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val imageReadProgress = AtomicInteger(0)
    private val inpaintProgress = AtomicInteger(0)
    private val imageWriteProgress = AtomicInteger(0)

    private val numberer = PageNumberer()

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

        files.sortWith(sortFilesByName)

        val fileCount = files.size
        logger.debug("$fileCount source files to inpaint")

        // Parallel process images
        files
            .toList()
            .mapIndexed { index, file -> Pair(file, index) }
            .stream()
            .parallel()
            .map { (file, index) ->
                // Read source files into memory
                logger.debug("Loading source image from $file")
                val img = ImageIO.read(file)
                updateProgress(
                    (imageReadProgress.incrementAndGet() + inpaintProgress.get() +
                            imageWriteProgress.get()).toLong(),
                    (fileCount * 3).toLong()
                )
                Triple(img, file, index)
            }
            .map { (img, file, index) ->
                // Inpaint images
                val matches = matcher.match(file.toPath(), img)
                if (matches.isEmpty()) {
                    return@map Triple(img, file, index)
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
                Triple(inpaintedImage, file, index)
            }
            .map { (img, file, index) ->
                // Add page numbers if needed
                // One could argue that this should be in a separate stage, but I'm lazy
                if (addPageNumbers) {
                    numberer.addPageNumber(img, index)
                }

                // Write resulting images
                val outFullPath = workingDirectory
                    .resolve(OUTPUT_DIRECTORY_NAME)
                    .resolve(file.name)

                ImageIO.write(img, "jpg", outFullPath.toFile())

                updateProgress(
                    (imageReadProgress.get() + inpaintProgress.get() +
                            imageWriteProgress.incrementAndGet()).toLong(),
                    (fileCount * 3).toLong()
                )
            }.toList()
    }

    /**
     * This sorting function is specific to my file name formats, change it as needed.
     *
     */
    private val sortFilesByName = Comparator<File> { file1, file2 ->
        val (prefix1, suffix1) = getFilenameParts(FilenameUtils.removeExtension(file1.name))
        val (prefix2, suffix2) = getFilenameParts(FilenameUtils.removeExtension(file2.name))
        if (prefix1 != prefix2) {
            return@Comparator if (prefix1 > prefix2) 1 else -1
        }
        return@Comparator if (suffix1 > suffix2) 1 else -1
    }

    private fun getFilenameParts(name: String): Pair<Int, Int> {
        val underscoreParts = name.split('_')
        val prefix = underscoreParts[0].toInt()
        val hyphenParts = name.split('-')
        val suffix = hyphenParts.last().toInt()
        return Pair(prefix, suffix)
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