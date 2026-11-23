package com.faceplugin.facerecognitionsdk.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageUtils {
    /** Same cap the SDK uses internally, so face boxes come back in this bitmap's coordinates. */
    const val ENGINE_MAX_SIDE = 1280

    /** Decode a picked photo upright (EXIF) with long side ≤ [maxSide]. */
    fun decodeUri(context: Context, uri: Uri, maxSide: Int = ENGINE_MAX_SIDE): Bitmap? {
        val cr = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val orientation = try {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            scaleDown(rotateExif(raw, orientation), maxSide)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun rotateExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longSide = max(src.width, src.height)
        if (longSide <= maxSide) return src
        val s = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true,
        )
    }

    /** Face box + 12% padding (of the shorter box side, min 4 px), clamped to the image. */
    fun cropFace(src: Bitmap, box: Rect, paddingFraction: Float = 0.12f): Bitmap? {
        val w = box.width()
        val h = box.height()
        if (w <= 1 || h <= 1) return null
        val pad = max(4, (min(w, h) * paddingFraction).toInt())
        val left = max(0, box.left - pad)
        val top = max(0, box.top - pad)
        val right = min(src.width, box.right + pad)
        val bottom = min(src.height, box.bottom + pad)
        if (right - left <= 1 || bottom - top <= 1) return null
        return try {
            Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        } catch (_: Exception) {
            null
        }
    }

    /** Square crop centred on the face for the Probe tile. */
    fun squareAround(src: Bitmap, box: Rect, scale: Float = 1.8f): Bitmap {
        val side = min((max(box.width(), box.height()) * scale).roundToInt(), min(src.width, src.height))
        if (side <= 1) return src
        val left = (box.centerX() - side / 2).coerceIn(0, src.width - side)
        val top = (box.centerY() - side / 2).coerceIn(0, src.height - side)
        return Bitmap.createBitmap(src, left, top, side, side)
    }

    fun jpeg(bitmap: Bitmap, quality: Int = 92): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val s = c.getString(0)
                    if (!s.isNullOrBlank()) return s
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "photo"
    }

    fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }
}
