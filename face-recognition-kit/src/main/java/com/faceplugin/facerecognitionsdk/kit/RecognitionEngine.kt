package com.faceplugin.facerecognitionsdk.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK
import com.faceplugin.facerecognitionsdk.kit.toFaceBox

/** Real engine backed by the Face Recognition SDK AAR. */
class RecognitionEngine : IdentityEngine {
    override val available: Boolean = true
    override val sdkVersion: String = "Face Recognition SDK"

    override fun machineCode(context: Context): String =
        FaceRecognitionSDK.machineCode(context)

    override fun activate(context: Context, license: String): Int =
        FaceRecognitionSDK.activate(context, license)

    override fun isActivated(): Boolean = FaceRecognitionSDK.isActivated()

    override fun licenseStatusJson(): String? {
        val license = FaceRecognitionSDK.license()
        return "{\"licensed\":${license.licensed},\"level\":${license.level}," +
            "\"levelName\":\"${license.levelName}\",\"recognition\":${license.recognition}," +
            "\"liveness\":${license.liveness},\"label\":\"${license.label}\"}"
    }

    override fun init(context: Context): Int = FaceRecognitionSDK.init(context)

    override fun <T> use(bitmap: Bitmap, block: (Frame) -> T): T {
        val image = FaceRecognitionSDK.Image.from(bitmap)
        if (image == null) return block(EmptyFrame)
        return try {
            block(SdkFrame(image))
        } finally {
            image.close()
        }
    }

    override fun similarity(a: ByteArray, b: ByteArray): Float =
        FaceRecognitionSDK.similarity(a, b)
}

private class SdkFrame(private val image: FaceRecognitionSDK.Image) : Frame {
    override fun detect(liveness: Boolean, pose: Boolean, eyes: Boolean): List<DetectedFace> {
        val options = FaceRecognitionSDK.DetectOptions()
        options.pose = pose
        options.eyes = eyes
        options.liveness = liveness
        return image.detect(options).map { it.toDetected() }
    }

    override fun detectAttributes(): List<DetectedFace> {
        val options = FaceRecognitionSDK.DetectOptions()
        options.liveness = true
        options.quality = true
        options.pose = true
        options.ageGender = true
        options.eyes = true
        options.expression = true
        options.points = true
        return image.detect(options).map { it.toDetected() }
    }

    override fun template(face: DetectedFace): ByteArray? {
        val source = face.handle as? FaceRecognitionSDK.Face ?: return null
        return image.template(source)
    }
}

private object EmptyFrame : Frame {
    override fun detect(liveness: Boolean, pose: Boolean, eyes: Boolean): List<DetectedFace> = emptyList()
    override fun detectAttributes(): List<DetectedFace> = emptyList()
    override fun template(face: DetectedFace): ByteArray? = null
}

private fun FaceRecognitionSDK.Face.toDetected(): DetectedFace {
    val box = toFaceBox()
    return DetectedFace(
        box = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
        yaw = box.yaw,
        pitch = box.pitch,
        roll = box.roll,
        liveness = box.liveness,
        livenessLabel = box.livenessLabel,
        luminance = 0f,
        leftEyeClosed = box.left_eye_closed,
        rightEyeClosed = box.right_eye_closed,
        handle = this,
    )
}

object EngineFactory {
    fun create(): IdentityEngine = RecognitionEngine()
}
