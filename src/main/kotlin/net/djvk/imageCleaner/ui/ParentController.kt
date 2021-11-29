package net.djvk.imageCleaner.ui

import javafx.beans.value.ChangeListener
import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.sync.Mutex
import net.coobird.thumbnailator.Thumbnails
import net.djvk.imageCleaner.constants.*
import net.djvk.imageCleaner.tasks.InputImageLoaderTask
import net.djvk.imageCleaner.util.DsStoreFilenameFilter
import net.djvk.imageCleaner.util.recursiveDeleteAllContents
import net.djvk.imageCleaner.util.unwrapOptional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.io.path.pathString
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ParentController {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @FXML
    lateinit var tabPane: TabPane

    private val pool = Executors.newWorkStealingPool()

    //region Input Tab
    @FXML
    lateinit var tabInput: Tab

    @FXML
    lateinit var txtOpencvBinDirectory: TextField

    @FXML
    lateinit var txtInputDirectory: TextField

    @FXML
    lateinit var txtWorkingDirectory: TextField

    @FXML
    lateinit var btnLoadInputFiles: Button

    private lateinit var opencvBinDirectory: Path
    private lateinit var inputDirectory: Path
    private lateinit var workingDirectory: Path
    //endregion

    //region Sample Tab
    @FXML
    lateinit var tabSample: Tab

    @FXML
    lateinit var apSample: AnchorPane

    @FXML
    lateinit var prgInputLoading: ProgressBar

    @FXML
    lateinit var imgEditor: ImageView

    @FXML
    lateinit var hboxSourceImages: HBox

    @FXML
    lateinit var ivSamplingMain: ImageView

    /**
     * [BufferedImage] version of [ivSamplingMain]
     */
    private var mainSamplingImage: BufferedImage? = null

    @FXML
    lateinit var paneSamplingMain: Pane

    @FXML
    lateinit var btnPositive: Button

    @FXML
    lateinit var btnNegative: Button

    data class AnnotationSelection(
        var type: AnnotationType,
        val rect: Rectangle,
    ) {
        override fun toString(): String {
            return "${type.displayValue}: ${rect.x.toInt()}, ${rect.y.toInt()}"
        }
    }

    @FXML
    lateinit var chbAnnotation: ChoiceBox<AnnotationSelection>

    @FXML
    lateinit var tgAnnotationType: ToggleGroup

    @FXML
    lateinit var rdbPositive: RadioButton

    @FXML
    lateinit var rdbNegative: RadioButton

    @FXML
    lateinit var hboxPositiveImages: HBox

    @FXML
    lateinit var hboxNegativeImages: HBox

    /**
     * Background task to load inputimages from files
     */
    private var inputImageLoadingTask: Task<List<InputTaskResult>>? = null
    private val inputImageLoadingMutex = Mutex()

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
            hboxSourceImages.children.clear()
            // Add the new ones
            hboxSourceImages.children.addAll(value.map { srcImgFilename ->
                val iv = ImageView(
                    SwingFXUtils.toFXImage(
                        Thumbnails
                            .of("$workingDirectory$sep$SOURCE_DIRECTORY_NAME$sep$srcImgFilename")
                            .size(50, 50)
                            .asBufferedImage(), null
                    )
                )
                iv.id = srcImgFilename
                iv.onMouseClicked = handleSourceThumbnailClick
                iv
            })
        }

    fun initialize() {
        // Tab change listener
        tabPane.selectionModel.selectedItemProperty().addListener { _, _, new ->
            when (new) {
                tabInput -> {
                }
                tabSample -> initSampleTab()
                else -> throw IllegalArgumentException("Invalid tab $new")
            }
        }
        // Sample tab, annotation type change listener
        tgAnnotationType.selectedToggleProperty().addListener(handleAnnotationTypeChange)
        // Sample tab, selected annotation change listener
        chbAnnotation.selectionModel.selectedItemProperty().addListener(handleCurrentAnnotationChange)
    }

    //region Input Tab
    @FXML
    private fun handleSelectOpencvBinDirectory(event: MouseEvent) {
        val path = selectFile("Select OpenCV 3 Bin Directory")

        if (path != null) {
            txtOpencvBinDirectory.text = path
        }
    }

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
        val opencvBin = txtOpencvBinDirectory.text
        val input = txtInputDirectory.text
        val working = txtWorkingDirectory.text
        if (opencvBin == null || opencvBin == "") {
            val alert = Alert(Alert.AlertType.ERROR, "OpenCV bin directory selection required")
            alert.showAndWait()
            tabPane.selectionModel.select(tabInput)
            return
        }
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
        opencvBinDirectory = Paths.get(opencvBin)
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

        val workingDirFiles = workingDirectory.toFile().listFiles(DsStoreFilenameFilter)
            ?: throw IllegalArgumentException("Invalid working directory $workingDirectory")

        if (workingDirFiles.isEmpty()) {
            return WorkingDirectoryStatus.EXISTS_BUT_EMPTY
        }

        return if (workingDirFiles.map { it.name }.toSet() == workingDirNames) {
            val srcDirectoryFiles = File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME").listFiles(DsStoreFilenameFilter)
                ?: throw IllegalArgumentException("Invalid source directory $workingDirectory")
            if (srcDirectoryFiles.isEmpty()) {
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
            ?.map { it.name }
            ?: listOf()
    }
    //endregion

    //region Sample Tab
    private fun initSampleTab() {
        // TODO: put all this on a background task
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

    /**
     * Handles clicks on source image thumbnails, loading them into the main image view for sampling.
     */
    private val handleSourceThumbnailClick = EventHandler<MouseEvent> { event ->
        val source = event.source as ImageView
        logger.trace("Thumbnail click on ${source.id}")

        // Read image into memory
        mainSamplingImage = ImageIO.read(File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME$sep${source.id}"))

        // Load full image into UI
        ivSamplingMain.image = SwingFXUtils.toFXImage(mainSamplingImage, null)
    }

    private val CURRENT_ANNOTATION_COLOR = Color.BLUE

    private fun buildDragBox(strokeColor: Color): Rectangle {
        val rect = Rectangle()
        rect.fill = Color.TRANSPARENT
        rect.stroke = strokeColor
        rect.strokeWidth = 5.0
        return rect
    }

    private fun getCurrentlySelectedDragBox(): Rectangle? {
        return chbAnnotation.selectionModel.selectedItem?.rect
    }

    @FXML
    private fun handleAnnotationPressed(event: MouseEvent) {
        val dragBox = getCurrentlySelectedDragBox() ?: addNewAnnotation()
        dragBox.x = event.x
        dragBox.y = event.y
        dragBox.width = 10.0
        dragBox.height = 10.0
//        logger.trace("Sample pressed event: ${event.x},${event.y}")

        forceRefreshChoiceBox(chbAnnotation)
    }

    @FXML
    private fun handleAnnotationDragged(event: MouseEvent) {
        annotationDrag(event)
    }

    @FXML
    private fun handleAnnotationReleased(event: MouseEvent) {
        annotationDrag(event)
    }

    private fun annotationDrag(event: MouseEvent) {
        val dragBox = getCurrentlySelectedDragBox()
            ?: run {
                logger.warn("Failed to find sample drag box on mouse drag event")
                return
            }

//        logger.trace("Sample dragged/released event: ${event.x},${event.y}; box: ${dragBox.x},${dragBox.y}")
        dragBox.x = min(event.x, dragBox.x)
        dragBox.y = min(event.y, dragBox.y)
        dragBox.width = abs(event.x - dragBox.x)
        dragBox.height = abs(event.y - dragBox.y)

        /**
         * Don't call forceRefreshChoiceBox(chbAnnotation) here because we don't currently display
         *  anything in the choiceBox that changes based on the end of the selection
         */
    }

    private val handleCurrentAnnotationChange = ChangeListener<AnnotationSelection> { value, old, new ->
        updateUiForNewAnnotationSelection(old, new)
    }

    private fun selectAnnotationByIndex(index: Int) {
        val old = chbAnnotation.selectionModel.selectedItem
        chbAnnotation.selectionModel.select(index)
        updateUiForNewAnnotationSelection(old, chbAnnotation.selectionModel.selectedItem)
    }

    private fun selectAnnotationByItem(item: AnnotationSelection) {
        val old = chbAnnotation.selectionModel.selectedItem
        chbAnnotation.selectionModel.select(item)
        updateUiForNewAnnotationSelection(old, item)
    }

    private fun updateUiForNewAnnotationSelection(oldItem: AnnotationSelection?, newItem: AnnotationSelection?) {
        // Now that the old item isn't "current" anymore, update its color to match its type
        oldItem?.rect?.stroke = oldItem?.type?.color

        if (newItem != null) {
            // Update the new item to the "current" color
            newItem.rect.stroke = CURRENT_ANNOTATION_COLOR

            // Update the annotation type radio button to match the newly selected item
            tgAnnotationType.selectToggle(apSample.lookup("#${newItem.type.nodeId}") as Toggle)
        }
    }

    @FXML
    private fun handleDeleteCurrentAnnotation(event: MouseEvent) {
        // Remove the rectangle from the UI
        paneSamplingMain.children.remove(chbAnnotation.selectionModel.selectedItem.rect)

        // Remove the element from the choice box
        val index = chbAnnotation.selectionModel.selectedIndex
        chbAnnotation.items.remove(chbAnnotation.selectionModel.selectedItem)
        selectAnnotationByIndex(max(index - 1, 0))
    }

    @FXML
    private fun handleAddNewAnnotation(event: MouseEvent) {
        addNewAnnotation()
    }

    private fun addNewAnnotation(): Rectangle {
        // Create a new rectangle object for this annotation and add it to the UI
        val rect = buildDragBox(CURRENT_ANNOTATION_COLOR)
        paneSamplingMain.children.add(rect)

        // Add a new entry to the annotations ChoiceBox
        val annotation = AnnotationSelection(
            AnnotationType.byNodeId[(tgAnnotationType.selectedToggle as RadioButton).id]
                ?: AnnotationType.POSITIVE,
            rect
        )
        chbAnnotation.items.add(annotation)
        // Select this annotation as the current one
        selectAnnotationByItem(annotation)

        return rect
    }

    enum class AnnotationType(
        val displayValue: String,
        val nodeId: String,
        val color: Color,
    ) {
        POSITIVE("Positive", "rdbPositive", Color.GREEN),
        NEGATIVE("Negative", "rdbNegative", Color.RED);

        companion object {
            val byNodeId = values().associateBy { it.nodeId }
        }
    }

    private val handleAnnotationTypeChange = ChangeListener<Toggle> { value, old, new ->
        val rdb = new as RadioButton
        val type = AnnotationType.byNodeId[rdb.id] ?: return@ChangeListener
        chbAnnotation.selectionModel?.selectedItem?.type = type
        forceRefreshChoiceBox(chbAnnotation)
    }

    private fun <T> forceRefreshChoiceBox(cb: ChoiceBox<T>) {
        val current = cb.selectionModel.selectedItem
        // Make a copy of the items list so the clear() at the beginning of setAll() doesn't blank this out too
        val items = cb.items.toList()
        // Smack the ChoiceBox in the side of the head to get it to update
        cb.items.setAll(items)
        cb.selectionModel.select(current)
    }

//    private fun cutAndWriteSample(targetDirectory: String) {
//        val mainImage = mainSamplingImage
//            ?: run {
//                val alert = Alert(Alert.AlertType.ERROR, "No sampling image selected.")
//                alert.showAndWait()
//                return
//            }
//        val dragBox = queryDragBox()
//            ?: run {
//                val alert = Alert(Alert.AlertType.ERROR, "No sample box selected.")
//                alert.showAndWait()
//                return
//            }
//        // Slice out the selected box from the main sampling image
//        val sub = mainImage.getSubimage(
//            dragBox.x.roundToInt(),
//            dragBox.y.roundToInt(),
//            dragBox.width.roundToInt(),
//            dragBox.height.roundToInt()
//        )
//
//        // Write it to a file
//        ImageIO.write(sub, "jpg", File("$workingDirectory$sep$targetDirectory$sep${System.currentTimeMillis()}.jpg"))
//    }

    //endregion
}