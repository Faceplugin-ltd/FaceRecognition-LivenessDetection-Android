package com.faceplugin.facerecognitionsdk.shell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.Candidate
import com.faceplugin.facerecognitionsdk.kit.FaceRecord
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.roundToInt

/** One match row: rank, round thumbnail, name, ID tag, similarity bar, score. */
object MatchRow {
    fun inflate(parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)

    fun bind(v: View, rank: Int, c: Candidate, record: FaceRecord?, onClick: (Candidate) -> Unit) {
        v.findViewById<TextView>(R.id.rank).text = rank.toString()
        v.findViewById<TextView>(R.id.name).text = record?.name ?: c.name
        v.findViewById<TextView>(R.id.id_tag).text = Ui.idTag(c.id)
        v.findViewById<LinearProgressIndicator>(R.id.bar).setProgressCompat((c.similarity * 100f).roundToInt().coerceIn(0, 100), false)
        v.findViewById<TextView>(R.id.score).text = Ui.score(c.similarity)
        val px = (44 * v.resources.displayMetrics.density).roundToInt()
        Ui.loadImage(v.findViewById<ImageView>(R.id.thumb), record?.cropPath, px)
        v.setOnClickListener { onClick(c) }
    }
}
