package net.djvk.imageCleaner.ui

import javafx.beans.value.ChangeListener
import javafx.concurrent.Task
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import kotlinx.coroutines.sync.Mutex
import net.djvk.imageCleaner.annotation.read.PositiveAnnotationFileReader
import net.djvk.imageCleaner.annotation.write.NegativeAnnotationFileWriter
import net.djvk.imageCleaner.annotation.write.PositiveAnnotationFileWriter
import net.djvk.imageCleaner.constants.*
import net.djvk.imageCleaner.tasks.InputImageLoaderTask
import net.djvk.imageCleaner.tasks.SourceImageThumbnailerTask
import net.djvk.imageCleaner.tasks.ThumbnailTaskResult
import net.djvk.imageCleaner.util.*
import org.opencv.core.*
import org.opencv.objdetect.CascadeClassifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ParentController {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @FXML
    lateinit var tabPane: TabPane

    private val pool = Executors.newWorkStealingPool()

    private val stage
        get() = txtInputDirectory.scene.window as Stage

    /**
     * List of source file names, with extensions.
     */
    private var sourceImages = listOf<SourceFilename>()

    fun initialize() {
        // Tab change listener
        tabPane.selectionModel.selectedItemProperty().addListener { _, _, new ->
            when (new) {
                tabInput -> {}
                tabAnnotate -> initAnnotateTab()
                tabTrain -> {}
                tabTest -> initTestTab()
                else -> throw IllegalArgumentException("Invalid tab $new")
            }
        }
        // Annotate tab, annotation type change listener
        tgAnnotationType.selectedToggleProperty().addListener(handleAnnotationTypeChange)
        // Annotate tab, selected annotation change listener
        chbAnnotation.selectionModel.selectedItemProperty().addListener(handleCurrentAnnotationChange)

        // Spinner value factories
        spnPosWidth.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(20, 1000, 150, 10)
        spnPosHeight.valueFactory = SpinnerValueFactory.IntegerSpinnerValueFactory(20, 1000, 150, 10)
    }

    /**
     * Loads and thumbnails source files with a background task.
     * This is the first part of the second stage of the pipeline, annotation and sampling.
     * Source files previously loaded into the filesystem with their filenames in [sourceImages] are
     *  loaded into memory, converted into thumbnails for display, and set into [hboxAnnotateThumbnails]
     *  and [hboxTestThumbnails] for use in the UI.
     */
    private fun loadSourceImageThumbnails(workingDirectory: Path) {
        if (!inputThumbnailLoadingMutex.tryLock()) {
            logger.warn("Not kicking off source file thumbnail process because lock is held")
            return
        }
        if (hboxAnnotateThumbnails.children.isNotEmpty()) {
            logger.info("Skipping source file thumbnail process because thumbnails are already loaded")
            return
        }

        logger.info("Kicking off source file thumbnail process")
        val task = SourceImageThumbnailerTask(
            workingDirectory,
            inputThumbnailLoadingMutex,
        )
        thumbnailLoadStart(task)
        inputThumbnailLoadingTask = task
        task.setOnSucceeded { stateEvent ->
            thumbnailLoadSucceeded(stateEvent.source.value as List<ThumbnailTaskResult>)
        }
        task.setOnCancelled {
            thumbnailLoadFailed()
        }
        task.setOnFailed {
            thumbnailLoadFailed()
        }
        pool.submit(task)
    }

    private fun thumbnailLoadStart(task: SourceImageThumbnailerTask) {
        prgAnnotateLoadThumbnails.isVisible = true
        prgAnnotateLoadThumbnails.progressProperty().bind(task.progressProperty())
        prgTestLoadThumbnails.progressProperty().bind(task.progressProperty())
    }

    private fun thumbnailLoadSucceeded(results: List<ThumbnailTaskResult>) {
        logger.info("Thumbnailed ${results.size} input images")
        prgAnnotateLoadThumbnails.isVisible = false
        scrlAnnotateThumbnails.isVisible = true
        hboxAnnotateThumbnails.children.clear()
        hboxAnnotateThumbnails.children.addAll(results.map { result ->
            val iv = ImageView(result.thumbnail)
            iv.id = result.filename.name
            iv.onMouseClicked = handleAnnotateThumbnailClick
            iv
        })
        // Do the same thing on the test page because it uses basically the same UI
        prgTestLoadThumbnails.isVisible = false
        scrlTestThumbnails.isVisible = true
        hboxTestThumbnails.children.clear()
        hboxTestThumbnails.children.addAll(results.map { result ->
            val iv = ImageView(result.thumbnail)
            iv.id = result.filename.name
            iv.onMouseClicked = handleTestThumbnailClick
            iv
        })
    }

    private fun thumbnailLoadFailed() {
        prgAnnotateLoadThumbnails.isVisible = false
        prgTestLoadThumbnails.isVisible = false
    }

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

    @FXML
    lateinit var prgInputLoading: ProgressBar

    private lateinit var opencvBinDirectory: Path
    private lateinit var inputDirectory: Path
    private lateinit var workingDirectory: Path

    /**
     * Background task to load input images from files into source image files
     */
    private var inputImageLoadingTask: Task<List<InputTaskResult>>? = null
    private val inputImageLoadingMutex = Mutex()

    @FXML
    private fun handleSelectOpencvBinDirectory(event: MouseEvent) {
        val path = selectDirectory("Select OpenCV 3 Bin Directory")

        if (path != null) {
            txtOpencvBinDirectory.text = path
        }
    }

    @FXML
    private fun handleSelectInputDirectoryClick(event: MouseEvent) {
        val path = selectDirectory("Select Input Directory")

        if (path != null) {
            txtInputDirectory.text = path
        }
    }

    @FXML
    private fun handleSelectWorkingDirectoryClick(event: MouseEvent) {
        val path = selectDirectory("Select Working Directory")

        if (path != null) {
            txtWorkingDirectory.text = path
        }
    }

    private fun selectDirectory(title: String): String? {
        val chooser = DirectoryChooser()
        chooser.title = title
        return chooser.showDialog(stage)?.path
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
            btnLoadInputFiles.isDisable = true
            sourceImages = result
            // Kick off the task to thumbnail the source images
            loadSourceImageThumbnails(workingDirectory)
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
            .listFiles(DsStoreFilenameFilter)
            ?.map { it.name }
            ?: listOf()
    }
    //endregion

    //region Annotate Tab
    @FXML
    lateinit var tabAnnotate: Tab

    /**
     * Background task to load input images from files into source image files
     */
    private var inputThumbnailLoadingTask: Task<List<ThumbnailTaskResult>>? = null
    private val inputThumbnailLoadingMutex = Mutex()

    @FXML
    lateinit var prgAnnotateLoadThumbnails: ProgressBar

    @FXML
    lateinit var paneAnnotateThumbnails: Pane

    @FXML
    lateinit var scrlAnnotateThumbnails: ScrollPane

    @FXML
    lateinit var apAnnotate: AnchorPane

    @FXML
    lateinit var hboxAnnotateThumbnails: HBox

    @FXML
    lateinit var ivAnnotatingMain: ImageView

    /**
     * [BufferedImage] version of [ivAnnotatingMain]
     */
    private var mainAnnotatingImage: BufferedImage? = null

    @FXML
    lateinit var paneAnnotatingMain: Pane

    @FXML
    lateinit var btnSaveAnnotations: Button

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
    lateinit var spnPosWidth: Spinner<Int>

    @FXML
    lateinit var spnPosHeight: Spinner<Int>

    private var areAnnotationsDirty = false

    private fun initAnnotateTab() {
        validateDirectorySelections()
        if (sourceImages.isEmpty()) {
            loadSourceFiles()
        }
        loadSourceImageThumbnails(workingDirectory)
    }

    /**
     * Handles clicks on source image thumbnails in the annotate tab, loading them into the main image view for annotating.
     */
    private val handleAnnotateThumbnailClick = EventHandler<MouseEvent> { event ->
        if (areAnnotationsDirty) {
            val alert = Alert(
                Alert.AlertType.CONFIRMATION,
                "Changes to annotations on your current image have not been saved, discard them and change images?"
            )
            val result = unwrapOptional(alert.showAndWait())
                ?: return@EventHandler
            if (result.buttonData != ButtonBar.ButtonData.OK_DONE) {
                return@EventHandler
            }
        }
        val source = event.source as ImageView
//        logger.trace("Thumbnail click on ${source.id}")

        // Display this thumbnail as selected in the UI
        val selectBox = (paneAnnotateThumbnails.lookup("#$THUMBNAIL_SELECTION_BOX_ID") as? Rectangle)
            ?: run {
                val rect = buildThumbnailSelectBox()
                paneAnnotateThumbnails.children.add(rect)
                rect
            }
        val selectedIndex = hboxAnnotateThumbnails.children.indexOf(event.source)
        selectBox.width = (event.source as Node).boundsInParent.width
        selectBox.x = (0..selectedIndex).reduce { acc, i ->
            acc + hboxAnnotateThumbnails.children[i].boundsInParent.width.roundToInt()
        }.toDouble()

        // Read image into memory
        mainAnnotatingImage = ImageIO.read(File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME$sep${source.id}"))

        // Load full image into UI
        ivAnnotatingMain.image = SwingFXUtils.toFXImage(mainAnnotatingImage, null)
        ivAnnotatingMain.id = source.id

        // Reset annotations
        deleteAllAnnotations()

        // Load any existing annotations that have been saved to files
        loadAnnotationsFromFile(source.id)
        setAnnotationsDirty(false)
    }

    private fun buildThumbnailSelectBox(): Rectangle {
        val rect = Rectangle()
        rect.fill = Color.TRANSPARENT
        rect.stroke = Color.LIGHTBLUE
        rect.strokeWidth = 3.0
        rect.width = THUMBNAIL_WIDTH.toDouble()
        rect.height = THUMBNAIL_HEIGHT.toDouble()
        rect.isMouseTransparent = true
        rect.id = THUMBNAIL_SELECTION_BOX_ID
        return rect
    }

    private fun getCurrentAnnotationType(): AnnotationType {
        return AnnotationType.byNodeId[(tgAnnotationType.selectedToggle as RadioButton).id]
            ?: AnnotationType.POSITIVE
    }

    private fun buildDragBox(strokeColor: Color): Rectangle {
        val rect = buildAnnotationRectangle(strokeColor)
        if (getCurrentAnnotationType() == AnnotationType.POSITIVE) {
            // Positive samples need to have a consistent size
            rect.width = spnPosWidth.value.toDouble()
            rect.height = spnPosHeight.value.toDouble()
        }
        return rect
    }

    private fun getCurrentlySelectedDragBox(): Rectangle? {
        return chbAnnotation.selectionModel.selectedItem?.rect
    }

    @FXML
    private fun handleAnnotationPressed(event: MouseEvent) {
        val dragBox = getCurrentlySelectedDragBox() ?: run {
            val annotation = AnnotationSelection(
                getCurrentAnnotationType(),
                buildDragBox(CURRENT_ANNOTATION_COLOR)
            )
            addNewAnnotation(annotation)
            annotation.rect
        }
        dragBox.x = event.x
        dragBox.y = event.y
        if (getCurrentAnnotationType() == AnnotationType.POSITIVE) {
            // Positive samples need to have a consistent size
            dragBox.width = spnPosWidth.value.toDouble()
            dragBox.height = spnPosHeight.value.toDouble()
        } else {
            dragBox.width = 10.0
            dragBox.height = 10.0
        }
//        logger.trace("Annotate pressed event: ${event.x},${event.y}")

        forceRefreshChoiceBox(chbAnnotation)
        setAnnotationsDirty(true)
    }

    @FXML
    private fun handleAnnotationDragged(event: MouseEvent) {
        annotationDrag(event)
    }

    @FXML
    private fun handleAnnotationReleased(event: MouseEvent) {
        annotationDrag(event)
        forceRefreshChoiceBox(chbAnnotation)
    }

    private fun annotationDrag(event: MouseEvent) {
        val dragBox = getCurrentlySelectedDragBox()
            ?: run {
                logger.warn("Failed to find annotate drag box on mouse drag event")
                return
            }

//        logger.trace("Annotate dragged/released event: ${event.x},${event.y}; box: ${dragBox.x},${dragBox.y}")
        if (getCurrentAnnotationType() == AnnotationType.POSITIVE) {
            dragBox.x = event.x
            dragBox.y = event.y
            // Positive samples need to have a consistent size
            dragBox.width = spnPosWidth.value.toDouble()
            dragBox.height = spnPosHeight.value.toDouble()
        } else {
            dragBox.x = min(event.x, dragBox.x)
            dragBox.y = min(event.y, dragBox.y)
            dragBox.width = abs(event.x - dragBox.x)
            dragBox.height = abs(event.y - dragBox.y)
        }

        /**
         * Don't call forceRefreshChoiceBox(chbAnnotation) here because we don't currently display
         *  anything in the choiceBox that changes based on the end of the selection
         */

        setAnnotationsDirty(true)
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
            tgAnnotationType.selectToggle(apAnnotate.lookup("#${newItem.type.nodeId}") as Toggle)
        }
    }

    @FXML
    private fun handleDeleteCurrentAnnotation(event: MouseEvent) {
        // Remove the rectangle from the UI
        paneAnnotatingMain.children.remove(chbAnnotation.selectionModel.selectedItem.rect)

        // Remove the element from the choice box
        val index = chbAnnotation.selectionModel.selectedIndex
        chbAnnotation.items.remove(chbAnnotation.selectionModel.selectedItem)
        selectAnnotationByIndex(max(index - 1, 0))
        setAnnotationsDirty(true)
    }

    private fun deleteAllAnnotations() {
        // Remove the rectangles from the UI
        paneAnnotatingMain.children.removeAll(paneAnnotatingMain.children.filterIsInstance<Rectangle>())

        // Remove the elements from the choice box
        chbAnnotation.items.clear()
    }

    private fun loadAnnotationsFromFile(filename: SourceFilename) {
        val reader = PositiveAnnotationFileReader(workingDirectory, Paths.get(filename))
        val annotations = reader.readAnnotations()

        annotations.forEach { annotation ->
            addNewAnnotation(annotation)
        }
        setAnnotationsDirty(false)
    }

    @FXML
    private fun handleAddNewAnnotation(event: MouseEvent) {
        addNewAnnotation(
            AnnotationSelection(getCurrentAnnotationType(), buildDragBox(CURRENT_ANNOTATION_COLOR))
        )
    }

    private fun addNewAnnotation(annotation: AnnotationSelection) {
        paneAnnotatingMain.children.add(annotation.rect)

        // Add a new entry to the annotations ChoiceBox
        chbAnnotation.items.add(annotation)
        // Select this annotation as the current one
        selectAnnotationByItem(annotation)

        setAnnotationsDirty(true)
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
        setAnnotationsDirty(true)
    }

    private fun <T> forceRefreshChoiceBox(cb: ChoiceBox<T>) {
        val current = cb.selectionModel.selectedItem
        // Make a copy of the items list so the clear() at the beginning of setAll() doesn't blank this out too
        val items = cb.items.toList()
        // Smack the ChoiceBox in the side of the head to get it to update
        cb.items.setAll(items)
        cb.selectionModel.select(current)
    }

    @FXML
    private fun handleSaveAnnotations(event: MouseEvent) {
        saveAnnotations()
    }

    private fun setAnnotationsDirty(dirty: Boolean) {
        btnSaveAnnotations.isDisable = !dirty
        areAnnotationsDirty = dirty
    }

    private fun saveAnnotations() {
        val imagePath = Paths.get(ivAnnotatingMain.id)
        val mainImage = mainAnnotatingImage
            ?: throw IllegalArgumentException("Failed to save; missing main annotation image file buffer")

        val negativeWriter = NegativeAnnotationFileWriter(workingDirectory, imagePath, mainImage)
        negativeWriter.writeAnnotations(chbAnnotation.items.filter { it.type == AnnotationType.NEGATIVE })

        val positiveWriter = PositiveAnnotationFileWriter(workingDirectory, imagePath, mainImage)
        positiveWriter.writeAnnotations(chbAnnotation.items.filter { it.type == AnnotationType.POSITIVE })

        setAnnotationsDirty(false)
    }

    //endregion

    //region Train Tab
    @FXML
    lateinit var tabTrain: Tab

    //endregion

    //region Test Tab
    @FXML
    lateinit var tabTest: Tab

    @FXML
    lateinit var prgTestLoadThumbnails: ProgressBar

    @FXML
    lateinit var paneTestThumbnails: Pane

    @FXML
    lateinit var scrlTestThumbnails: ScrollPane

    @FXML
    lateinit var apTest: AnchorPane

    @FXML
    lateinit var hboxTestThumbnails: HBox

    @FXML
    lateinit var ivTestMain: ImageView

    /**
     * [BufferedImage] version of [ivTestingMain]
     */
    private var mainTestImage: BufferedImage? = null

    @FXML
    lateinit var paneTestMain: Pane

    private fun initTestTab() {
        validateDirectorySelections()
        if (sourceImages.isEmpty()) {
            loadSourceFiles()
        }
        loadSourceImageThumbnails(workingDirectory)
    }

    data class OpenCvAnnotation(
        val rect: Rect,
        val rejectLevel: Int,
        val weight: Double,
    )

    /**
     * Handles clicks on source image thumbnails in the test tab, loading them into the main test image view.
     */
    private val handleTestThumbnailClick = EventHandler<MouseEvent> { event ->
        val source = event.source as ImageView
//        logger.trace("Thumbnail click on ${source.id}")

        // Display this thumbnail as selected in the UI
        // TODO: this is mostly straight duplicated from the annotation tab, dedupe it
        val selectBox = (paneTestThumbnails.lookup("#$THUMBNAIL_SELECTION_BOX_ID") as? Rectangle)
            ?: run {
                val rect = buildThumbnailSelectBox()
                paneTestThumbnails.children.add(rect)
                rect
            }
        val selectedIndex = hboxTestThumbnails.children.indexOf(event.source)
        selectBox.width = (event.source as Node).boundsInParent.width
        selectBox.x = (0..selectedIndex).reduce { acc, i ->
            acc + hboxTestThumbnails.children[i].boundsInParent.width.roundToInt()
        }.toDouble()

        // Read image into memory
        val img = ImageIO.read(File("$workingDirectory$sep$SOURCE_DIRECTORY_NAME$sep${source.id}"))
        mainTestImage = img

        // Load full image into UI
        ivTestMain.image = SwingFXUtils.toFXImage(img, null)
        ivTestMain.id = source.id

        // Remove all annotations
        paneTestMain.children.removeAll(paneTestMain.children.filterIsInstance<Rectangle>())

        // Generate annotations from the object recognition model
        val classifier = CascadeClassifier(
            workingDirectory
                .resolve(MODEL_DIRECTORY_NAME)
                .resolve(MODEL_FILENAME)
                .pathString
        )

        val trainedHeight = spnPosHeight.value.toDouble()
        val trainedWidth = spnPosWidth.value.toDouble()
        val mat = OpenCvUtilities.bufferedImageToMat(img)
        val objects = MatOfRect()
        val rawRejectLevels = MatOfInt()
        val rawWeights = MatOfDouble()
        classifier.detectMultiScale3(
            mat,
            objects,
            rawRejectLevels,
            rawWeights,
            1.1,
            6,
            // Allegedly this param doesn't matter for "new" cascades
            0,
            Size(trainedWidth / 2, trainedHeight / 2),
            Size(trainedWidth * 2, trainedHeight * 2),
            true,
        )

        val rejectLevels = rawRejectLevels.toList()
        val weights = rawWeights.toList()

        val openCvAnnotations = objects.toList().mapIndexed { index, rect ->
            OpenCvAnnotation(
                rect,
                rejectLevels[index],
                weights[index],
            )
        }

        // Display the annotations from the model
        val annotations = openCvAnnotations.map { annotation ->
            val box = buildAnnotationRectangle(Color.LIGHTGREEN)
            box.x = annotation.rect.x.toDouble()
            box.y = annotation.rect.y.toDouble()
            box.width = annotation.rect.width.toDouble()
            box.height = annotation.rect.height.toDouble()
            box
        }
        paneTestMain.children.addAll(annotations)
    }
    //endregion
}