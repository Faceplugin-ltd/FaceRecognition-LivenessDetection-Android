package com.faceplugin.facerecognitionsdk.kit

import com.faceplugin.facerecognitionsdk.kit.FaceBox
import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK
import kotlin.math.min

/** Smile confidence at/above which the face counts as smiling. */
const val SMILE_THRESHOLD = 0.5f

fun FaceRecognitionSDK.Face.toFaceBox(): FaceBox {
    val box = FaceBox()
    box.x1 = bounds.left
    box.y1 = bounds.top
    box.x2 = bounds.right
    box.y2 = bounds.bottom
    box.face_quality = if (quality.isNaN()) score else quality
    box.yaw = if (yaw.isNaN()) 0f else yaw
    box.pitch = if (pitch.isNaN()) 0f else pitch
    box.roll = if (roll.isNaN()) 0f else roll
    if (!liveness.isNaN()) {
        box.liveness = liveness
        box.livenessLabel = if (liveness > FaceRecognitionSDK.LIVENESS_THRESHOLD) "Live" else "Fake"
    }
    if (!smile.isNaN()) {
        box.smile = smile
        // The engine reports smile only; there is no multi-class emotion model.
        box.emotionLabel = if (smile >= SMILE_THRESHOLD) "Smiling" else "Neutral"
    }
    if (!mouthOpen.isNaN()) box.mouth_opened = mouthOpen
    box.age = age
    box.gender = gender
    box.genderLabel = genderLabel
    if (!eyesOpen.isNaN()) {
        val closed = 1f - eyesOpen
        box.left_eye_closed = closed
        box.right_eye_closed = closed
        val label = if (eyesOpen >= 0.5f) "Open" else "Closed"
        box.eyesLeftLabel = label
        box.eyesRightLabel = label
    }
    val n = min(points.size / 2, box.landmarks_68.size / 2)
    box.landmarkCount = n
    for (i in 0 until n) {
        box.landmarks_68[i * 2] = points[i * 2]
        box.landmarks_68[i * 2 + 1] = points[i * 2 + 1]
    }
    box.source = this
    return box
}
