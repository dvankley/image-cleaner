package net.djvk.imageCleaner.ui

import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.sync.Mutex
import net.coobird.thumbnailator.Thumbnails
import net.djvk.imageCleaner.constants.*
import net.djvk.imageCleaner.tasks.InputImageLoaderTask
import net.djvk.imageCleaner.util.DsStoreFilenameFilter
import net.djvk.imageCleaner.util.recursiveDeleteAllContents
import net.djvk.imageCleaner.util.unwrapOptional
import org.apache.commons.io.FilenameUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import kotlin.io.path.pathString

class ParentController {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @FXML
    lateinit var tabPane: TabPane

    val pool = Executors.newWorkStealingPool()

    //region Input Tab
    @FXML
    lateinit var tabInput: Tab

    @FXML
    lateinit var btnSelectInputDirectory: Button

    @FXML
    lateinit var txtInputDirectory: TextField

    @FXML
    lateinit var btnSelectWorkingDirectory: Button

    @FXML
    lateinit var txtWorkingDirectory: TextField

    @FXML
    lateinit var btnLoadInputFiles: Button

    lateinit var inputDirectory: Path
    lateinit var workingDirectory: Path
    //endregion

    //region Sample Tab
    @FXML
    lateinit var tabSample: Tab

    @FXML
    lateinit var prgInputLoading: ProgressBar

    @FXML
    lateinit var imgEditor: ImageView

    @FXML
    lateinit var hboxInputImages: HBox

    /**
     * Background task to load inputimages from files
     */
    var inputImageLoadingTask: Task<List<InputTaskResult>>? = null
    val inputImageLoadingMutex = Mutex()

    //endregion

    private val stage
        get() = txtInputDirectory.scene.window as Stage

    /**
     * List of source file names, with extensions.
     */
    private var sourceImages = listOf<SourceFilename>()
        /**
         * This setter is called when [sourceImages] are first loaded, either from input
         *  images or straight from the source file..
         * It updates the sample UI with thumbnails and clears existing training data.
         */
        set(value) {
            field = value

            // Update the UI with the new set of images

            // Remove the old images
            hboxInputImages.children.clear()
            // Add the new ones
            hboxInputImages.children.addAll(value.map { srcImgFilename ->
                val iv = ImageView(
                    SwingFXUtils.toFXImage(
                        Thumbnails
                            .of("$workingDirectory$sep$SOURCE_DIRECTORY_NAME$sep$srcImgFilename")
                            .size(50, 50)
                            .asBufferedImage(), null
                    )
                )
                iv.id = srcImgFilename
                iv
            })
        }

    fun initialize() {
        tabPane.selectionModel.selectedItemProperty().addListener { value, old, new ->
            when (new) {
                tabInput -> {
                }
                tabSample -> initSampleTab()
                else -> throw IllegalArgumentException("Invalid tab $new")
            }
        }
    }

    //region Input Tab
    @FXML
    private fun handleSelectInputDirectoryClick(event: MouseEvent) {
        val path = selectFile("Select Input Directory")

        if (path != null) {
            txtInputDirectory.text = path
        }
    }

    @FXML
    private fun handleSelectWorkingDirectoryClick(event: MouseEvent) {
        val path = selectFile("Select Working Directory")

        if (path != null) {
            txtWorkingDirectory.text = path
        }
    }

    private fun selectFile(title: String): String? {
        val fileChooser = FileChooser()
        fileChooser.title = title
        return fileChooser.showOpenDialog(stage)?.path
    }

