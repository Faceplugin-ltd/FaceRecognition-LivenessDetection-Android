package com.faceplugin.facerecognitionsdk.kit

import kotlin.math.abs

/**
 * The thresholds every check in this library uses. Hold one instance per app — build it from
 * your own settings store and pass it in; the library never reads preferences itself.
 *
 * The defaults are the engine's operating points: identify at 0.80 and liveness at 0.5.
 *
 * ```kotlin
 * val limits = FaceLimits(match = 0.82f, liveness = 0.6f)
 * val outcome = FaceGallery.get(context).identify(photo, limits, livenessOn = true)
 * ```
 */
data class FaceLimits(
    /** Lowest similarity that counts as the same person (1:N identify and the duplicate check). */
    val match: Float = DEFAULT_MATCH,
    /** Lowest liveness score that counts as a live face. */
    val liveness: Float = DEFAULT_LIVENESS,
    /** Largest head turn allowed, in degrees. */
    val yaw: Float = DEFAULT_ANGLE,
    val roll: Float = DEFAULT_ANGLE,
    val pitch: Float = DEFAULT_ANGLE,
) {
    /** True when [score] counts as a live face. A NaN score (liveness not run) is not live. */
    fun isLive(score: Float): Boolean = !score.isNaN() && score + EPSILON >= liveness

    /** True when the head is within [yaw], [roll] and [pitch] of facing the camera. */
    fun poseOk(yawDeg: Float, rollDeg: Float, pitchDeg: Float): Boolean =
        abs(yawDeg) <= yaw && abs(rollDeg) <= roll && abs(pitchDeg) <= pitch

    /** True when [similarity] counts as the same person. */
    fun matches(similarity: Float): Boolean = similarity >= match

    companion object {
        const val DEFAULT_MATCH = 0.80f
        const val DEFAULT_LIVENESS = 0.5f
        const val DEFAULT_ANGLE = 20f
        private const val EPSILON = 0.0001f

        /** The engine's operating points. */
        @JvmField
        val DEFAULT = FaceLimits()
    }
}
