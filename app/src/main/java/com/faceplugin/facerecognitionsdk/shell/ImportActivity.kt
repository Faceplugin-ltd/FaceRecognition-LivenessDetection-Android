package com.faceplugin.facerecognitionsdk.shell

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.EnrollOutcome
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.ImageUtils
import com.faceplugin.facerecognitionsdk.shell.data.AppSettings
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Import photos: each photo = one person named after the file (without extension);
 * photos with 0 or 2+ faces are skipped with the reason.
 */
class ImportActivity : AppCompatActivity() {

    private data class Item(val uri: Uri, val fileName: String)

    private var items: List<Item> = emptyList()
    private var started = false
    private var finished = false
    @Volatile private var cancelled = false
    private var skippedCount = 0
    private var enrolledCount = 0
    private var totalMs = 0.0

    private lateinit var action: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        action = findViewById(R.id.action)
        action.isEnabled = false
        action.setOnClickListener {
            when {
                finished -> finish()
                !started -> start()
            }
        }
        collect()
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun collect() {
        val uris: List<Uri> = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(EXTRA_URIS, Uri::class.java) ?: emptyList()
        } else {
            intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS) ?: emptyList()
        }
        val tree: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_TREE, Uri::class.java)
        else intent.getParcelableExtra(EXTRA_TREE)
        val app = applicationContext
        FaceEngine.worker.execute {
            val list = if (tree != null) listTree(tree) else uris.map { Item(it, ImageUtils.displayName(app, it)) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                items = list.sortedBy { it.fileName.lowercase(Locale.ROOT) }
                val summary = findViewById<TextView>(R.id.import_summary_text)
                if (items.isEmpty()) {
                    summary.setText(R.string.no_images_found)
                    finished = true
                    action.setText(R.string.done)
                } else {
                    summary.text = if (items.size == 1) getString(R.string.import_summary_one)
                    else getString(R.string.import_summary, items.size)
                }
                action.isEnabled = true
            }
        }
    }

    /** Image files directly inside the picked folder. */
    private fun listTree(tree: Uri): List<Item> {
        val out = ArrayList<Item>()
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (mime.startsWith("image/") || ext in IMAGE_EXTS) {
                        out += Item(DocumentsContract.buildDocumentUriUsingTree(tree, docId), name)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun start() {
        started = true
        action.isEnabled = false
        findViewById<View>(R.id.rules).visibility = View.GONE
        findViewById<View>(R.id.progress_section).visibility = View.VISIBLE
        val bar = findViewById<LinearProgressIndicator>(R.id.progress)
        bar.max = items.size
        val progressText = findViewById<TextView>(R.id.progress_text)
        val gallery = FaceGallery.get(applicationContext)
        val app = applicationContext
        val total = items.size
        updateMetrics()

        FaceEngine.worker.execute {
            items.forEachIndexed { index, item ->
                if (cancelled) return@execute
                runOnUiThread {
                    progressText.text = getString(R.string.import_progress, index + 1, total, item.fileName)
                }
                val bmp = ImageUtils.decodeUri(app, item.uri)
                val outcome = if (bmp == null) EnrollOutcome.Rejected(getString(R.string.photo_unreadable))
                else gallery.enroll(ImageUtils.stripExtension(item.fileName), "", bmp, AppSettings.limits(app))
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    bar.setProgressCompat(index + 1, true)
                    addResult(item.fileName, outcome)
                    updateMetrics()
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                finished = true
                progressText.text = getString(R.string.import_progress, total, total, getString(R.string.done))
                val done = findViewById<TextView>(R.id.result_text)
                done.visibility = View.VISIBLE
                done.text = getString(R.string.import_done, enrolledCount, skippedCount)
                action.isEnabled = true
                action.setText(R.string.done)
            }
        }
    }

    private fun updateMetrics() {
        metric(R.id.metric_enrolled, enrolledCount.toString(), getString(R.string.label_enrolled))
        metric(R.id.metric_skipped, skippedCount.toString(), getString(R.string.label_skipped))
        val n = enrolledCount + skippedCount
        metric(R.id.metric_avg, if (n == 0) "–" else Ui.ms(totalMs / n), getString(R.string.metric_avg_ms))
    }

    private fun metric(id: Int, value: String, label: String) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.metric_value).text = value
        v.findViewById<TextView>(R.id.metric_label).text = label
    }

    /** One row per photo, newest on top: file, person or reason, Enrolled/Skipped, time. Tap for the status card. */
    private fun addResult(fileName: String, outcome: EnrollOutcome) {
        val report = when (outcome) {
            is EnrollOutcome.Enrolled -> outcome.report
            is EnrollOutcome.Rejected -> outcome.report
        }
        val ok = outcome is EnrollOutcome.Enrolled
        if (ok) enrolledCount++ else skippedCount++
        totalMs += report?.totalMs ?: 0.0

        findViewById<View>(R.id.results_section).visibility = View.VISIBLE
        findViewById<TextView>(R.id.results_count).text = (enrolledCount + skippedCount).toString()
        val list = findViewById<LinearLayout>(R.id.results_list)
        val row = LayoutInflater.from(this).inflate(R.layout.item_import_result, list, false)
        row.findViewById<TextView>(R.id.result_file).text = fileName
        val thumb = row.findViewById<ImageView>(R.id.result_thumb)
        val thumbPx = (44 * resources.displayMetrics.density).roundToInt()
        row.findViewById<TextView>(R.id.result_sub).text = when (outcome) {
            is EnrollOutcome.Enrolled -> {
                Ui.loadImage(thumb, outcome.record.cropPath, thumbPx)
                val base = "${outcome.record.name} · ${Ui.idTag(outcome.record.id)}"
                // Bulk enroll names every match over the threshold, as Add one person does.
                outcome.duplicates
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { "${it.name} ${Ui.idTag(it.id)} ${Ui.score(it.similarity)}" }
                    ?.let { "$base · ${getString(R.string.step_duplicate)}: $it" }
                    ?: base
            }
            is EnrollOutcome.Rejected -> {
                Ui.loadImage(thumb, null, thumbPx)
                outcome.reason
            }
        }
        val tag = row.findViewById<TextView>(R.id.result_tag)
        tag.setText(if (ok) R.string.import_tag_enrolled else R.string.import_tag_skipped)
        val warn = ok && (outcome as EnrollOutcome.Enrolled).duplicates.isNotEmpty()
        Ui.tintPill(tag, ContextCompat.getColor(this, when {
            !ok -> R.color.danger
            warn -> R.color.status_nomatch
            else -> R.color.status_match
        }))
        row.findViewById<TextView>(R.id.result_ms).text =
            report?.let { getString(R.string.ms_value, Ui.ms(it.totalMs)) } ?: ""
        val status = row.findViewById<FrameLayout>(R.id.result_status)
        if (report != null) {
            row.findViewById<View>(R.id.result_row).setOnClickListener {
                if (status.visibility == View.VISIBLE) {
                    status.visibility = View.GONE
                } else {
                    if (status.childCount == 0) StatusCard.bind(status, report, getString(R.string.enroll_status_title))
                    status.visibility = View.VISIBLE
                }
            }
        }
        list.addView(row, 0)
    }

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TREE = "tree"
        private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "bmp", "heic", "heif")
    }
}
