package net.djvk.imageCleaner.ui

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.input.MouseEvent
import javafx.stage.FileChooser
import javafx.stage.Stage

class ParentController {
    @FXML
    lateinit var txtInputDirectory: TextField

    @FXML
    lateinit var btnSelectDirectory: Button

    val stage
        get() = txtInputDirectory.scene.window as Stage

    @FXML
    private fun handleSelectDirectoryClick(event: MouseEvent) {
        val fileChooser = FileChooser()
        fileChooser.title = "Select Input Directory"
        val file = fileChooser.showOpenDialog(stage)

        if (file != null) {
            txtInputDirectory.text = file.path
        }
    }
}