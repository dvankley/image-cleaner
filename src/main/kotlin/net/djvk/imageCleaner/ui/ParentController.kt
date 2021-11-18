package net.djvk.imageCleaner.ui

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.input.MouseEvent
import javafx.stage.FileChooser
import javafx.stage.Stage

class ParentController {
    @FXML
    lateinit var btnSelectInputDirectory: Button
    @FXML
    lateinit var txtInputDirectory: TextField

    @FXML
    lateinit var btnSelectWorkingDirectory: Button
    @FXML
    lateinit var txtWorkingDirectory: TextField

    val stage
        get() = txtInputDirectory.scene.window as Stage

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
}