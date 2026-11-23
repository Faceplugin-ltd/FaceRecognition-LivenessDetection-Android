package com.faceplugin.facerecognitionsdk.shell

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.faceplugin.facerecognitionsdk.kit.FaceBox
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.shell.Sections.Badge
import com.faceplugin.facerecognitionsdk.shell.data.AppSettings
import com.faceplugin.facerecognitionsdk.kit.AttributeResult
import com.faceplugin.facerecognitionsdk.kit.FaceAttributes
import com.faceplugin.facerecognitionsdk.kit.ImageUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Every face in the photo with every value the SDK returns: liveness, quality, detection score,
 * pose, eyes, age, gender, landmarks, box, gallery closest match. Each gated value is checked
 * against the limits in Settings. The call status card is at the bottom.
 */
class AttributeResultActivity : AppCompatActivity() {

    private lateinit var result: AttributeResult
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        result = ResultStore.attribute ?: run { finish(); return }
        setContentView(R.layout.activity_attribute_result)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        selected = savedInstanceState?.getInt(KEY_SELECTED, 0)?.coerceIn(0, max(0, result.faces.size - 1)) ?: 0

        bindSummary()
        bindChips()
        StatusCard.bind(findViewById(R.id.status_container), result.report)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED, selected)
    }

    private fun bindSummary() {
        val n = result.faces.size
        val pill = findViewById<TextView>(R.id.attr_pill)
        pill.text = if (n == 0) getString(R.string.status_no_face) else Ui.facesCount(this, n)
        Ui.tintPill(pill, ContextCompat.getColor(this, if (n == 0) R.color.danger else R.color.status_match))
        findViewById<TextView>(R.id.attr_summary).text = when {
            n == 0 -> getString(R.string.attr_no_face_hint)
            n > MAX_SEARCH -> getString(R.string.attr_faces_capped, MAX_SEARCH)
            else -> getString(R.string.attr_tap_face)
        }
        metric(R.id.metric_faces, n.toString(), getString(R.string.label_faces))
        val detect = result.report.steps.firstOrNull { it.name == "faceDetection" }?.ms ?: 0.0
        metric(R.id.metric_detect, Ui.ms(detect), getString(R.string.metric_detect_ms))
        metric(R.id.metric_total, Ui.ms(result.report.totalMs), getString(R.string.metric_total_ms))
    }

    private fun metric(id: Int, value: String, label: String) {
        val v = findViewById<View>(id)
        v.findViewById<TextView>(R.id.metric_value).text = value
        v.findViewById<TextView>(R.id.metric_label).text = label
    }

    private fun bindChips() {
        if (result.faces.size < 2) return
        findViewById<View>(R.id.face_chips_scroll).visibility = View.VISIBLE
        val group = findViewById<ChipGroup>(R.id.face_chips)
        result.faces.forEach { f ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(R.string.face_n, f.index + 1)
                isCheckable = true
                isChecked = f.index == selected
                tag = f.index
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val idx = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? Int } ?: return@setOnCheckedStateChangeListener
            if (idx != selected) { selected = idx; render() }
        }
    }

    private fun render() {
        findViewById<ImageView>(R.id.attr_photo).setImageBitmap(result.probe)
        val header = findViewById<View>(R.id.face_header)
        val sections = findViewById<LinearLayout>(R.id.face_sections)
        sections.removeAllViews()
        val face = result.faces.getOrNull(selected)
        if (face == null) {
            header.visibility = View.GONE
            val body = Sections.add(sections, getString(R.string.result_title))
            Sections.row(body, getString(R.string.status), getString(R.string.status_no_face), getString(R.string.status_failed) to Badge.FAIL)
            return
        }
        header.visibility = View.VISIBLE
        bindHeader(face)
        bindSections(sections, face)
    }

    // ---- selected face ----

    private fun bindHeader(f: FaceAttributes) {
        val b = f.box
        findViewById<ImageView>(R.id.face_crop).setImageBitmap(
            ImageUtils.cropFace(result.probe, Rect(b.x1, b.y1, b.x2, b.y2)) ?: result.probe
        )
        findViewById<TextView>(R.id.face_title).text = getString(R.string.face_n, f.index + 1)
        findViewById<TextView>(R.id.face_subtitle).text = listOfNotNull(
            if (b.age > 0) getString(R.string.age_value, b.age) else null,
            genderText(b).takeIf { it.isNotBlank() },
            if (!b.smile.isNaN()) emotionText(b) else null,
            if (hasLiveness(b)) livenessWord(b) else null,
        ).joinToString(" · ").ifBlank { getString(R.string.attr_not_computed) }

        val verdict = findViewById<TextView>(R.id.face_verdict)
        val failed = gates(f).count { !it }
        verdict.text = if (failed == 0) getString(R.string.attr_all_pass) else resources.getQuantityString(R.plurals.attr_checks_failed, failed, failed)
        Ui.tintPill(verdict, ContextCompat.getColor(this, if (failed == 0) R.color.status_match else R.color.status_nomatch))
    }

    /** Pass/fail of each gated value that the engine computed. */
    private fun gates(f: FaceAttributes): List<Boolean> {
        val b = f.box
        val out = ArrayList<Boolean>()
        if (hasLiveness(b)) out += isLive(b)
        out += abs(b.yaw) <= AppSettings.yaw(this)
        out += abs(b.roll) <= AppSettings.roll(this)
        out += abs(b.pitch) <= AppSettings.pitch(this)
        return out
    }

    private fun bindSections(parent: LinearLayout, f: FaceAttributes) {
        val b = f.box
        val src = b.source
        val pass = getString(R.string.badge_pass)
        val fail = getString(R.string.badge_fail)

        // Authenticity
        Sections.add(parent, getString(R.string.sec_authenticity)).let { s ->
            if (hasLiveness(b)) {
                val limit = AppSettings.livenessThreshold(this)
                val live = isLive(b)
                Sections.row(
                    s, getString(R.string.liveness_check),
                    "${livenessWord(b)} · ${f2(b.liveness)}  (≥ ${f2(limit)})",
                    (if (live) pass else fail) to (if (live) Badge.PASS else Badge.FAIL),
                    b.liveness,
                )
            } else {
                Sections.row(s, getString(R.string.liveness_check), getString(R.string.attr_not_computed))
            }
        }

        // Person
        Sections.add(parent, getString(R.string.sec_person)).let { s ->
            Sections.row(s, getString(R.string.attr_age), if (b.age > 0) b.age.toString() else getString(R.string.attr_not_computed))
            Sections.row(s, getString(R.string.attr_gender), genderText(b).ifBlank { getString(R.string.attr_not_computed) })
        }

        // Quality
        Sections.add(parent, getString(R.string.sec_quality)).let { s ->
            val q = src?.quality?.takeIf { !it.isNaN() } ?: b.face_quality
            val qBadge = when {
                q < 0.5f -> getString(R.string.quality_low) to Badge.FAIL
                q < 0.75f -> getString(R.string.quality_medium) to Badge.WARN
                else -> getString(R.string.quality_high) to Badge.PASS
            }
            Sections.row(s, getString(R.string.attr_quality), f2(q), qBadge, q)
            src?.score?.takeIf { !it.isNaN() }?.let {
                Sections.row(s, getString(R.string.attr_detection_score), f2(it), null, it)
            }
        }

        // Pose
        Sections.add(parent, getString(R.string.sec_pose)).let { s ->
            poseRow(s, getString(R.string.attr_yaw), b.yaw, AppSettings.yaw(this))
            poseRow(s, getString(R.string.attr_roll), b.roll, AppSettings.roll(this))
            poseRow(s, getString(R.string.attr_pitch), b.pitch, AppSettings.pitch(this))
        }

        // Eyes-open score from the engine, read against EYE_OPEN. Not a Settings threshold:
        // the engine's own label uses a fixed 0.5, which calls a half-open eye closed.
        Sections.add(parent, getString(R.string.sec_eyes)).let { s ->
            val open = src?.eyesOpen?.takeIf { !it.isNaN() }
            if (open == null) {
                Sections.row(s, getString(R.string.attr_eyes), getString(R.string.attr_not_computed))
            } else {
                val isOpen = open >= EYE_OPEN
                val word = getString(if (isOpen) R.string.eyes_open else R.string.eyes_closed)
                Sections.row(
                    s, getString(R.string.attr_eyes),
                    "$word · ${f2(open)}  (>= ${f2(EYE_OPEN)})",
                    word to (if (isOpen) Badge.PASS else Badge.WARN),
                    open,
                )
            }
        }

        // Expression: smile is an engine attribute; mouth openness is measured from the lip points.
        Sections.add(parent, getString(R.string.sec_expression)).let { s ->
            if (b.smile.isNaN()) {
                Sections.row(s, getString(R.string.attr_emotion), getString(R.string.attr_not_computed))
            } else {
                val smiling = b.smile >= SMILE_THRESHOLD
                Sections.row(
                    s, getString(R.string.attr_emotion),
                    "${emotionText(b)} · ${getString(R.string.smile_value, f2(b.smile))}",
                    emotionText(b) to (if (smiling) Badge.PASS else Badge.INFO),
                    b.smile,
                )
            }
            if (b.mouth_opened.isNaN()) {
                Sections.row(s, getString(R.string.attr_mouth), getString(R.string.attr_not_computed))
            } else {
                val m = b.mouth_opened
                val (word, kind) = when {
                    m >= MOUTH_OPEN -> getString(R.string.mouth_open) to Badge.WARN
                    m >= MOUTH_SLIGHT -> getString(R.string.mouth_slightly_open) to Badge.INFO
                    else -> getString(R.string.mouth_closed) to Badge.PASS
                }
                Sections.row(s, getString(R.string.attr_mouth), "$word · ${f2(m)}", word to kind, m)
            }
        }

        // FaceGallery
        Sections.add(parent, getString(R.string.sec_gallery)).let { s ->
            val c = f.closest
            when {
                result.gallerySize == 0 -> Sections.row(s, getString(R.string.attr_closest), getString(R.string.no_gallery_faces))
                f.templateBytes == 0 -> Sections.row(s, getString(R.string.attr_closest), getString(R.string.attr_no_template))
                c == null -> Sections.row(s, getString(R.string.attr_closest), getString(R.string.no_gallery_faces))
                else -> {
                    val matched = c.similarity >= result.threshold
                    Sections.row(
                        s, getString(R.string.attr_closest),
                        "${c.name} · ${Ui.idTag(c.id)}\n${f2(c.similarity)}  (≥ ${f2(result.threshold)})",
                        getString(if (matched) R.string.match else R.string.no_match) to (if (matched) Badge.PASS else Badge.WARN),
                        c.similarity,
                    ).setOnClickListener { FaceDetailSheet.show(supportFragmentManager, c.id, c.similarity) }
                }
            }
            Sections.row(s, getString(R.string.attr_template), if (f.templateBytes > 0) getString(R.string.bytes_value, f.templateBytes) else getString(R.string.attr_no_template))
        }

        // Geometry
        Sections.add(parent, getString(R.string.sec_geometry)).let { s ->
            Sections.row(s, getString(R.string.attr_box), "${b.x1}, ${b.y1} → ${b.x2}, ${b.y2}")
            Sections.row(s, getString(R.string.attr_size), getString(R.string.size_value, b.width(), b.height()))
            Sections.row(s, getString(R.string.attr_landmarks), if (b.landmarkCount > 0) getString(R.string.points_value, b.landmarkCount) else getString(R.string.attr_not_computed))
            if (b.landmarkCount > 0) {
                val text = (0 until b.landmarkCount).joinToString("\n") { i ->
                    "${i + 1}:  ${f1(b.landmarks_68[i * 2])}, ${f1(b.landmarks_68[i * 2 + 1])}"
                }
                Sections.row(s, getString(R.string.attr_positions), text, collapsed = true)
            }
        }

        // Anything else the engine reported
        val extra = b.extraAttributes.filter { it.value.isNotBlank() && it.value.length < 400 }
        if (extra.isNotEmpty()) {
            Sections.add(parent, getString(R.string.sec_more)).let { s ->
                extra.forEach { (k, v) -> Sections.row(s, k, v) }
            }
        }
    }

    private fun poseRow(s: LinearLayout, label: String, value: Float, limit: Float) {
        val ok = abs(value) <= limit
        Sections.row(
            s, label, "${f1(value)}°  (±${f1(limit)}°)",
            getString(if (ok) R.string.badge_pass else R.string.badge_over) to (if (ok) Badge.PASS else Badge.FAIL),
        )
    }

    // ---- value helpers ----

    private fun hasLiveness(b: FaceBox) = b.livenessLabel.isNotBlank()

    /** Same rule as Identify: the Settings liveness threshold decides (AppSettings.isLive). */
    private fun isLive(b: FaceBox): Boolean = AppSettings.isLive(this, b.liveness)

    private fun livenessWord(b: FaceBox) = getString(if (isLive(b)) R.string.live else R.string.spoof)

    private fun emotionText(b: FaceBox) =
        getString(if (b.smile >= SMILE_THRESHOLD) R.string.emotion_smiling else R.string.emotion_neutral)

    private fun genderText(b: FaceBox): String = b.genderLabel.ifBlank {
        when (b.gender) { 0 -> getString(R.string.male); 1 -> getString(R.string.female); else -> "" }
    }

    private fun f2(v: Float) = String.format(Locale.US, "%.2f", v)
    private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)

    companion object {
        private const val KEY_SELECTED = "selected"
        private const val SMILE_THRESHOLD = com.faceplugin.facerecognitionsdk.kit.SMILE_THRESHOLD
        /** Inner-lip gap / mouth width at or above which the mouth reads as open or slightly open. */
        /** Eyes read as open at or above this score. */
        private const val EYE_OPEN = 0.25f
        private const val MOUTH_OPEN = 0.25f
        private const val MOUTH_SLIGHT = 0.10f
        private const val MAX_SEARCH = com.faceplugin.facerecognitionsdk.kit.FaceGallery.MAX_ATTRIBUTE_FACES
    }
}
