package net.djvk.imageCleaner.ui

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import org.springframework.stereotype.Component
import java.io.File
import java.net.URL

@Component
class UiApp(
) : Application() {
    fun show() {
        launch()
    }

    override fun start(stage: Stage) {
        val root = FXMLLoader.load<Parent>(getResourceUrl("Parent.fxml"))
        val scene = Scene(root)
        stage.scene = scene
        stage.show()


//        val vBox = VBox(tabPane)
//        val scene = Scene(vBox)
////        val scene = Scene(StackPane(l), 640.0, 480.0)
//        stage.scene = scene
//        stage.show()
    }

    protected fun getResourceUrl(resourceFilename: String): URL {
        return File("src/main/resources/$resourceFilename")
            .toURI()
            .toURL()
    }
}