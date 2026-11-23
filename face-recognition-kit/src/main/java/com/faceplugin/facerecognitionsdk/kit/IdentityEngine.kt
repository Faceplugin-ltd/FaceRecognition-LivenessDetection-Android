package com.faceplugin.facerecognitionsdk.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect

/** Seam between the demo UI and [com.faceplugin.facerecognitionsdk.FaceRecognitionSDK]. */
interface IdentityEngine {
    val available: Boolean
    val sdkVersion: String

    fun machineCode(context: Context): String
    fun activate(context: Context, license: String): Int
    fun isActivated(): Boolean
    fun licenseStatusJson(): String?

    /** Unpacks the model pack and loads the engine. Call once, off the main thread. */
    fun init(context: Context): Int

    /**
     * One decoded frame. [detect] and [template] share it.
     * The block should not keep the [Frame] after it returns.
     */
    fun <T> use(bitmap: Bitmap, block: (Frame) -> T): T

    fun similarity(a: ByteArray, b: ByteArray): Float

    companion object {
        const val SUCCESS = 0
        const val LICENSE_INVALID = 1
        const val LICENSE_EXPIRED = 2
        const val NOT_ACTIVATED = 3
        const val INIT_FAILED = 4
        const val SDK_MISSING = -100
    }
}

interface Frame {
    fun detect(liveness: Boolean, pose: Boolean = true, eyes: Boolean = true): List<DetectedFace>
    fun detectAttributes(): List<DetectedFace>
    fun template(face: DetectedFace): ByteArray?
}

data class DetectedFace(
    val box: Rect,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val liveness: Float,
    val livenessLabel: String,
    val luminance: Float,
    val leftEyeClosed: Float = 0f,
    val rightEyeClosed: Float = 0f,
    val handle: Any? = null,
)
