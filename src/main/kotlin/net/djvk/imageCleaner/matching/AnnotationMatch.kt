package net.djvk.imageCleaner.matching

data class AnnotationMatch(
    override val x: Double,
    override val y: Double,
    override val width: Double,
    override val height: Double,
) : ObjectMatch
