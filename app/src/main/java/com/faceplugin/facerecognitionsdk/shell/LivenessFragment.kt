package com.faceplugin.facerecognitionsdk.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import com.faceplugin.facerecognitionsdk.kit.FaceEngine
import com.faceplugin.facerecognitionsdk.kit.ImageUtils
import com.faceplugin.facerecognitionsdk.shell.data.AppSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Liveness on its own: the camera, a box per face and that face's liveness score. There is
 * nothing to press — no shutter, no gallery, no result screen. It is the plainest possible
 * demonstration that the engine tells a live face from a photo of one.
 */
class LivenessFragment : Fragment() {

    private lateinit var preview: PreviewView
    private lateinit var overlay: LivenessOverlayView
    private lateinit var hint: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: ExecutorService? = null
    private var cameraBound = false
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val askCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else showHint(getString(R.string.camera_permission_needed)) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_liveness, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        preview = view.findViewById(R.id.preview)
        overlay = view.findViewById(R.id.overlay)
        hint = view.findViewById(R.id.hint)
        view.findViewById<View>(R.id.switch_camera).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            stopCamera()
            bindCamera()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isHidden) onVisible()
    }

    override fun onStop() {
        super.onStop()
        stopCamera()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) stopCamera() else onVisible()
    }

    private fun onVisible() {
        if (hasCameraPermission()) startCamera() else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun showHint(text: String) {
        hint.text = text
        hint.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

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
            showHint("")
        } catch (_: Exception) {
            cameraBound = false
            showHint(getString(R.string.unavailable))
        }
    }

    private fun stopCamera() {
        if (cameraBound) {
            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
            cameraBound = false
        }
        analyzer?.shutdown()
        analyzer = null
        if (view != null) overlay.clear()
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (!FaceEngine.isReady) return
            val frame = CameraFrameUtils.fromImageProxy(
                proxy,
                lensFacing == CameraSelector.LENS_FACING_BACK,
            ) ?: return
            val small = ImageUtils.scaleDown(frame, CameraFrameUtils.PREVIEW_MAX_SIDE)
            // Liveness is the whole point of this screen, so it is the one estimator asked for.
            val faces = synchronized(FaceEngine.lock) {
                FaceEngine.impl.use(small) { it.detect(liveness = true, pose = false, eyes = false) }
            }
            val limits = AppSettings.limits(requireContext())
            val drawn = faces.map {
                LivenessOverlayView.Face(
                    android.graphics.RectF(it.box),
                    it.liveness,
                    limits.isLive(it.liveness),
                )
            }
            val size = Size(small.width, small.height)
            val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
            preview.post {
                if (!isAdded || view == null) return@post
                overlay.submit(drawn, size, mirror)
                showHint(if (drawn.isEmpty()) getString(R.string.status_no_face) else "")
            }
        } catch (_: Throwable) {
            // A dropped frame must not stop the camera loop.
        } finally {
            proxy.close()
        }
    }
}
