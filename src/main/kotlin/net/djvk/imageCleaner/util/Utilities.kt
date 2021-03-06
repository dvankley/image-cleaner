package net.djvk.imageCleaner.util

import java.io.File
import java.io.FilenameFilter
import java.util.*

fun <T> unwrapOptional(optional: Optional<T>): T? {
    return if (optional.isPresent) {
        optional.get()
    } else {
        null
    }
}

fun recursiveDeleteAllContents(directory: File) {
    if (!directory.isDirectory) {
        throw IllegalArgumentException("Can only be called on directories")
    }

    directory.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            recursiveDeleteAllContents(file)
        }
        file.delete()
    }
}

val DsStoreFilenameFilter = FilenameFilter {dir, name ->
    name != ".DS_Store"
}

val filenameCleanerRegex = """[^a-zA-Z0-9_-]""".toRegex()