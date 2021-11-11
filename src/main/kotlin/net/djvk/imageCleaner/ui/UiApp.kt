package net.djvk.imageCleaner.ui

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.springframework.stereotype.Component

@Component
class UiApp(
) : Application() {
    fun show() {
        launch()
    }

    override fun start(stage: Stage?) {
        val l = Label("Hello, douche.")
        val scene = Scene(StackPane(l), 640.0, 480.0)
        stage!!.scene = scene
        stage!!.show()

        Thread.sleep(5000)
    }
}