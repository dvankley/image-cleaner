package net.djvk.imageCleaner.tasks

import javafx.scene.image.WritableImage
import java.nio.file.Path

data class ThumbnailTaskResult(
    val thumbnail: WritableImage,
    val filename: Path,
)