    @FXML
    private fun handleLoadInputFilesClick(event: MouseEvent) {
        validateDirectorySelections()

        val inputDirectory = Paths.get(txtInputDirectory.text)
            ?: throw IllegalArgumentException("Missing expected input directory")
        val workingDirectory = Paths.get(txtWorkingDirectory.text)
            ?: throw IllegalArgumentException("Missing expected working directory")

        when (checkWorkingDirectory(workingDirectory)) {
            WorkingDirectoryStatus.DOESNT_EXIST -> {
                val alert =
                    Alert(Alert.AlertType.ERROR, "Working directory does not exist, please select a valid directory.")
                alert.showAndWait()
            }
            WorkingDirectoryStatus.EXISTS_BUT_EMPTY -> initWorkingDirectory(workingDirectory)
            WorkingDirectoryStatus.EXISTS_BUT_UNRECOGNIZED -> {
                val alert = Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Working directory contains unrecognized files; purge and continue?"
                )
                val result = unwrapOptional(alert.showAndWait())
                    ?: return
                if (result.buttonData == ButtonBar.ButtonData.OK_DONE) {
                    purgeWorkingDirectory(workingDirectory)
                    initWorkingDirectory(workingDirectory)
                } else {
                    return
                }
            }
            WorkingDirectoryStatus.EXISTS_AND_NON_EMPTY_SRC -> {
                val alert = Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Source directory already contains input files; purge and continue?"
                )
                val result = unwrapOptional(alert.showAndWait())
                    ?: return
                if (result.buttonData == ButtonBar.ButtonData.OK_DONE) {
                    purgeWorkingDirectory(workingDirectory)
                    initWorkingDirectory(workingDirectory)
                } else {
                    return
                }
            }
            WorkingDirectoryStatus.EXISTS_AND_EMPTY_SRC -> {
            }
        }

        loadInputFiles(inputDirectory, workingDirectory)
    }

    private fun validateDirectorySelections() {
        val input = txtInputDirectory.text
        val working = txtWorkingDirectory.text
        if (input == null ||
            input == "" ||
            working == null ||
            working == ""
        ) {
            val alert = Alert(Alert.AlertType.ERROR, "Input directory selection required")
            alert.showAndWait()
            tabPane.selectionModel.select(tabInput)
            return
        }
        inputDirectory = Paths.get(input)
        workingDirectory = Paths.get(working)
    }

    private enum class WorkingDirectoryStatus {
        DOESNT_EXIST,
        EXISTS_BUT_EMPTY,
        EXISTS_BUT_UNRECOGNIZED,
        EXISTS_AND_EMPTY_SRC,
        EXISTS_AND_NON_EMPTY_SRC,
    }

    private fun checkWorkingDirectory(workingDirectory: Path): WorkingDirectoryStatus {
        if (!Files.exists(workingDirectory)) {
            return WorkingDirectoryStatus.DOESNT_EXIST
        }

//        val workingDirFiles = Files.list(workingDirectory).toList()
        val workingDirFiles = workingDirectory.toFile().listFiles(DsStoreFilenameFilter)
            ?: throw IllegalArgumentException("Invalid working directory $workingDirectory")

        if (workingDirFiles.isEmpty()) {
            return WorkingDirectoryStatus.EXISTS_BUT_EMPTY
        }

        return if (workingDirFiles.map { it.name }.toSet() == workingDirNames) {
            val srcDirectoryFiles = File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME").listFiles(DsStoreFilenameFilter)
                ?: throw IllegalArgumentException("Invalid source directory $workingDirectory")
            if (srcDirectoryFiles.isEmpty()) {
//            if (Files.list(Paths.get("$workingDirectory$sep$SOURCE_DIRECTORY_NAME")).toList().isEmpty()) {
                WorkingDirectoryStatus.EXISTS_AND_EMPTY_SRC
            } else {
                WorkingDirectoryStatus.EXISTS_AND_NON_EMPTY_SRC
            }
        } else {
            WorkingDirectoryStatus.EXISTS_BUT_UNRECOGNIZED
        }
    }

    private fun purgeWorkingDirectory(workingDirectory: Path) {
        recursiveDeleteAllContents(workingDirectory.toFile())
    }

    private fun initWorkingDirectory(workingDirectory: Path) {
        Files.createDirectory(Paths.get("${workingDirectory.pathString}$sep$SOURCE_DIRECTORY_NAME"))
        Files.createDirectory(Paths.get("${workingDirectory.pathString}$sep$POSITIVE_DIRECTORY_NAME"))
        Files.createDirectory(Paths.get("${workingDirectory.pathString}$sep$NEGATIVE_DIRECTORY_NAME"))
    }

    /**
     * Loads input files with a background task.
     * This is the first stage of the pipeline.
     * Input files are loaded (and expanded, if necessary) into a consistent format and filename
     *  scheme in the source directory. Filenames are set into [sourceImages] for future reference.
     */
    private fun loadInputFiles(inputDirectory: Path, workingDirectory: Path) {
        if (!inputImageLoadingMutex.tryLock()) {
            logger.warn("Not kicking off input file read process because lock is held")
            return
        }
        if (sourceImages.isNotEmpty()) {
            logger.info("Skipping input file read process because files are already loaded")
            return
        }

        logger.info("Kicking off input file read process")
        val task = InputImageLoaderTask(
            inputDirectory,
            workingDirectory,
            inputImageLoadingMutex,
        )
        prgInputLoading.isVisible = true
        prgInputLoading.progressProperty().bind(task.progressProperty())
        inputImageLoadingTask = task
        task.setOnSucceeded { stateEvent ->
            val result = stateEvent.source.value as List<InputTaskResult>
            logger.info("Loaded ${result.size} input images")
            prgInputLoading.isVisible = false
            sourceImages = result
        }
        task.setOnCancelled {
            prgInputLoading.isVisible = false
        }
        task.setOnFailed {
            prgInputLoading.isVisible = false
        }
        pool.submit(task)
    }

    /**
     * If input images are not loaded directly, call this function to read the contents of the source
     *  directory and set [sourceImages].
     */
    private fun loadSourceFiles() {
        sourceImages = File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME")
            .listFiles()
            ?.map { FilenameUtils.removeExtension(it.name) }
            ?: listOf()
    }
    //endregion

    //region Sample Tab
    private fun initSampleTab() {
        validateDirectorySelections()
        if (sourceImages.isEmpty()) {
            loadSourceFiles()
        }
    }

    @FXML
    private fun editorPress(event: MouseEvent) {
        val path = selectFile("Select Working Directory")

        if (path != null) {
            txtWorkingDirectory.text = path
        }
    }

    @FXML
    private fun editorRelease(event: MouseEvent) {
        val path = selectFile("Select Working Directory")

        if (path != null) {
            txtWorkingDirectory.text = path
        }
    }
    //endregion
}