package net.djvk.imageCleaner.constants

import net.djvk.imageCleaner.util.NormalizingUtils
import java.io.File

val SOURCE_DIRECTORY_NAME = "src"
val POSITIVE_DIRECTORY_NAME = "pos"
val NEGATIVE_DIRECTORY_NAME = "neg"

val workingDirNames = setOf(SOURCE_DIRECTORY_NAME, POSITIVE_DIRECTORY_NAME, NEGATIVE_DIRECTORY_NAME)

/**
 * The name of an image file in the source directory.
 * These files are the input to the object detection model.
 * The filenames are unique and all source files should be of the same image type.
 */
typealias SourceFilename = String
typealias InputTaskResult = SourceFilename

val sep = File.separator

fun getImageId(filename: String, index: Int): String {
    return "${NormalizingUtils.normalizeValue(filename)}|$index"
}
