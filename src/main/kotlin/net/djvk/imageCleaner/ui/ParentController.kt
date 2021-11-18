package net.djvk.imageCleaner.ui

import javafx.concurrent.Task
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.sync.Mutex
import net.djvk.imageCleaner.tasks.InputImageLoaderTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.nio.file.Paths
import java.util.concurrent.Executors

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
    var inputImageLoadingTask: Task<List<BufferedImage>>? = null
    val inputImageLoadingMutex = Mutex()

    //endregion

    private val stage
        get() = txtInputDirectory.scene.window as Stage

    private var inputImages = listOf<BufferedImage>()

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
    //endregion

    //region Sample Tab
    private fun initSampleTab() {
        val inputDirectory = txtInputDirectory.text
        val workingDirectory = txtWorkingDirectory.text
        if (inputDirectory == null ||
            inputDirectory == "" ||
            workingDirectory == null ||
            workingDirectory == ""
        ) {
            val alert = Alert(Alert.AlertType.ERROR, "Input directory selection required")
            alert.showAndWait()
            tabPane.selectionModel.select(tabInput)
            return
        }

        if (!inputImageLoadingMutex.tryLock()) {
            logger.warn("Not kicking off input file read process because lock is held")
            return
        }

        logger.info("Kicking off input file read process")
        val task = InputImageLoaderTask(
            Paths.get(inputDirectory),
            inputImageLoadingMutex,
            prgInputLoading,
        )
        prgInputLoading.isVisible = true
        prgInputLoading.progressProperty().bind(task.progressProperty())
        inputImageLoadingTask = task
        pool.submit(task)
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