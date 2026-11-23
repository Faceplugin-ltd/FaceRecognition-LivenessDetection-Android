package com.faceplugin.facerecognitionsdk.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.faceplugin.facerecognitionsdk.R
import com.faceplugin.facerecognitionsdk.kit.CameraFrameUtils
import com.faceplugin.facerecognitionsdk.kit.CameraPreview
import com.faceplugin.facerecognitionsdk.shell.data.AppSettings
import com.faceplugin.facerecognitionsdk.kit.FaceGallery
import com.faceplugin.facerecognitionsdk.kit.IdentifyOutcome
import com.faceplugin.facerecognitionsdk.kit.ImageUtils
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/** Live camera identify with status chip, shutter, FaceGallery and Auto. Attribute uses the same screen. */
class IdentifyFragment : Fragment() {

    private enum class Status(val text: Int, val good: Boolean) {
        OK(R.string.status_face_ok, true),
        NO_FACE(R.string.status_no_face, false),
        MOVE_CLOSER(R.string.status_move_closer, false),
        TOO_DARK(R.string.status_too_dark, false),
        MANY(R.string.status_many_faces, false),
        YAW(R.string.status_yaw, false),
        ROLL(R.string.status_roll, false),
        PITCH(R.string.status_pitch, false),
        EYES(R.string.status_eyes, false),
    }

    private val attributeMode: Boolean get() = arguments?.getBoolean(ARG_ATTRIBUTE) == true

    private lateinit var preview: PreviewView
    private lateinit var freeze: ImageView
    private lateinit var statusChip: TextView
    private lateinit var thresholdChip: TextView
    private lateinit var countTag: TextView
    private lateinit var messageBox: LinearLayout
    private lateinit var messageText: TextView
    private lateinit var messageAction: MaterialButton
    private lateinit var busyView: CircularProgressIndicator
    private lateinit var shutter: ImageButton
    private lateinit var autoSwitch: MaterialSwitch

    private var analyzer: ExecutorService? = null
    private var cameraBound = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    @Volatile private var busy = false
    @Volatile private var autoOn = false
    @Volatile private var lastFrame: Bitmap? = null
    @Volatile private var lastStatus = Status.NO_FACE
    private var stableSince = 0L
    private var lastCenter: Pair<Float, Float>? = null

