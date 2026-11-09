package id.harts.videocollage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Live preview of the collage. Tap a box to choose its video. */
class CollageView(context: Context) : View(context) {

    var layout: CollageLayout = CollageLayout.ONE_TOP_TWO_BOTTOM
        set(value) { field = value; invalidate() }
    var aspect: Aspect = Aspect.PORTRAIT
        set(value) { field = value; requestLayout(); invalidate() }
    var onCellTap: ((Int) -> Unit)? = null

    private val thumbs = arrayOfNulls<Bitmap>(4)
    private val frame = RectF()
    private val d = resources.displayMetrics.density

    private val emptyPaint = Paint().apply { color = Color.parseColor("#2A2A2E") }
    private val framePaint = Paint().apply { color = Color.parseColor("#000000") }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6A3D"); textAlign = Paint.Align.CENTER; textSize = 40 * d
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBBBBB"); textAlign = Paint.Align.CENTER; textSize = 14 * d
    }
    private val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC000000") }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 13 * d; isFakeBoldText = true
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun setThumb(index: Int, bmp: Bitmap?) { thumbs[index] = bmp; invalidate() }

    private fun cellRect(i: Int): RectF {
        val c = layout.cells[i]
        val gap = 1.5f * d
        return RectF(
            frame.left + c.left * frame.width() + gap, frame.top + c.top * frame.height() + gap,
            frame.left + c.right * frame.width() - gap, frame.top + c.bottom * frame.height() - gap
        )
    }

    override fun onDraw(canvas: Canvas) {
        val ratio = aspect.width.toFloat() / aspect.height
        val w = width.toFloat(); val h = height.toFloat()
        val fw = min(w, h * ratio); val fh = fw / ratio
        frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
        canvas.drawRect(frame, framePaint)

        for (i in 0 until layout.count) {
            val r = cellRect(i)
            val bmp = thumbs[i]
            if (bmp != null) {
                canvas.save(); canvas.clipRect(r)
                canvas.drawBitmap(bmp, centerCropSrc(bmp, r), r, bmpPaint)
                canvas.restore()
            } else {
                canvas.drawRect(r, emptyPaint)
                canvas.drawText("+", r.centerX(), r.centerY() + 6 * d, plusPaint)
                canvas.drawText("Tap to add video or photo", r.centerX(), r.centerY() + 30 * d, labelPaint)
            }
            val bx = r.left + 16 * d; val by = r.top + 16 * d
            canvas.drawCircle(bx, by, 11 * d, badgeBg)
            canvas.drawText("${i + 1}", bx, by + 4.5f * d, badgeText)
        }
    }

    private fun centerCropSrc(b: Bitmap, dst: RectF): Rect {
        val dstRatio = dst.width() / dst.height()
        val srcRatio = b.width.toFloat() / b.height
        return if (srcRatio > dstRatio) {
            val sw = (b.height * dstRatio).toInt(); val x = (b.width - sw) / 2
            Rect(x, 0, x + sw, b.height)
        } else {
            val sh = (b.width / dstRatio).toInt(); val y = (b.height - sh) / 2
            Rect(0, y, b.width, y + sh)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            for (i in 0 until layout.count) {
                if (cellRect(i).contains(e.x, e.y)) { onCellTap?.invoke(i); break }
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Small icon showing a layout's shape, used in the layout chooser. */
class LayoutIconView(context: Context, val layoutType: CollageLayout) : View(context) {
    var selectedLayout = false
        set(value) { field = value; invalidate() }
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d }

    override fun onDraw(canvas: Canvas) {
        val pad = 4 * d; val gap = 2 * d
        val w = width - 2 * pad; val h = height - 2 * pad
        fill.color = if (selectedLayout) Color.parseColor("#FF6A3D") else Color.parseColor("#55555C")
        border.color = if (selectedLayout) Color.WHITE else Color.TRANSPARENT
        for (c in layoutType.cells) {
            canvas.drawRoundRect(
                pad + c.left * w + gap, pad + c.top * h + gap,
                pad + c.right * w - gap, pad + c.bottom * h - gap, 3 * d, 3 * d, fill
            )
        }
        canvas.drawRoundRect(1 * d, 1 * d, width - 1 * d, height - 1 * d, 6 * d, 6 * d, border)
    }
}
