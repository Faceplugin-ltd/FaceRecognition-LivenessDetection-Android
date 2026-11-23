package com.faceplugin.facerecognitionsdk.shell

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.faceplugin.facerecognitionsdk.kit.FaceBox
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.shell.Sections.Badge
import com.faceplugin.facerecognitionsdk.shell.data.AppSettings
import com.faceplugin.facerecognitionsdk.kit.EnrollOutcome
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.faceplugin.facerecognitionsdk.capture.FaceCaptureActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Add one person: the Capture guide, then a review of the captured still.
 * The review lists each check with its value and the Settings limit; name, note,
 * Retake and Enroll appear right away. Enroll is blocked while a required check fails:
 * one face, liveness (when on in Settings), and head pose. Low quality only warns.
 */
class AddPersonActivity : FaceCaptureActivity() {

    override fun resultPanelLayout() = R.layout.view_enroll_panel

    override fun limits() = AppSettings.limits(this)


    private var saving = false
    /** Reason the capture cannot be enrolled; null when it can. */
    private var blockReason: String? = null
    private var analyzed = false
    private var wired = false

    /** The review lives in a dialog, so its views are found on the panel, not the activity. */
    private fun <T : View> panel(id: Int): T = resultPanel().findViewById(id)

    private val nameLayout get() = panel<TextInputLayout>(R.id.name_layout)
    private val nameInput get() = panel<TextInputEditText>(R.id.name_input)
    private val noteInput get() = panel<TextInputEditText>(R.id.note_input)
    private val save get() = panel<MaterialButton>(R.id.enroll_save)

    override fun onCaptureAnalyzing() {
        analyzed = false
        blockReason = null
        // The review is a dialog, so the camera preview is no longer visible behind it: the
        // captured still has to be in the panel itself.
        panel<ImageView>(R.id.capture_face).setImageBitmap(capturedPhoto())
        panel<View>(R.id.capture_review).visibility = View.VISIBLE
        panel<View>(R.id.capture_progress).visibility = View.VISIBLE
        panel<LinearLayout>(R.id.capture_checks).removeAllViews()
        panel<View>(R.id.capture_reason).visibility = View.GONE
        val verdict = panel<TextView>(R.id.capture_verdict)
        verdict.setText(R.string.capture_checking)
        Ui.tintPill(verdict, ContextCompat.getColor(this, R.color.status_info))

        panel<View>(R.id.enroll_fields).visibility = View.VISIBLE
        panel<View>(R.id.enroll_error).visibility = View.GONE
        panel<View>(R.id.enroll_status).visibility = View.GONE
        panel<View>(R.id.enroll_retake).apply {
            visibility = View.VISIBLE
            setOnClickListener { if (!saving) retakeCapture() }
        }
        if (!wired) {
            wired = true
            nameLayout.helperText = getString(R.string.name_optional_help)
            nameInput.doAfterTextChanged { nameLayout.error = null; refreshSave() }
            save.setOnClickListener { enroll() }
        }
        refreshSave()
    }

    override fun onCaptureAnalyzed(shown: FaceBox?, faceCount: Int) {
        analyzed = true
        panel<View>(R.id.capture_progress).visibility = View.GONE
        val checks = panel<LinearLayout>(R.id.capture_checks)
        checks.removeAllViews()
        val body = Sections.add(checks, getString(R.string.sec_capture_checks))
        val pass = getString(R.string.badge_pass)
        val fail = getString(R.string.badge_fail)
        val reasons = ArrayList<String>()

        // Exactly one face in the still.
        val oneFace = faceCount == 1 && shown != null
        Sections.row(body, getString(R.string.check_faces), faceCount.toString(),
            (if (oneFace) pass else fail) to (if (oneFace) Badge.PASS else Badge.FAIL))
        if (!oneFace) reasons += getString(R.string.block_faces)

        if (shown != null) {
            // Liveness: the Settings threshold decides; when Liveness is off it is shown, not enforced.
            if (shown.livenessLabel.isNotBlank()) {
                val live = AppSettings.isLive(this, shown.liveness)
                val word = getString(if (live) R.string.live else R.string.spoof)
                val value = "$word · ${f2(shown.liveness)}  (≥ ${f2(AppSettings.livenessThreshold(this))})"
                if (AppSettings.liveness(this)) {
                    Sections.row(body, getString(R.string.liveness_check), value,
                        (if (live) pass else fail) to (if (live) Badge.PASS else Badge.FAIL), shown.liveness)
                    if (!live) reasons += getString(R.string.block_liveness) + "\n" + getString(R.string.liveness_turn_off_hint)
                } else {
                    Sections.row(body, getString(R.string.liveness_check),
                        getString(R.string.check_liveness_off, value), null, shown.liveness)
                }
            }

            // Quality warns only.
            val q = shown.source?.quality?.takeIf { !it.isNaN() } ?: shown.face_quality
            val qBadge = when {
                q < 0.5f -> getString(R.string.quality_low) to Badge.FAIL
                q < 0.75f -> getString(R.string.quality_medium) to Badge.WARN
                else -> getString(R.string.quality_high) to Badge.PASS
            }
            Sections.row(body, getString(R.string.attr_quality), f2(q), qBadge, q)

            // Head pose against the Settings limits.
            val yawOk = abs(shown.yaw) <= AppSettings.yaw(this)
            val rollOk = abs(shown.roll) <= AppSettings.roll(this)
            val pitchOk = abs(shown.pitch) <= AppSettings.pitch(this)
            val poseOk = yawOk && rollOk && pitchOk
            val limit = max(AppSettings.yaw(this), max(AppSettings.roll(this), AppSettings.pitch(this)))
            Sections.row(body, getString(R.string.check_pose),
                getString(R.string.pose_value, f1(shown.yaw), f1(shown.roll), f1(shown.pitch), f1(limit)),
                getString(if (poseOk) R.string.badge_pass else R.string.badge_over) to (if (poseOk) Badge.PASS else Badge.FAIL))
            if (!poseOk) reasons += getString(R.string.block_pose)

            if (reasons.isEmpty() && q < 0.5f) showReason(getString(R.string.warn_quality), R.color.status_nomatch)
        }

        blockReason = reasons.takeIf { it.isNotEmpty() }?.joinToString("\n")
        blockReason?.let { showReason(it, R.color.danger) }
        val verdict = panel<TextView>(R.id.capture_verdict)
        verdict.setText(if (blockReason == null) R.string.capture_ready else R.string.capture_retake_needed)
        Ui.tintPill(verdict, ContextCompat.getColor(this, if (blockReason == null) R.color.status_match else R.color.danger))
        refreshSave()
        if (blockReason == null && AppSettings.skipReview(this)) {
            // Nothing to report, and the tester asked not to be stopped: enroll straight away.
            enroll()
            return
        }
        if (blockReason == null) nameInput.requestFocus()
    }

