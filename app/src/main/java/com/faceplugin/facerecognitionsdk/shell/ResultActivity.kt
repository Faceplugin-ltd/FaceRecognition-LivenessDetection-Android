package com.faceplugin.facerecognitionsdk.shell

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.Candidate
import com.faceplugin.facerecognitionsdk.kit.FaceRecord
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.IdentifyResult
import com.faceplugin.facerecognitionsdk.kit.ImageUtils
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/** Pushed from Identify: Match / No match, Probe vs Rank 1, metrics, matches ≥ threshold. */
class ResultActivity : AppCompatActivity() {

    private lateinit var result: IdentifyResult
    private var records: Map<Long, FaceRecord?> = emptyMap()
    private val galleryListener: () -> Unit = { loadRecords() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val r = ResultStore.last
        if (r == null) { finish(); return }
        result = r
        setContentView(R.layout.activity_identify_result)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val probe = findViewById<ImageView>(R.id.probe_image)
        probe.setImageBitmap(ImageUtils.squareAround(result.probe, result.primary.box))

        metric(R.id.metric_extract, Ui.ms(result.extractMs), getString(R.string.extract_ms))
        metric(R.id.metric_search, Ui.ms(result.searchMs), getString(R.string.search_ms))
        metric(R.id.metric_gallery, result.gallerySize.toString(), getString(R.string.gallery_size))
        StatusCard.bind(findViewById(R.id.status_container), result.report)
        render()
        loadRecords()
    }

    override fun onStart() {
        super.onStart()
        if (::result.isInitialized) FaceGallery.get(this).addListener(galleryListener)
    }

    override fun onStop() {
        FaceGallery.get(this).removeListener(galleryListener)
        super.onStop()
    }

    private fun metric(id: Int, value: String, label: String) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.metric_value).text = value
        v.findViewById<TextView>(R.id.metric_label).text = label
    }

    /** Fetch the stored records (thumbnails, originals, current names) for every shown face. */
    private fun loadRecords() {
        val ids = (result.faces.mapNotNull { it.best?.id } + result.matches.map { it.id }).toSet()
        val gallery = FaceGallery.get(this)
        FaceEngine.worker.execute {
            val map = ids.associateWith { gallery.get(it) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                records = map
                render()
            }
        }
    }

    /** Records not yet loaded count as present; deleted ones are dropped from the screen. */
    private fun exists(id: Long) = !records.containsKey(id) || records[id] != null

    private fun render() {
        val t = result.threshold
        val primary = result.primary
        val best = primary.best?.takeIf { exists(it.id) }
        val matched = best != null && best.similarity >= t
        val matches = result.matches.filter { exists(it.id) }

        val pill = findViewById<TextView>(R.id.status_pill)
        pill.setText(if (matched) R.string.match else R.string.no_match)
        Ui.tintPill(pill, ContextCompat.getColor(this, if (matched) R.color.status_match else R.color.status_nomatch))

        findViewById<TextView>(R.id.rank1_label).setText(if (matched) R.string.rank_1 else R.string.closest)
        val rank1 = findViewById<ImageView>(R.id.rank1_image)
        val rec = best?.let { records[it.id] }
        val tilePx = (resources.displayMetrics.widthPixels / 2f).roundToInt()
        // Rank 1 shows the stored ORIGINAL photo; falls back to the crop.
        Ui.loadImage(rank1, rec?.originalPath, tilePx) {
            Ui.loadImage(rank1, rec?.cropPath, tilePx)
        }

        val headline = findViewById<TextView>(R.id.headline)
        val headlineId = findViewById<TextView>(R.id.headline_id)
        val subline = findViewById<TextView>(R.id.subline)
        if (best == null) {
            headline.setText(R.string.no_gallery_faces)
            headlineId.visibility = View.GONE
            subline.text = getString(R.string.no_face_reaches, Ui.score(t))
        } else {
            headline.text = rec?.name ?: best.name
            headlineId.visibility = View.VISIBLE
            headlineId.text = Ui.idTag(best.id)
            subline.text = if (matched) getString(R.string.similarity_above, Ui.score(best.similarity), Ui.score(t))
            else getString(R.string.no_face_reaches, Ui.score(t))
        }
        val openBest = View.OnClickListener { best?.let { openFace(it) } }
        findViewById<View>(R.id.headline_row).setOnClickListener(openBest)
        rank1.setOnClickListener(openBest)

        renderFaces()

        findViewById<TextView>(R.id.matches_count).text = Ui.matchesCount(this, matches.size)
        val more = findViewById<MaterialButton>(R.id.more)
        if (matches.size > MAX_ROWS) {
            more.visibility = View.VISIBLE
            more.text = getString(R.string.n_more, matches.size - MAX_ROWS)
            more.setOnClickListener { startActivity(Intent(this, MatchesActivity::class.java)) }
        } else {
            more.visibility = View.GONE
        }
        val list = findViewById<LinearLayout>(R.id.matches_list)
        list.removeAllViews()
        matches.take(MAX_ROWS).forEachIndexed { i, c ->
            val row = MatchRow.inflate(list)
            MatchRow.bind(row, i + 1, c, records[c.id]) { openFace(it) }
            list.addView(row)
        }
        val none = findViewById<TextView>(R.id.no_matches)
        none.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        none.text = getString(R.string.no_matches_row, Ui.score(t))
    }

    /** When the probe has 2+ faces, one line per face. */
    private fun renderFaces() {
        val section = findViewById<View>(R.id.faces_section)
        if (result.faces.size < 2) { section.visibility = View.GONE; return }
        section.visibility = View.VISIBLE
        val list = findViewById<LinearLayout>(R.id.faces_list)
        list.removeAllViews()
        result.faces.forEach { f ->
            val tv = TextView(this)
            tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            tv.setPadding(0, (6 * resources.displayMetrics.density).roundToInt(), 0, 0)
            val b = f.best?.takeIf { exists(it.id) }
            tv.text = when {
                b == null -> "Face ${f.index + 1} · no gallery faces"
                b.similarity >= result.threshold ->
                    "Face ${f.index + 1} · ${records[b.id]?.name ?: b.name} · ${Ui.idTag(b.id)} · ${Ui.score(b.similarity)}"
                else -> "Face ${f.index + 1} · No match · closest ${Ui.score(b.similarity)}"
            }
            if (b != null) tv.setOnClickListener { openFace(b) }
            list.addView(tv)
        }
    }

    private fun openFace(c: Candidate) {
        FaceDetailSheet.show(supportFragmentManager, c.id, c.similarity)
    }

    companion object {
        const val MAX_ROWS = 3
    }
}