    private val galleryListener: () -> Unit = { refreshCount() }
    private val engineListener: (FaceEngine.State) -> Unit = { refreshEngineState() }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else refreshEngineState()
    }

    private val pickProbe = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext().applicationContext
        setBusy(true)
        FaceEngine.worker.execute {
            val bmp = ImageUtils.decodeUri(ctx, uri)
            if (bmp == null) {
                view?.post { setBusy(false); snack(getString(R.string.photo_unreadable)) }
            } else {
                identifyOnWorker(bmp)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_identify, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        preview = view.findViewById(R.id.preview)
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        freeze = view.findViewById(R.id.freeze_frame)
        view.findViewById<TextView>(R.id.screen_title).setText(
            if (attributeMode) R.string.tab_attribute else R.string.tab_identify
        )
        statusChip = view.findViewById(R.id.status_chip)
        thresholdChip = view.findViewById(R.id.threshold_chip)
        countTag = view.findViewById(R.id.gallery_count)
        messageBox = view.findViewById(R.id.message_box)
        messageText = view.findViewById(R.id.message_text)
        messageAction = view.findViewById(R.id.message_action)
        busyView = view.findViewById(R.id.busy)
        shutter = view.findViewById(R.id.shutter)
        autoSwitch = view.findViewById(R.id.auto_switch)

        autoSwitch.isChecked = AppSettings.autoCapture(requireContext())
        autoOn = autoSwitch.isChecked
        view.findViewById<View>(R.id.btn_auto).setOnClickListener {
            autoSwitch.isChecked = !autoSwitch.isChecked
            AppSettings.setAutoCapture(requireContext(), autoSwitch.isChecked)
            autoOn = autoSwitch.isChecked
            stableSince = 0L
        }
        view.findViewById<View>(R.id.btn_gallery).setOnClickListener {
            if (busy) return@setOnClickListener
            pickProbe.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        shutter.setOnClickListener { captureNow() }
        view.findViewById<View>(R.id.switch_camera).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK
            else CameraSelector.LENS_FACING_FRONT
            bindCamera()
        }
        setStatus(Status.NO_FACE)
    }

    override fun onStart() {
        super.onStart()
        FaceEngine.addListener(engineListener)
        FaceGallery.get(requireContext()).addListener(galleryListener)
    }

    override fun onStop() {
        FaceEngine.removeListener(engineListener)
        FaceGallery.get(requireContext()).removeListener(galleryListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        onVisible()
    }

    override fun onPause() {
        stopCamera()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) stopCamera() else onVisible()
    }

    override fun onDestroyView() {
        stopCamera()
        super.onDestroyView()
    }

    private fun onVisible() {
        if (isHidden || !isResumed) return
        busy = false
        setBusy(false)
        thresholdChip.text = getString(R.string.txt_threshold_chip, Ui.score(AppSettings.threshold(requireContext())))
        refreshCount()
        refreshEngineState()
        if (hasCameraPermission()) startCamera()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun refreshCount() {
        if (!isAdded) return
        val ctx = requireContext().applicationContext
        FaceEngine.worker.execute {
            val n = FaceGallery.get(ctx).count()
            view?.post { if (isAdded) countTag.text = Ui.facesCount(ctx, n) }
        }
    }

    private fun refreshEngineState() {
        if (!isAdded || view == null) return
        when {
            !hasCameraPermission() -> showMessage(getString(R.string.camera_permission_needed), getString(R.string.allow_camera)) {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
            !FaceEngine.isReady -> showMessage(FaceEngine.stateMessage(), null) {}
            else -> messageBox.visibility = View.GONE
        }
        shutter.isEnabled = FaceEngine.isReady && !busy
    }

    private fun showMessage(text: String, action: String?, onAction: () -> Unit) {
        messageBox.visibility = View.VISIBLE
        messageText.text = text
        if (action == null) {
            messageAction.visibility = View.GONE
        } else {
            messageAction.visibility = View.VISIBLE
            messageAction.text = action
            messageAction.setOnClickListener { onAction() }
        }
    }

    // ---- camera ----

    private fun startCamera() {
        if (cameraProvider != null) { bindCamera(); return }
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            if (!isAdded) return@addListener
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        if (!isAdded || isHidden || view == null || preview.display == null) {
            preview.post { if (isAdded && !isHidden && preview.display != null) bindCamera() }
            return
        }
        val exec = analyzer ?: Executors.newSingleThreadExecutor().also { analyzer = it }
        if (lensFacing == CameraSelector.LENS_FACING_BACK && !provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            lensFacing = CameraSelector.LENS_FACING_FRONT
        }
        try {
            CameraPreview.bind(viewLifecycleOwner, provider, preview, lensFacing, exec) { analyze(it) }
            cameraBound = true
        } catch (_: Exception) {
            cameraBound = false
            snack(getString(R.string.unavailable))
        }
    }

    private fun stopCamera() {
        // Only the fragment that owns the camera may unbind. unbindAll is process-wide,
        // so a tab that is hidden after the other tab has already bound would blank it.
        if (cameraBound) {
            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
            cameraBound = false
        }
        analyzer?.shutdown()
        analyzer = null
        val frame = lastFrame
        if (frame != null && !frame.isRecycled) {
            freeze.setImageBitmap(frame)
            freeze.visibility = View.VISIBLE
        }
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (busy || !FaceEngine.isReady) return
            val frame = CameraFrameUtils.fromImageProxy(proxy, lensFacing == CameraSelector.LENS_FACING_BACK) ?: return
            val small = ImageUtils.scaleDown(frame, CameraFrameUtils.PREVIEW_MAX_SIDE)
            val faces = synchronized(FaceEngine.lock) {
                FaceEngine.impl.use(small) { it.detect(liveness = false, pose = false, eyes = false) }
            }
            val status = when {
                faces.isEmpty() -> Status.NO_FACE
                faces.size > 1 -> Status.MANY
                faces[0].box.width() < small.width * 0.28f -> Status.MOVE_CLOSER
                faces[0].luminance in 0.001f..0.25f -> Status.TOO_DARK
                else -> Status.OK
            }
            lastFrame = frame
            val now = SystemClock.elapsedRealtime()
            if (status == Status.OK) {
                val b: Rect = faces[0].box
                val c = b.exactCenterX() to b.exactCenterY()
                val prev = lastCenter
                val moved = prev == null || abs(prev.first - c.first) > small.width * 0.06f ||
                    abs(prev.second - c.second) > small.height * 0.06f
                if (moved || stableSince == 0L) stableSince = now
                lastCenter = c
            } else {
                stableSince = 0L
                lastCenter = null
            }
            lastStatus = status
            val autoFire = status == Status.OK && autoOn && stableSince != 0L && now - stableSince >= AUTO_STABLE_MS
            preview.post {
                if (!isAdded) return@post
                if (freeze.visibility == View.VISIBLE) freeze.visibility = View.GONE
                setStatus(status)
                if (autoFire && !busy) {
                    stableSince = 0L
                    runIdentify(frame)
                }
            }
        } catch (_: Throwable) {
        } finally {
            proxy.close()
        }
    }

    private fun setStatus(s: Status) {
        statusChip.setText(s.text)
        Ui.tintPill(statusChip, ContextCompat.getColor(requireContext(), if (s.good) R.color.status_match else R.color.camera_chip))
    }

    // ---- identify ----

    private fun captureNow() {
        if (busy) return
        val frame = lastFrame
        if (frame == null || lastStatus == Status.NO_FACE) {
            snack(FaceGallery.NO_FACE)
            return
        }
        runIdentify(frame)
    }

    private fun runIdentify(probe: Bitmap) {
        setBusy(true)
        FaceEngine.worker.execute { identifyOnWorker(probe) }
    }

    private fun identifyOnWorker(probe: Bitmap) {
        val ctx = context?.applicationContext ?: return
        if (attributeMode) {
            val result = FaceGallery.get(ctx).attributes(probe, AppSettings.limits(ctx))
            view?.post {
                if (!isAdded) return@post
                ResultStore.attribute = result
                startActivity(Intent(requireContext(), AttributeResultActivity::class.java))
            }
            return
        }
        val outcome = FaceGallery.get(ctx).identify(probe, AppSettings.limits(ctx), AppSettings.liveness(ctx))
        view?.post {
            if (!isAdded) return@post
            when (outcome) {
                is IdentifyOutcome.Done -> {
                    ResultStore.last = outcome.result
                    startActivity(Intent(requireContext(), ResultActivity::class.java))
                }
                is IdentifyOutcome.Rejected -> {
                    val report = outcome.report
                    if (report == null) {
                        setBusy(false)
                        snack(outcome.reason)
                    } else {
                        // Stay busy while the status is open so auto-capture does not fire again behind it.
                        StatusCard.showDialog(requireContext(), getString(R.string.identify_stopped), outcome.reason, report) {
                            stableSince = 0L
                            setBusy(false)
                        }
                    }
                }
            }
        }
    }

    private fun setBusy(on: Boolean) {
        busy = on
        if (view == null) return
        busyView.visibility = if (on) View.VISIBLE else View.GONE
        if (on) statusChip.setText(if (attributeMode) R.string.status_attributes else R.string.status_identifying)
        shutter.isEnabled = !on && FaceEngine.isReady
    }

    private fun snack(text: String) {
        val v = view ?: return
        val bar = Snackbar.make(v, text, Snackbar.LENGTH_LONG)
        bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = 8
        bar.show()
    }

    companion object {
        /** Auto fires once one good face has stayed in place this long. */
        private const val AUTO_STABLE_MS = 300L

        fun attribute() = IdentifyFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_ATTRIBUTE, true) }
        }
    }
}

/** Hands the last identify or attribute result across without parceling bitmaps. */
object ResultStore {
    @Volatile
    var last: com.faceplugin.facerecognitionsdk.kit.IdentifyResult? = null
    @Volatile
    var attribute: com.faceplugin.facerecognitionsdk.kit.AttributeResult? = null
}

private const val ARG_ATTRIBUTE = "attribute"
