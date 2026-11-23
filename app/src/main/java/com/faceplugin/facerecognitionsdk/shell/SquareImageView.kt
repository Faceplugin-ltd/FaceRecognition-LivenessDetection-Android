package com.faceplugin.facerecognitionsdk.shell

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView

/** Height follows width (square image tiles on the Result screen and face sheet). */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ShapeableImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
