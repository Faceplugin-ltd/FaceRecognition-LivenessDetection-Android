package com.faceplugin.facerecognitionsdk.shell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.FaceRecord
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.roundToInt

/** People: count tag, Add person, ⋮ (Import photos, Import folder, Delete all faces), search, paged list. */
class PeopleFragment : Fragment() {

    private lateinit var list: RecyclerView
    private lateinit var countTag: TextView
    private lateinit var empty: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView

    private val items = ArrayList<FaceRecord>()
    private val adapter = Adapter()
    private var query = ""
    private var total = 0
    private var loading = false
    private var endReached = false
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { reload() }
    private val galleryListener: () -> Unit = { reload() }

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            startActivity(Intent(requireContext(), ImportActivity::class.java)
                .putParcelableArrayListExtra(ImportActivity.EXTRA_URIS, ArrayList(uris)))
        }
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) {
            startActivity(Intent(requireContext(), ImportActivity::class.java).putExtra(ImportActivity.EXTRA_TREE, tree))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_people, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.list)
        countTag = view.findViewById(R.id.count_tag)
        empty = view.findViewById(R.id.empty)
        emptyTitle = view.findViewById(R.id.empty_title)
        emptyBody = view.findViewById(R.id.empty_body)

        val lm = LinearLayoutManager(requireContext())
        list.layoutManager = lm
        list.adapter = adapter
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (lm.findLastVisibleItemPosition() >= items.size - 8) loadMore()
            }
        })

        view.findViewById<View>(R.id.add_person).setOnClickListener { showAddMenu(it) }
        view.findViewById<View>(R.id.overflow).setOnClickListener { confirmDeleteAll() }
        view.findViewById<TextInputEditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                main.removeCallbacks(searchRunnable)
                main.postDelayed(searchRunnable, 250)
            }
        })
        FaceGallery.get(requireContext()).addListener(galleryListener)
        reload()
    }

    override fun onDestroyView() {
        FaceGallery.get(requireContext()).removeListener(galleryListener)
        main.removeCallbacks(searchRunnable)
        super.onDestroyView()
    }

    private fun showAddMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.add_one_person)
        popup.menu.add(0, 2, 1, R.string.add_multiple)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> startActivity(Intent(requireContext(), AddPersonActivity::class.java))
                2 -> chooseMultipleSource()
            }
            true
        }
        popup.show()
    }

    /** Add multiple people: pick several photos, or a folder whose photos are all imported. */
    private fun chooseMultipleSource() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_multiple)
            .setItems(arrayOf(getString(R.string.select_photos), getString(R.string.select_folder))) { _, which ->
                if (which == 0) pickPhotos.launch(arrayOf("image/*")) else pickFolder.launch(null)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        val app = requireContext().applicationContext
        FaceEngine.worker.execute {
            val n = FaceGallery.get(app).count()
            main.post {
                if (!isAdded) return@post
                if (n == 0) {
                    Toast.makeText(app, R.string.nothing_to_delete, Toast.LENGTH_SHORT).show()
                    return@post
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_all_faces)
                    .setMessage(getString(R.string.delete_all_confirm, n))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete_all) { _, _ ->
                        FaceEngine.worker.execute {
                            val deleted = FaceGallery.get(app).deleteAll()
                            main.post {
                                Toast.makeText(app, app.getString(R.string.deleted_all, deleted), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
                    .getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
            }
        }
    }

    // ---- paging ----

    private fun reload() {
        if (view == null) return
        generation++
        items.clear()
        endReached = false
        loading = false
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
        val app = requireContext().applicationContext
        val gen = generation
        val q = query
        FaceEngine.worker.execute {
            val all = FaceGallery.get(app).count()
            val filtered = if (q.isEmpty()) all else FaceGallery.get(app).count(q)
            main.post {
                if (gen != generation || view == null) return@post
                total = filtered
                countTag.text = all.toString()
                updateEmpty(all)
                loadMore()
            }
        }
    }

    private fun loadMore() {
        if (loading || endReached || view == null) return
        loading = true
        val app = requireContext().applicationContext
        val gen = generation
        val q = query
        val offset = items.size
        FaceEngine.worker.execute {
            val page = FaceGallery.get(app).page(q, PAGE_SIZE, offset)
            main.post {
                if (gen != generation || view == null) return@post
                loading = false
                if (page.size < PAGE_SIZE) endReached = true
                val start = items.size
                items.addAll(page)
                adapter.notifyItemRangeInserted(start, page.size)
                updateEmpty(null)
            }
        }
    }

    private fun updateEmpty(all: Int?) {
        val galleryEmpty = (all ?: -1) == 0
        val show = items.isEmpty() && !loading && (galleryEmpty || total == 0)
        empty.visibility = if (show) View.VISIBLE else View.GONE
        if (query.isNotEmpty()) {
            emptyTitle.setText(R.string.no_results)
            emptyBody.visibility = View.GONE
        } else {
            emptyTitle.setText(R.string.txt_empty_title)
            emptyBody.visibility = View.VISIBLE
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_person, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    }

    private inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        private val thumb = v.findViewById<ImageView>(R.id.thumb)
        private val name = v.findViewById<TextView>(R.id.name)
        private val sub = v.findViewById<TextView>(R.id.subline)
        private val tag = v.findViewById<TextView>(R.id.id_tag)

        fun bind(r: FaceRecord) {
            name.text = r.name
            val date = Ui.enrolledDate(r.createdAt)
            sub.text = if (r.note.isBlank()) date else "${r.note} · $date"
            tag.text = Ui.idTag(r.id)
            Ui.loadImage(thumb, r.cropPath, (48 * itemView.resources.displayMetrics.density).roundToInt())
            itemView.setOnClickListener { FaceDetailSheet.show(childFragmentManager, r.id) }
        }
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
