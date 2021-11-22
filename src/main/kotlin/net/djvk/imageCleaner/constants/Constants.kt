package net.djvk.imageCleaner.constants

import java.io.File

val SOURCE_DIRECTORY_NAME = "src"
val POSITIVE_DIRECTORY_NAME = "pos"
val NEGATIVE_DIRECTORY_NAME = "neg"

val workingDirNames = setOf(SOURCE_DIRECTORY_NAME, POSITIVE_DIRECTORY_NAME, NEGATIVE_DIRECTORY_NAME)

typealias InputTaskResult = String

val sep = File.separator
