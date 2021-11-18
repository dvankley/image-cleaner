package net.djvk.imageCleaner.ui

import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.stage.FileChooser
import javafx.stage.Stage

class ParentController {
    @FXML
    lateinit var tabPane: TabPane

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
    lateinit var imgEditor: ImageView

    @FXML
    lateinit var hboxInputImages: HBox
    //endregion

    private val stage
        get() = txtInputDirectory.scene.window as Stage

    fun initialize() {
        tabPane.selectionModel.selectedItemProperty().addListener { value, old, new ->
            when (new) {
                tabInput -> {}
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
        if (txtInputDirectory.text == "" || txtWorkingDirectory.text == "") {
            val alert = Alert(Alert.AlertType.ERROR, "Input directory selection required")
            alert.showAndWait()
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