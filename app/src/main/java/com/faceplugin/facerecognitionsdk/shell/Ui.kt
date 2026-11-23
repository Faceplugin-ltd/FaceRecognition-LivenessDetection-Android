package com.faceplugin.facerecognitionsdk.shell

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import java.io.File
import java.text.DateFormat
import java.util.Locale
import java.util.concurrent.Executors

object Ui {
    /** IDs are always shown as a pill tag "ID 12" — never "#12". */
    fun idTag(id: Long): String = "ID $id"

    fun score(v: Float): String = String.format(Locale.US, "%.2f", v)

    fun ms(v: Double): String = if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)

    fun facesCount(c: Context, n: Int): String =
        if (n == 1) c.getString(R.string.face_count_one) else c.getString(R.string.faces_count, n)

    fun matchesCount(c: Context, n: Int): String =
        if (n == 1) c.getString(R.string.match_count_one) else c.getString(R.string.txt_matches_count, n)

    fun enrolledDate(iso: String): String =
        FaceGallery.parseIso(iso)?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) } ?: iso

    fun enrolledDateTime(iso: String): String =
        FaceGallery.parseIso(iso)?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) } ?: iso

    fun tintPill(view: View, color: Int) {
        ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color))
    }

    fun setIdTag(tv: TextView, id: Long) { tv.text = idTag(id) }

    // ---- small async image loader for crops / originals ----

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    /** Loads [path] into [iv] (down-sampled to about [px]); shows [placeholder] while loading or when missing. */
    fun loadImage(iv: ImageView, path: String?, px: Int, placeholder: Int = R.drawable.ic_person, onMissing: (() -> Unit)? = null) {
        val key = "$path@$px"
        iv.tag = key
        if (path.isNullOrEmpty()) {
            iv.setImageResource(placeholder); onMissing?.invoke(); return
        }
        cache.get(key)?.let { iv.setImageBitmap(it); return }
        iv.setImageResource(placeholder)
        io.execute {
            val bmp = decode(path, px)
            if (bmp != null) cache.put(key, bmp)
            main.post {
                if (iv.tag != key) return@post
                if (bmp != null) iv.setImageBitmap(bmp) else onMissing?.invoke()
            }
        }
    }

    fun evict(path: String?) {
        if (path == null) return
        cache.snapshot().keys.filter { it.startsWith("$path@") }.forEach { cache.remove(it) }
    }

    private fun decode(path: String, px: Int): Bitmap? {
        val f = File(path)
        if (!f.isFile) return null
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            var s = 1
            while (minOf(b.outWidth, b.outHeight) / (s * 2) >= px) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        } catch (_: Throwable) {
            null
        }
    }
}
