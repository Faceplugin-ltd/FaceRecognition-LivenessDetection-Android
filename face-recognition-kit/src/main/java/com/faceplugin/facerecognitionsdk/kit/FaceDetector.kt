package com.faceplugin.facerecognitionsdk.kit

import android.graphics.Bitmap
import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK

/**
 * One-shot detection and template extraction on a still bitmap.
 *
 * The engine is not concurrent, so every call here takes the same lock as [FaceEngine] and
 * [FaceGallery]. Call these from a background thread — [FaceEngine.worker] is one.
 *
 * ```java
 * FaceEngine.INSTANCE.getWorker().execute(() -> {
 *     List<FaceBox> faces = FaceDetector.detect(photo, FaceDetectionParam.allAttributes());
 *     byte[] template = faces.isEmpty() ? null : FaceDetector.template(photo, faces.get(0));
 * });
 * ```
 */
object FaceDetector {

    /** Every face in [bitmap], with the attributes [param] asks for. Empty when the engine is not ready. */
    @JvmStatic
    @JvmOverloads
    fun detect(bitmap: Bitmap, param: FaceDetectionParam = FaceDetectionParam()): List<FaceBox> {
        if (!FaceEngine.isReady) return emptyList()
        return synchronized(FaceEngine.lock) {
            val image = FaceRecognitionSDK.Image.from(bitmap) ?: return@synchronized emptyList()
            try {
                image.detect(param.options()).map { it.toFaceBox() }
            } catch (_: Throwable) {
                emptyList()
            } finally {
                image.close()
            }
        }
    }

    /** The 1040-byte template for [face], or null when the engine could not read it. */
    @JvmStatic
    fun template(bitmap: Bitmap, face: FaceBox): ByteArray? {
        val source = face.source ?: return null
        if (!FaceEngine.isReady) return null
        return synchronized(FaceEngine.lock) {
            try {
                FaceRecognitionSDK.template(bitmap, source)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Similarity of two templates, 0..1. */
    @JvmStatic
    fun similarity(a: ByteArray, b: ByteArray): Float =
        synchronized(FaceEngine.lock) { FaceRecognitionSDK.similarity(a, b) }
}
