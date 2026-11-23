package com.faceplugin.facerecognitionsdk.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.FaceRecord
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.roundToInt

/** Face detail bottom sheet, opened from a result, the matches list or the People list. */
class FaceDetailSheet : BottomSheetDialogFragment() {

    private var record: FaceRecord? = null
    private val faceId get() = requireArguments().getLong(ARG_ID)
    private val similarity get() = requireArguments().getFloat(ARG_SIMILARITY, -1f)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_face_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.close).setOnClickListener { dismiss() }
        view.findViewById<TextView>(R.id.id_tag).text = Ui.idTag(faceId)
        val sim = similarity
        if (sim >= 0f) {
            view.findViewById<View>(R.id.similarity_row).visibility = View.VISIBLE
            view.findViewById<LinearProgressIndicator>(R.id.similarity_bar)
                .setProgressCompat((sim * 100).roundToInt().coerceIn(0, 100), false)
            view.findViewById<TextView>(R.id.similarity_value).text = Ui.score(sim)
        }
        view.findViewById<View>(R.id.delete).setOnClickListener { confirmDelete() }
        view.findViewById<View>(R.id.edit).setOnClickListener { edit() }
        load()
    }

    private fun load() {
        val gallery = FaceGallery.get(requireContext())
        val id = faceId
        FaceEngine.worker.execute {
            val rec = gallery.get(id)
            view?.post {
                if (!isAdded) return@post
                if (rec == null) {
                    Toast.makeText(requireContext(), R.string.face_not_found, Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                } else {
                    record = rec
                    bind(rec)
                }
            }
        }
    }

    private fun bind(r: FaceRecord) {
        val v = requireView()
        v.findViewById<TextView>(R.id.face_title).text = r.name
        v.findViewById<TextView>(R.id.note).text = r.note.ifBlank { getString(R.string.no_note) }
        v.findViewById<TextView>(R.id.enrolled).text = Ui.enrolledDateTime(r.createdAt)
        val px = (resources.displayMetrics.widthPixels / 2f).roundToInt()
        Ui.loadImage(v.findViewById<ImageView>(R.id.crop), r.cropPath, px)
        val missing = v.findViewById<View>(R.id.original_missing)
        val original = v.findViewById<ImageView>(R.id.original)
        missing.visibility = View.GONE
        Ui.loadImage(original, r.originalPath, px, android.R.color.transparent) {
            missing.visibility = View.VISIBLE
        }
        val status = v.findViewById<android.widget.FrameLayout>(R.id.enroll_status)
        val report = com.faceplugin.facerecognitionsdk.kit.CallReport.fromJson(r.enrollReport)
        if (report == null) {
            status.visibility = View.GONE
            v.findViewById<View>(R.id.enroll_status_missing).visibility = View.VISIBLE
        } else {
            v.findViewById<View>(R.id.enroll_status_missing).visibility = View.GONE
            StatusCard.bind(status, report, getString(R.string.enroll_status_title))
        }
    }

    private fun confirmDelete() {
        val r = record ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.delete_one_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.txt_delete) { _, _ ->
                val app = requireContext().applicationContext
                FaceEngine.worker.execute {
                    Ui.evict(r.cropPath); Ui.evict(r.originalPath)
                    val ok = FaceGallery.get(app).delete(r.id)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (ok) Toast.makeText(app, app.getString(R.string.deleted_face, r.name), Toast.LENGTH_SHORT).show()
                    }
                }
                dismiss()
            }
            .show()
    }

    private fun edit() {
        val r = record ?: return
        val content = layoutInflater.inflate(R.layout.dialog_edit_person, null)
        val nameLayout = content.findViewById<TextInputLayout>(R.id.name_layout)
        val nameInput = content.findViewById<TextInputEditText>(R.id.name_input)
        val noteInput = content.findViewById<TextInputEditText>(R.id.note_input)
        nameInput.setText(r.name)
        noteInput.setText(r.note)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_person)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.txt_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    nameLayout.error = getString(R.string.enter_name)
                    return@setOnClickListener
                }
                val note = noteInput.text?.toString()?.trim().orEmpty()
                val gallery = FaceGallery.get(requireContext())
                FaceEngine.worker.execute {
                    gallery.update(r.id, name, note)
                    view?.post { if (isAdded) load() }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_SIMILARITY = "similarity"
        private const val TAG = "face_detail"

        /** [similarity] < 0 hides the Similarity row (People list). */
        fun show(fm: FragmentManager, id: Long, similarity: Float = -1f) {
            if (fm.findFragmentByTag(TAG) != null) return
            FaceDetailSheet().apply {
                arguments = Bundle().apply { putLong(ARG_ID, id); putFloat(ARG_SIMILARITY, similarity) }
            }.show(fm, TAG)
        }
    }
}
