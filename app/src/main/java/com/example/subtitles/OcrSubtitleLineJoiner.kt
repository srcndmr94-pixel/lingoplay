package com.example.subtitles

import com.google.mlkit.vision.text.Text

object OcrSubtitleLineJoiner {
    fun join(result: Text): String {
        val lines = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.replace(Regex("\\s+"), " ").trim()
                if (text.isBlank()) null else Line(text, box.top, box.left, box.height())
            }
        if (lines.isEmpty()) return result.text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")

        val medianHeight = lines.map { it.height }.sorted().let { it[it.size / 2].coerceAtLeast(1) }
        val rowTolerance = (medianHeight * 0.65f).toInt().coerceAtLeast(4)
        val rows = mutableListOf<MutableList<Line>>()

        for (line in lines.sortedWith(compareBy<Line> { it.top }.thenBy { it.left })) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.map { it.top }.average() - line.top) <= rowTolerance) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }

        return rows.joinToString(" ") { row ->
            row.sortedBy { it.left }.joinToString(" ") { it.text }
        }.replace(Regex("\\s+"), " ").trim()
    }

    private data class Line(val text: String, val top: Int, val left: Int, val height: Int)
}
