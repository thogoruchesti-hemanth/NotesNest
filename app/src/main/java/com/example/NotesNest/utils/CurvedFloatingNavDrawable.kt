package com.example.NotesNest.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

class CurvedFloatingNavDrawable(
    private var fillColor: Int,
    private val cornerRadiusPx: Float,
    private val cradleRadiusPx: Float,
    private val cradleDepthPx: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val path = Path()

    fun updateColor(color: Int) {
        fillColor = color
        paint.color = color
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildPath(bounds)
    }

    private fun buildPath(bounds: Rect) {
        path.reset()

        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return

        val r = cornerRadiusPx.coerceAtMost(height / 2f).coerceAtMost(width / 2f)
        val cx = left + width / 2f
        val cr = cradleRadiusPx
        val cd = cradleDepthPx

        val cradleStart = cx - cr - 12f
        val cradleEnd = cx + cr + 12f

        path.moveTo(left + r, top)

        if (cradleStart > left + r) {
            path.lineTo(cradleStart, top)
        }

        path.cubicTo(
            cx - cr + 4f, top,
            cx - cr / 2f, top + cd,
            cx, top + cd
        )
        path.cubicTo(
            cx + cr / 2f, top + cd,
            cx + cr - 4f, top,
            cradleEnd, top
        )

        path.lineTo(right - r, top)
        path.arcTo(RectF(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false)

        path.lineTo(right, bottom)
        path.lineTo(left, bottom)

        path.lineTo(left, top + r)
        path.arcTo(RectF(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)

        path.close()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
