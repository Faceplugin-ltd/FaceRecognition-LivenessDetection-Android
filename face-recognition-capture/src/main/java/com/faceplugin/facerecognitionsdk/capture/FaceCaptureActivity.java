package com.faceplugin.facerecognitionsdk.capture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import android.view.WindowManager;
import android.view.Window;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.faceplugin.facerecognitionsdk.kit.FaceBox;
import com.faceplugin.facerecognitionsdk.kit.FaceDetectionParam;
import com.faceplugin.facerecognitionsdk.kit.CameraFrameUtils;
import com.faceplugin.facerecognitionsdk.kit.FaceDetector;
import com.faceplugin.facerecognitionsdk.kit.FaceEngine;
import com.faceplugin.facerecognitionsdk.kit.FaceLimits;
import com.faceplugin.facerecognitionsdk.kit.CameraPreview;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Capture guide used by Add one person: oval coach, 2 s hold, then a full check of the still. */
public abstract class FaceCaptureActivity extends AppCompatActivity implements CaptureView.ViewModeChanged {

    /** Add one person always uses the front camera. */
    private static final int LENS = CameraSelector.LENS_FACING_FRONT;

    /** Per-frame guide check: box, landmarks and pose only, so each frame stays cheap. */
    private static final FaceDetectionParam TRACKING_PARAM = new FaceDetectionParam();