    /** While typing, the still gives up most of its height so the fields stay reachable. */
    override fun onReviewKeyboardChanged(visible: Boolean) {
        val face = panel<ImageView>(R.id.capture_face)
        val height = resources.getDimensionPixelSize(
            if (visible) R.dimen.capture_face_height_typing else R.dimen.capture_face_height
        )
        if (face.layoutParams.height != height) {
            face.layoutParams = face.layoutParams.apply { this.height = height }
            face.requestLayout()
        }
    }

    override fun retakeCapture() {
        panel<View>(R.id.capture_review).visibility = View.GONE
        panel<View>(R.id.enroll_fields).visibility = View.GONE
        analyzed = false
        blockReason = null
        super.retakeCapture()
    }

    private fun showReason(text: String, color: Int) {
        val reason = panel<TextView>(R.id.capture_reason)
        reason.visibility = View.VISIBLE
        reason.text = text
        reason.setTextColor(ContextCompat.getColor(this, color))
    }

    /**
     * Enroll turns on once the checks have run and nothing blocks. The name is optional: a
     * blank one is saved as Person N, so testing is capture then Enroll with no typing.
     */
    private fun refreshSave() {
        save.isEnabled = !saving && analyzed && blockReason == null && hasCapture()
        save.setText(if (saving) R.string.saving else R.string.action_enroll)
    }

    private fun enroll() {
        if (saving) return
        val bitmap = capturedPhoto() ?: return
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val note = noteInput.text?.toString()?.trim().orEmpty()
        val error = panel<TextView>(R.id.enroll_error)
        val status = panel<FrameLayout>(R.id.enroll_status)
        if (blockReason != null) return
        error.visibility = View.GONE
        saving = true
        refreshSave()
        FaceEngine.worker.execute {
            val outcome = FaceGallery.get(applicationContext).enroll(name, note, bitmap, AppSettings.limits(applicationContext))
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                saving = false
                refreshSave()
                when (outcome) {
                    is EnrollOutcome.Enrolled -> {
                        status.visibility = View.GONE
                        // Every person over the threshold is named, not just the closest.
                        val dup = outcome.duplicates
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(", ") {
                                getString(R.string.enroll_duplicate_entry, it.name, it.id, Ui.score(it.similarity))
                            }
                            ?.let { getString(R.string.enroll_duplicate_warning, it) }
                        // The review closes first, so the status dialog replaces it rather
                        // than stacking a second dialog on top.
                        dismissResultPanel()
                        StatusCard.showDialog(
                            this,
                            getString(R.string.enrolled_as, outcome.record.name, outcome.record.id),
                            dup,
                            outcome.report,
                        ) { finish() }
                    }
                    is EnrollOutcome.Rejected -> {
                        error.text = outcome.reason
                        error.visibility = View.VISIBLE
                        val report = outcome.report
                        if (report != null) StatusCard.bind(status, report, getString(R.string.enroll_status_title))
                        else status.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun f2(v: Float) = String.format(Locale.US, "%.2f", v)
    private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
}
