package net.djvk.imageCleaner.ui

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.springframework.stereotype.Component


@Component
class UiApp(
) : Application() {
    private lateinit var tabInput: Tab
    private lateinit var tabSample: Tab
    private lateinit var tabTrain: Tab
    private lateinit var tabClassify: Tab
    private lateinit var tabTransform: Tab

    private lateinit var tabPane: TabPane

    private fun lateInitializer() {
        tabInput = Tab("Input", Label("Define input files"))
        tabSample = Tab("Sample", Label("Select positive and negative samples"))
        tabTrain = Tab("Train", Label("Create the classifier model"))
        tabClassify = Tab("Classify", Label("Run the classifier and preview the detected objects"))
        tabTransform = Tab("Transform", Label("Apply the desired transformation to detected objects"))

        tabPane = TabPane()
        tabPane.tabs.add(tabInput)
        tabPane.tabs.add(tabSample)
        tabPane.tabs.add(tabTrain)
        tabPane.tabs.add(tabClassify)
        tabPane.tabs.add(tabTransform)
    }

    override fun init() {
        super.init()

        // Have to do all our init here rather than in a typical Kotlin init block or we'll get
        //  "toolkit not initialized" errors from JavaFX
        lateInitializer()
    }

    fun show() {


        launch()
    }

    override fun start(stage: Stage) {


        val l = Label("Hello, douche.")
        val vBox = VBox(tabPane)
        val scene = Scene(vBox)
//        val scene = Scene(StackPane(l), 640.0, 480.0)
        stage.scene = scene
        stage.show()

        Thread.sleep(5000)
    }
}