    private ExecutorService cameraExecutorService;
    private PreviewView viewFinder;
    private CaptureView captureView;
    private TextView warningTxt;
    private ConstraintLayout lytCaptureResult;
    private Context context;
    private Bitmap capturedBitmap = null;
    private FaceBox capturedFace = null;
    private ProcessCameraProvider cameraProvider = null;
    private Size lastFrameSize = new Size(CameraPreview.WIDTH, CameraPreview.HEIGHT);
    private Bitmap lastFrame = null;
    private View resultPanel;
    private androidx.appcompat.app.AlertDialog resultDialog;
    private boolean keyboardUp;
    private Bitmap holdBitmap;
    private FaceBox holdFace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fp_activity_capture);
        context = this;
        viewFinder = findViewById(R.id.fp_capture_preview);
        captureView = findViewById(R.id.fp_capture_overlay);
        warningTxt = findViewById(R.id.fp_capture_warning);
        lytCaptureResult = findViewById(R.id.fp_capture_result);
        if (resultPanelInDialog()) {
            // The review is presented as a full-height dialog, the same surface the enrollment
            // status uses straight after it, so the two steps do not change shape mid-flow.
            resultPanel = getLayoutInflater().inflate(resultPanelLayout(), null, false);
        } else {
            resultPanel = getLayoutInflater().inflate(
                    resultPanelLayout(), findViewById(R.id.fp_capture_result_content), true);
        }
        cameraExecutorService = Executors.newFixedThreadPool(1);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            viewFinder.post(this::setUpCamera);
        }

        captureView.setViewModeInterface(this);
        captureView.setHoldListener(this::onCaptureHoldComplete);
        captureView.setViewMode(CaptureView.VIEW_MODE.NO_FACE_PREPARE);
        captureView.setMirrorX(LENS == CameraSelector.LENS_FACING_FRONT);

        ((MaterialToolbar) findViewById(R.id.fp_capture_toolbar)).setNavigationOnClickListener(view -> finish());
    }

    protected Bitmap capturedPhoto() {
        return capturedBitmap;
    }

    protected boolean hasCapture() {
        return capturedBitmap != null && capturedFace != null;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelCaptureHold();
        captureView.setFaceBoxes(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraExecutorService.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            viewFinder.post(this::setUpCamera);
        }
    }

    private void setUpCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception ignored) {
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void bindCameraUseCases() {
        try {
            CameraPreview.bind(
                    this,
                    cameraProvider,
                    viewFinder,
                    LENS,
                    cameraExecutorService,
                    this::analyzeImage);
        } catch (Exception ignored) {
        }
    }

    private void rememberHoldFrame(Bitmap bitmap, FaceBox face) {
        if (holdBitmap != null && holdBitmap != bitmap && !holdBitmap.isRecycled()) {
            holdBitmap.recycle();
        }
        holdBitmap = bitmap;
        holdFace = face;
    }

    private void clearHoldFrame() {
        if (holdBitmap != null && !holdBitmap.isRecycled()) {
            holdBitmap.recycle();
        }
        holdBitmap = null;
        holdFace = null;
    }

    private void cancelCaptureHold() {
        captureView.cancelHold();
        clearHoldFrame();
    }

    /** The held face stayed valid for 2 seconds. Keep the newest frame from that window. */
    private void onCaptureHoldComplete() {
        if (captureView.viewMode != CaptureView.VIEW_MODE.FACE_CIRCLE) {
            return;
        }
        if (holdBitmap == null || holdBitmap.isRecycled() || holdFace == null) {
            cancelCaptureHold();
            return;
        }
        if (capturedBitmap != null && capturedBitmap != holdBitmap && !capturedBitmap.isRecycled()) {
            capturedBitmap.recycle();
        }
        capturedBitmap = holdBitmap;
        capturedFace = holdFace;
        holdBitmap = null;
        holdFace = null;
        captureView.setCapturedBitmap(capturedBitmap);
        captureView.setViewMode(CaptureView.VIEW_MODE.FACE_CAPTURE_PREPARE);
    }

    private void applyCaptureTracking(List<FaceBox> faceBoxes, Bitmap frame, Size frameSize) {
        FaceCaptureState faceCaptureState = checkFace(faceBoxes, frameSize, limits());

        if (captureView.viewMode == CaptureView.VIEW_MODE.REPEAT_NO_FACE_PREPARE) {
            if (faceCaptureState.compareTo(FaceCaptureState.NO_FACE) > 0) {
                runOnUiThread(() -> captureView.setViewMode(CaptureView.VIEW_MODE.TO_FACE_CIRCLE));
            }
        } else if (captureView.viewMode == CaptureView.VIEW_MODE.FACE_CIRCLE) {
            boolean ready = faceCaptureState == FaceCaptureState.CAPTURE_OK
                    && frame != null && !faceBoxes.isEmpty();
            Bitmap captureCopy = null;
            FaceBox captureFace = null;
            if (ready) {
                synchronized (this) {
                    if (!frame.isRecycled()) {
                        captureCopy = frame.copy(Bitmap.Config.ARGB_8888, false);
                    }
                }
                if (captureCopy != null) {
                    captureFace = faceBoxes.get(0);
                } else {
                    ready = false;
                }
            }
            final boolean holdReady = ready;
            final Bitmap pending = captureCopy;
            final FaceBox pendingFace = captureFace;
            runOnUiThread(() -> {
                if (captureView.viewMode != CaptureView.VIEW_MODE.FACE_CIRCLE) {
                    if (pending != null && !pending.isRecycled()) {
                        pending.recycle();
                    }
                    return;
                }
                captureView.setFrameSize(frameSize);
                captureView.setFaceBoxes(faceBoxes);
                if (faceCaptureState == FaceCaptureState.NO_FACE) {
                    cancelCaptureHold();
                    warningTxt.setText("");
                    captureView.setViewMode(CaptureView.VIEW_MODE.FACE_CIRCLE_TO_NO_FACE);
                } else if (holdReady && pending != null && pendingFace != null) {
                    warningTxt.setText("");
                    rememberHoldFrame(pending, pendingFace);
                    captureView.startHold();
                } else {
                    cancelCaptureHold();
                    if (pending != null && !pending.isRecycled()) {
                        pending.recycle();
                    }
                    warningTxt.setText(warningFor(faceCaptureState));
                }
            });
        } else if (captureView.viewMode == CaptureView.VIEW_MODE.FACE_CAPTURE_DONE) {
            runOnUiThread(() -> {
                if (cameraProvider != null) cameraProvider.unbindAll();
            });
        }
    }

    @Override
    public void view5_finished() {
        Bitmap bitmap = capturedBitmap;
        FaceBox fallback = capturedFace;
        if (bitmap == null) {
            onCaptureAnalyzed(null, 0);
            return;
        }
        showResultPanel();
        onCaptureAnalyzing();
        FaceEngine.INSTANCE.getWorker().execute(() -> {
            FaceBox shown = fallback;
            int count = fallback == null ? 0 : 1;
            try {
                FaceDetectionParam param = FaceDetectionParam.allAttributes();
                param.check_eye_closeness = false;
                List<FaceBox> faceBoxes = FaceDetector.detect(bitmap, param);
                if (faceBoxes != null) {
                    count = faceBoxes.size();
                    if (!faceBoxes.isEmpty()) shown = largest(faceBoxes);
                }
            } catch (Exception ignored) {
            }
            FaceBox result = shown;
            int faces = count;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                onCaptureAnalyzed(result, faces);
            });
        });
    }

    private static FaceBox largest(List<FaceBox> boxes) {
        FaceBox best = boxes.get(0);
        for (FaceBox b : boxes) {
            if (b.width() * b.height() > best.width() * best.height()) best = b;
        }
        return best;
    }

    /**
     * The panel to show once a still has been captured and checked — your own review, fields
     * and buttons. It is inflated into a scrolling container under the captured face.
     */
    @LayoutRes
    protected abstract int resultPanelLayout();

    /** The inflated {@link #resultPanelLayout()}, for findViewById in the subclass. */
    protected View resultPanel() {
        return resultPanel;
    }

    /**
     * Whether the review panel is presented as a full-height dialog rather than a panel under
     * the captured face. True by default: the enrollment status that follows is a dialog, and
     * two different surfaces for two consecutive steps reads as two different screens.
     *
     * <p>Override to {@code false} for the inline panel.
     */
    protected boolean resultPanelInDialog() {
        return true;
    }

    /** Shows the review, as a dialog or as the inline panel. */
    private void showResultPanel() {
        if (!resultPanelInDialog()) {
            lytCaptureResult.setVisibility(View.VISIBLE);
            return;
        }
        if (resultDialog != null && resultDialog.isShowing()) return;
        ScrollView scroller = new ScrollView(this);
        if (resultPanel.getParent() instanceof ViewGroup) {
            ((ViewGroup) resultPanel.getParent()).removeView(resultPanel);
        }
        scroller.addView(resultPanel);
        scroller.setFillViewport(true);
        resultDialog = new MaterialAlertDialogBuilder(this)
                .setView(scroller)
                // Dismissing is a retake, not a silent exit: the capture is not yet enrolled.
                .setOnCancelListener(dialog -> retakeCapture())
                .show();
        Window window = resultDialog.getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            // Without this the keyboard covers the fields: a dialog window does not resize
            // for the IME unless it is asked to.
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView(), (v, insets) -> {
                boolean typing = insets.isVisible(WindowInsetsCompat.Type.ime());
                if (typing != keyboardUp) {
                    keyboardUp = typing;
                    onReviewKeyboardChanged(typing);
                }
                return insets;
            });
        }
    }

    /** Hides the review without treating it as a cancel. */
    private void hideResultPanel() {
        lytCaptureResult.setVisibility(View.GONE);
        keyboardUp = false;
        if (resultDialog != null) {
            resultDialog.setOnCancelListener(null);
            resultDialog.dismiss();
            resultDialog = null;
        }
    }

    /** Closes the review once the capture has been accepted, e.g. after a successful enroll. */
    protected void dismissResultPanel() {
        hideResultPanel();
    }

    /**
     * Called when the keyboard opens or closes over the review dialog. Shrink whatever can
     * give way — on a short screen the fields and buttons have to stay reachable.
     *
     * @param visible true while the keyboard is up
     */
    protected void onReviewKeyboardChanged(boolean visible) {
    }

    /** The thresholds the guide checks against. Override to use your own settings. */
    protected FaceLimits limits() {
        return FaceLimits.DEFAULT;
    }

    /** Called on the main thread when the still starts its full attribute check. */
    protected void onCaptureAnalyzing() {
    }

    /**
     * Called on the main thread with the captured still's largest face (all attributes, null when none)
     * and how many faces the still holds.
     */
    protected abstract void onCaptureAnalyzed(FaceBox shown, int faceCount);

    /** Back to the live guide for a new capture. */
    protected void retakeCapture() {
        hideResultPanel();
        warningTxt.setText("");
        capturedBitmap = null;
        capturedFace = null;
        clearHoldFrame();
        captureView.setFaceBoxes(null);
        captureView.setViewMode(CaptureView.VIEW_MODE.NO_FACE_PREPARE);
        if (cameraProvider != null) bindCameraUseCases();
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(androidx.camera.core.ImageProxy imageProxy) {
        Bitmap frame = null;
        try {
            if (skipFrame()) {
                return;
            }
            boolean backCamera = LENS == CameraSelector.LENS_FACING_BACK;
            frame = CameraFrameUtils.fromImageProxy(imageProxy, backCamera);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            imageProxy.close();
        }
        if (frame == null || skipFrame()) {
            return;
        }
        synchronized (this) {
            if (lastFrame != null && lastFrame != frame && !lastFrame.isRecycled()) {
                lastFrame.recycle();
            }
            lastFrame = frame;
            lastFrameSize = new Size(frame.getWidth(), frame.getHeight());
        }
        // The guide needs the box, the landmarks and the pose — not the full attribute set.
        List<FaceBox> faceBoxes = FaceDetector.detect(frame, TRACKING_PARAM);
        applyCaptureTracking(faceBoxes, frame, new Size(frame.getWidth(), frame.getHeight()));
    }

    /** True while the guide is animating in, already finished, or the engine is not ready. */
    private boolean skipFrame() {
        return captureView.viewMode == CaptureView.VIEW_MODE.NO_FACE_PREPARE
                || captureView.viewMode == CaptureView.VIEW_MODE.FACE_CAPTURE_DONE
                || !FaceEngine.INSTANCE.isReady();
    }

    private static String warningFor(FaceCaptureState state) {
        switch (state) {
            case MULTIPLE_FACES:
                return "Multiple face detected!";
            case FIT_IN_CIRCLE:
                return "Fit in circle!";
            case MOVE_CLOSER:
                return "Move closer!";
            case NO_FRONT:
                return "Not fronted face!";
            case FACE_OCCLUDED:
                return "Face occluded!";
            case EYE_CLOSED:
                return "Eye closed!";
            case SPOOFED_FACE:
                return "Spoof face";
            default:
                return "";
        }
    }

    public static FaceCaptureState checkFace(List<FaceBox> faceBoxes, Size frameSize, FaceLimits limits) {
        if (faceBoxes == null || faceBoxes.size() == 0)
            return FaceCaptureState.NO_FACE;
        if (faceBoxes.size() > 1) return FaceCaptureState.MULTIPLE_FACES;

        FaceBox faceBox = faceBoxes.get(0);
        float faceLeft = Float.MAX_VALUE;
        float faceRight = 0f;
        float faceBottom = 0f;
        int nMarks = Math.max(0, Math.min(faceBox.landmarkCount, faceBox.landmarks_68.length / 2));
        if (nMarks >= 5) {
            for (int i = 0; i < nMarks; i++) {
                faceLeft = Math.min(faceLeft, faceBox.landmarks_68[i * 2]);
                faceRight = Math.max(faceRight, faceBox.landmarks_68[i * 2]);
                faceBottom = Math.max(faceBottom, faceBox.landmarks_68[i * 2 + 1]);
            }
        } else {
            faceLeft = faceBox.x1;
            faceRight = faceBox.x2;
            faceBottom = faceBox.y2;
        }

        float sizeRate = 0.30f;
        float interRate = 0.03f;
        if (frameSize == null || frameSize.getWidth() <= 0 || frameSize.getHeight() <= 0) {
            frameSize = new Size(CameraPreview.WIDTH, CameraPreview.HEIGHT);
        }
        RectF roiRect = CaptureView.getROIRect(frameSize);
        float centerY = (faceBox.y2 + faceBox.y1) / 2f;
        float topY = centerY - (faceBox.y2 - faceBox.y1) * 2f / 3f;
        float interX = Math.max(0f, roiRect.left - faceLeft) + Math.max(0f, faceRight - roiRect.right);
        float interY = Math.max(0f, roiRect.top - topY) + Math.max(0f, faceBottom - roiRect.bottom);
        if (interX / roiRect.width() > interRate || interY / roiRect.height() > interRate) {
            return FaceCaptureState.FIT_IN_CIRCLE;
        }
        if ((faceBox.y2 - faceBox.y1) * (faceBox.x2 - faceBox.x1) < roiRect.width() * roiRect.height() * sizeRate) {
            return FaceCaptureState.MOVE_CLOSER;
        }
        if (!limits.poseOk(faceBox.yaw, faceBox.roll, faceBox.pitch)) {
            return FaceCaptureState.NO_FRONT;
        }
        String mask = faceBox.maskLabel == null ? "" : faceBox.maskLabel.toLowerCase();
        if (mask.contains("yes")) {
            return FaceCaptureState.FACE_OCCLUDED;
        }
        return FaceCaptureState.CAPTURE_OK;
    }
}
