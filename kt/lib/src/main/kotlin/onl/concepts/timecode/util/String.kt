package onl.concepts.timecode.util

fun String.flow(): String {
    return this.trimIndent().lines().joinToString(" ")
}
