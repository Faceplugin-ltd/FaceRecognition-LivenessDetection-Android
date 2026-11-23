package com.faceplugin.facerecognitionsdk.shell

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.CallReport
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * One status UI for every action (identify, enroll, import, attribute):
 * outcome pill, total time, when it finished, then one row per SDK call with
 * pass/fail, its time and its share of the total.
 */
object StatusCard {

    /** Readable names for [com.faceplugin.facerecognitionsdk.kit.CallStep.name]. */
    fun stepName(c: Context, name: String): String = when (name) {
        "faceDetection" -> c.getString(R.string.step_detection)
        "templateExtraction" -> c.getString(R.string.step_template)
        "similarity" -> c.getString(R.string.step_search)
        "livenessCheck" -> c.getString(R.string.step_liveness)
        "duplicateCheck" -> c.getString(R.string.step_duplicate)
        "save" -> c.getString(R.string.step_save)
        else -> name
    }

    fun functionName(c: Context, function: String): String = when (function) {
        "identify" -> c.getString(R.string.tab_identify)
        "enroll" -> c.getString(R.string.action_enroll)
        "attribute" -> c.getString(R.string.tab_attribute)
        else -> function.replaceFirstChar { it.uppercase() }
    }

    /** Inflates a status card for [report], replacing whatever [container] held. */
    fun bind(container: ViewGroup, report: CallReport, title: String? = null, showDetail: Boolean = true): View {
        container.removeAllViews()
        val card = create(container, report, title, showDetail)
        container.addView(card)
        container.visibility = View.VISIBLE
        return card
    }

    fun create(parent: ViewGroup, report: CallReport, title: String? = null, showDetail: Boolean = true): View {
        val c = parent.context
        val card = LayoutInflater.from(c).inflate(R.layout.view_status_card, parent, false)
        card.findViewById<TextView>(R.id.status_title).text =
            title ?: c.getString(R.string.status_of, functionName(c, report.function))

        val pill = card.findViewById<TextView>(R.id.status_pill)
        pill.setText(if (report.success) R.string.status_success else R.string.status_failed)
        Ui.tintPill(pill, ContextCompat.getColor(c, if (report.success) R.color.status_match else R.color.danger))

        val ok = report.steps.count { it.ok }
        val at = if (report.finishedAt > 0) DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(report.finishedAt)) else ""
        card.findViewById<TextView>(R.id.status_summary).text = listOfNotNull(
            c.getString(R.string.status_total, Ui.ms(report.totalMs)),
            c.getString(R.string.status_calls, ok, report.steps.size),
            at.ifBlank { null },
        ).joinToString("  ·  ")

        val steps = card.findViewById<LinearLayout>(R.id.status_steps)
        val total = report.totalMs.takeIf { it > 0 } ?: 1.0
        report.steps.forEach { step ->
            val row = LayoutInflater.from(c).inflate(R.layout.item_status_step, steps, false)
            val dot = row.findViewById<TextView>(R.id.step_dot)
            dot.text = if (step.ok) "✓" else "✕"
            Ui.tintPill(dot, ContextCompat.getColor(c, if (step.ok) R.color.status_match else R.color.danger))
            row.findViewById<TextView>(R.id.step_name).text = stepName(c, step.name)
            row.findViewById<TextView>(R.id.step_ms).text = c.getString(R.string.ms_value, Ui.ms(step.ms))
            row.findViewById<TextView>(R.id.step_detail).text = step.detail
            val bar = row.findViewById<LinearProgressIndicator>(R.id.step_bar)
            bar.setProgressCompat(((step.ms / total) * 100).roundToInt().coerceIn(if (step.ms > 0) 1 else 0, 100), false)
            if (!step.ok) bar.setIndicatorColor(ContextCompat.getColor(c, R.color.danger))
            steps.addView(row)
        }
        if (report.steps.isEmpty()) {
            steps.addView(TextView(c).apply {
                setText(R.string.status_no_calls)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }

        val detail = card.findViewById<TextView>(R.id.status_detail)
        if (showDetail && report.detail.isNotBlank()) {
            detail.visibility = View.VISIBLE
            detail.text = report.detail
        }
        return card
    }

    /** A dialog for an action that stopped: [reason] on top, then the status card. */
    fun showDialog(context: Context, title: String, reason: String?, report: CallReport?, onDone: (() -> Unit)? = null) {
        val pad = (20 * context.resources.displayMetrics.density).roundToInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        if (!reason.isNullOrBlank()) {
            column.addView(TextView(context).apply {
                text = reason
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(0, 0, 0, pad / 2)
            })
        }
        if (report != null) column.addView(create(column, report))
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(column) })
            .setPositiveButton(R.string.done) { _, _ -> onDone?.invoke() }
            .setOnCancelListener { onDone?.invoke() }
            .show()
        // A status card is a list of calls, so it opens at full height rather than making the
        // reader scroll a short window. The card itself is unchanged.
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/** Titled cards of label / value rows with optional pass-fail badge and score bar. */
object Sections {
    enum class Badge(val color: Int) { PASS(R.color.status_match), FAIL(R.color.danger), WARN(R.color.status_nomatch), INFO(R.color.status_info) }

    fun add(parent: ViewGroup, title: String): LinearLayout {
        val card = LayoutInflater.from(parent.context).inflate(R.layout.view_section, parent, false)
        card.findViewById<TextView>(R.id.section_title).text = title
        parent.addView(card)
        return card.findViewById(R.id.section_body)
    }

    /** [score] in 0..1 draws a bar under the row. */
    fun row(
        body: LinearLayout,
        label: String,
        value: String,
        badge: Pair<String, Badge>? = null,
        score: Float? = null,
        collapsed: Boolean = false,
    ): View {
        val c = body.context
        val row = LayoutInflater.from(c).inflate(R.layout.item_attr_row, body, false)
        row.findViewById<TextView>(R.id.row_label).text = label
        val v = row.findViewById<TextView>(R.id.row_value)
        v.text = value
        if (collapsed) {
            v.maxLines = 2
            v.setTextIsSelectable(false)
            v.setOnClickListener { v.maxLines = if (v.maxLines == 2) Int.MAX_VALUE else 2 }
        }
        if (badge != null) {
            val b = row.findViewById<TextView>(R.id.row_badge)
            b.visibility = View.VISIBLE
            b.text = badge.first
            Ui.tintPill(b, ContextCompat.getColor(c, badge.second.color))
        }
        if (score != null && !score.isNaN()) {
            val bar = row.findViewById<LinearProgressIndicator>(R.id.row_bar)
            bar.visibility = View.VISIBLE
            bar.setProgressCompat((score * 100).roundToInt().coerceIn(0, 100), false)
            badge?.let { bar.setIndicatorColor(ContextCompat.getColor(c, it.second.color)) }
        }
        body.addView(row)
        return row
    }
}
