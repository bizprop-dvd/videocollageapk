package id.harts.videocollage

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import kotlin.math.roundToInt

/** Each layout is a list of boxes, as fractions (0..1) of the canvas. Box order = "Video 1, 2, 3, 4". */
enum class CollageLayout(val label: String, val cells: List<RectF>) {
    ONE_TOP_TWO_BOTTOM(
        "1 top, 2 bottom",
        listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f))
    ),
    TWO_TOP_ONE_BOTTOM(
        "2 top, 1 bottom",
        listOf(RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f), RectF(0f, .5f, 1f, 1f))
    ),
    TOP_BOTTOM(
        "Top / bottom",
        listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, 1f, 1f))
    ),
    LEFT_RIGHT(
        "Left / right",
        listOf(RectF(0f, 0f, .5f, 1f), RectF(.5f, 0f, 1f, 1f))
    ),
    GRID_4(
        "4 videos",
        listOf(
            RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f),
            RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f)
        )
    );

    val count get() = cells.size

    /** Pixel boxes for an output of w x h. Edges are rounded to even numbers (video encoders need that). */
    fun pixelCells(w: Int, h: Int): List<Rect> = cells.map {
        Rect(even(it.left * w), even(it.top * h), even(it.right * w), even(it.bottom * h))
    }

    private fun even(v: Float) = (v / 2f).roundToInt() * 2
}

enum class Aspect(val label: String, val width: Int, val height: Int) {
    PORTRAIT("9:16", 1080, 1920),
    SQUARE("1:1", 1080, 1080),
    LANDSCAPE("16:9", 1920, 1080)
}

/** Sound choice: audio from one box (0-based index), all boxes mixed, or none. */
sealed class Sound {
    data class From(val index: Int) : Sound()
    object Mix : Sound()
    object Mute : Sound()

    fun keeps(box: Int) = this is Mix || (this is From && index == box)
}

/** A video, or a photo shown as a still for [PHOTO_DURATION_US]. */
/** [mimeType] matters: picker URIs have no file extension, so export needs it to know photo vs video. */
data class Clip(val uri: Uri, val durationUs: Long, val thumb: Bitmap?, val isImage: Boolean = false, val mimeType: String? = null)

const val PHOTO_DURATION_US = 5_000_000L
