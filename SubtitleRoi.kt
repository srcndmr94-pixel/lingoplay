package com.example.subtitles

import android.content.Context
import android.graphics.Bitmap

class SubtitleRoi(
    private var settings: SubtitleCropSettings? = null
) {
    constructor(context: Context) : this(SubtitleCropSettings(context))

    fun attach(context: Context) {
        settings = SubtitleCropSettings(context)
    }

    fun crop(screen: Bitmap): Bitmap {
        val normalized = settings?.load() ?: NormalizedCropRect.DEFAULT
        val rect = normalized.toPixelRect(screen.width, screen.height)
        return Bitmap.createBitmap(screen, rect.left, rect.top, rect.width(), rect.height())
    }
}
