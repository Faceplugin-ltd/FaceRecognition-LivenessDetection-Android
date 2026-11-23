package com.faceplugin.facerecognitionsdk.shell

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.Candidate
import com.faceplugin.facerecognitionsdk.kit.FaceRecord
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.google.android.material.appbar.MaterialToolbar

/** "N more ›" target: every match at/above the threshold. */
class MatchesActivity : AppCompatActivity() {

    private var matches: List<Candidate> = emptyList()
    private var records: Map<Long, FaceRecord?> = emptyMap()
    private val adapter = Adapter()
    private val galleryListener: () -> Unit = { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = ResultStore.last
        if (result == null) { finish(); return }
        setContentView(R.layout.activity_matches)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        matches = result.matches
        toolbar.subtitle = Ui.matchesCount(this, matches.size)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        load()
    }

    override fun onStart() {
        super.onStart()
        FaceGallery.get(this).addListener(galleryListener)
    }

    override fun onStop() {
        FaceGallery.get(this).removeListener(galleryListener)
        super.onStop()
    }

    private fun load() {
        val all = ResultStore.last?.matches ?: return
        val gallery = FaceGallery.get(this)
        FaceEngine.worker.execute {
            val map = all.associate { it.id to gallery.get(it.id) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                records = map
                matches = all.filter { map[it.id] != null }
                findViewById<MaterialToolbar>(R.id.toolbar).subtitle = Ui.matchesCount(this, matches.size)
                @Suppress("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(MatchRow.inflate(parent)) {}

        override fun getItemCount() = matches.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val c = matches[position]
            MatchRow.bind(holder.itemView, position + 1, c, records[c.id]) {
                FaceDetailSheet.show(supportFragmentManager, it.id, it.similarity)
            }
        }
    }
}
