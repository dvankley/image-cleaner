package net.djvk.imageCleaner.ui

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.input.MouseEvent

class ParentController {
    @FXML
    lateinit var txtInputDirectory: TextField

    @FXML
    lateinit var btnSelectDirectory: Button

    @FXML
    private fun handleSelectDirectoryClick(event: MouseEvent) {
        txtInputDirectory?.text = "buttz"
    }
}