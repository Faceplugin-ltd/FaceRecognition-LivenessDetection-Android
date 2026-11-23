package com.faceplugin.facerecognitionsdk.shell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.view.View
import kotlin.math.max

/**
 * Face boxes with their liveness score, drawn over the preview.
 *
 * The analyser works on a frame that is scaled down and, on the front camera, not mirrored,
 * while the preview fills the view and is mirrored. Boxes are mapped with the same
 * FILL_CENTER maths the preview uses, so they sit on the face rather than near it.
 */
class LivenessOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** One face: where it is in frame coordinates, and what the engine scored it. */
    data class Face(val box: RectF, val liveness: Float, val live: Boolean)

    private var faces: List<Face> = emptyList()
    private var frame: Size = Size(0, 0)
    private var mirror = true

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        isFakeBoldText = true
    }

    fun submit(faces: List<Face>, frame: Size, mirror: Boolean) {
        this.faces = faces
        this.frame = frame
        this.mirror = mirror
        postInvalidateOnAnimation()
    }

    fun clear() = submit(emptyList(), frame, mirror)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fw = frame.width.toFloat()
        val fh = frame.height.toFloat()
        if (fw <= 0f || fh <= 0f || faces.isEmpty()) return

        val scale = max(width / fw, height / fh)
        val dx = (width - fw * scale) / 2f
        val dy = (height - fh * scale) / 2f

        for (face in faces) {
            var left = face.box.left * scale + dx
            var right = face.box.right * scale + dx
            val top = face.box.top * scale + dy
            val bottom = face.box.bottom * scale + dy
            if (mirror) {
                val mirroredLeft = width - right
                right = width - left
                left = mirroredLeft
            }

            val color = if (face.live) LIVE else FAKE
            boxPaint.color = color
            canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, boxPaint)

            val text = "${if (face.live) "Live" else "Fake"}  ${"%.2f".format(face.liveness)}"
            val textWidth = textPaint.measureText(text)
            val labelHeight = 54f
            val labelTop = (top - labelHeight - 8f).coerceAtLeast(0f)
            labelPaint.color = color
            canvas.drawRoundRect(
                left,
                labelTop,
                left + textWidth + 28f,
                labelTop + labelHeight,
                12f,
                12f,
                labelPaint,
            )
            canvas.drawText(text, left + 14f, labelTop + labelHeight - 16f, textPaint)
        }
    }

    private companion object {
        const val LIVE = 0xFF2EE6A6.toInt()
        const val FAKE = 0xFFFF6B6B.toInt()
    }
}
