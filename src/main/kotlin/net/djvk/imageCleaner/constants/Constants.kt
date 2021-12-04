package net.djvk.imageCleaner.constants

import javafx.scene.paint.Color
import java.io.File

const val SOURCE_DIRECTORY_NAME = "src"
const val POSITIVE_DIRECTORY_NAME = "pos"
const val NEGATIVE_DIRECTORY_NAME = "neg"

const val NEGATIVE_ANNOTATION_FILENAME = "neg.txt"
const val POSITIVE_ANNOTATION_FILENAME = "pos.txt"

val workingDirNames = setOf(SOURCE_DIRECTORY_NAME, POSITIVE_DIRECTORY_NAME, NEGATIVE_DIRECTORY_NAME)

const val THUMBNAIL_WIDTH = 50
const val THUMBNAIL_HEIGHT = 50

val CURRENT_ANNOTATION_COLOR = Color.BLUE

const val THUMBNAIL_SELECTION_BOX_ID = "thumbnailSelectBox"

/**
 * The name of an image file in the source directory.
 * These files are the input to the object detection model.
 * The filenames are unique and all source files should be of the same image type.
 */
typealias SourceFilename = String
typealias InputTaskResult = SourceFilename

val sep: String = File.separator