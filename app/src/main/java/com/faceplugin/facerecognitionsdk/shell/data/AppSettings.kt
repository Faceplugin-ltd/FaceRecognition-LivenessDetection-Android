package com.faceplugin.facerecognitionsdk.shell.data

import android.content.Context
import android.content.SharedPreferences
import com.faceplugin.facerecognitionsdk.kit.FaceLimits
import kotlin.math.roundToInt

/**
 * Persisted demo settings.
 * Defaults are the engine operating points: match 0.80, liveness 0.5, head pose ±20°.
 */
object AppSettings {
    const val DEFAULT_THRESHOLD = FaceLimits.DEFAULT_MATCH
    const val MIN_THRESHOLD = 0.70f
    const val MAX_THRESHOLD = 0.90f

    const val DEFAULT_LIVENESS = FaceLimits.DEFAULT_LIVENESS
    const val DEFAULT_YAW = FaceLimits.DEFAULT_ANGLE
    const val DEFAULT_ROLL = FaceLimits.DEFAULT_ANGLE
    const val DEFAULT_PITCH = FaceLimits.DEFAULT_ANGLE

    private const val PREFS = "face_recognition_demo_settings"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_LIVENESS = "liveness"
    private const val KEY_LIVENESS_THRESHOLD = "liveness_threshold"
    private const val KEY_YAW = "yaw"
    private const val KEY_ROLL = "roll"
    private const val KEY_PITCH = "pitch"
    private const val KEY_AUTO = "auto_capture"
    private const val KEY_SKIP_REVIEW = "skip_review"
    private const val KEY_SCHEMA = "schema"
    private const val SCHEMA = 6

    private fun prefs(c: Context): SharedPreferences {
        val p = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getInt(KEY_SCHEMA, 0) < SCHEMA) {
            val edit = p.edit()
            val oldMatch = p.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
            if (!p.contains(KEY_THRESHOLD) || oldMatch == 0.67f) {
                edit.putFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
            }
            val oldLive = p.getFloat(KEY_LIVENESS_THRESHOLD, DEFAULT_LIVENESS)
            if (!p.contains(KEY_LIVENESS_THRESHOLD) || oldLive == 0.8f) {
                edit.putFloat(KEY_LIVENESS_THRESHOLD, DEFAULT_LIVENESS)
            }
            val oldPitch = p.getFloat(KEY_PITCH, DEFAULT_PITCH)
            if (!p.contains(KEY_PITCH) || oldPitch == 40f) {
                edit.putFloat(KEY_PITCH, DEFAULT_PITCH)
            }
            val oldYaw = p.getFloat(KEY_YAW, DEFAULT_YAW)
            if (!p.contains(KEY_YAW) || oldYaw == 40f) {
                edit.putFloat(KEY_YAW, DEFAULT_YAW)
            }
            val oldRoll = p.getFloat(KEY_ROLL, DEFAULT_ROLL)
            if (!p.contains(KEY_ROLL) || oldRoll == 40f) {
                edit.putFloat(KEY_ROLL, DEFAULT_ROLL)
            }
            edit.putBoolean(KEY_LIVENESS, false)
            edit.putInt(KEY_SCHEMA, SCHEMA).apply()
        }
        return p
    }

    fun threshold(c: Context): Float =
        clampThreshold(prefs(c).getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD))

    fun setThreshold(c: Context, v: Float) {
        prefs(c).edit().putFloat(KEY_THRESHOLD, clampThreshold(v)).apply()
    }

    fun clampThreshold(v: Float): Float =
        ((v.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)) * 100f).roundToInt() / 100f

    fun liveness(c: Context): Boolean = prefs(c).getBoolean(KEY_LIVENESS, false)
    fun setLiveness(c: Context, on: Boolean) { prefs(c).edit().putBoolean(KEY_LIVENESS, on).apply() }

    fun livenessThreshold(c: Context): Float = unit(c, KEY_LIVENESS_THRESHOLD, DEFAULT_LIVENESS)
    fun setLivenessThreshold(c: Context, v: Float) { putUnit(c, KEY_LIVENESS_THRESHOLD, v) }

    /** Every threshold the library checks against, read from these settings. */
    fun limits(c: Context): FaceLimits = FaceLimits(
        match = threshold(c),
        liveness = livenessThreshold(c),
        yaw = yaw(c),
        roll = roll(c),
        pitch = pitch(c),
    )

    fun isLive(c: Context, score: Float): Boolean = limits(c).isLive(score)

    fun yaw(c: Context): Float = angle(c, KEY_YAW, DEFAULT_YAW)
    fun setYaw(c: Context, v: Float) { putAngle(c, KEY_YAW, v) }

    fun roll(c: Context): Float = angle(c, KEY_ROLL, DEFAULT_ROLL)
    fun setRoll(c: Context, v: Float) { putAngle(c, KEY_ROLL, v) }

    fun pitch(c: Context): Float = angle(c, KEY_PITCH, DEFAULT_PITCH)
    fun setPitch(c: Context, v: Float) { putAngle(c, KEY_PITCH, v) }

    fun autoCapture(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO, false)

    /**
     * Skip the post-capture review when every check passes. Off by default: the review is
     * where the SDK is seen refusing a bad capture, which is worth showing a customer.
     */
    fun skipReview(c: Context): Boolean = prefs(c).getBoolean(KEY_SKIP_REVIEW, false)

    fun setSkipReview(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_SKIP_REVIEW, on).apply()
    }
    fun setAutoCapture(c: Context, on: Boolean) { prefs(c).edit().putBoolean(KEY_AUTO, on).apply() }

    fun reset(c: Context) {
        setThreshold(c, DEFAULT_THRESHOLD)
        setLiveness(c, false)
        setLivenessThreshold(c, DEFAULT_LIVENESS)
        setYaw(c, DEFAULT_YAW)
        setRoll(c, DEFAULT_ROLL)
        setPitch(c, DEFAULT_PITCH)
    }

    private fun unit(c: Context, key: String, fallback: Float): Float =
        prefs(c).getFloat(key, fallback).coerceIn(0f, 1f)

    private fun putUnit(c: Context, key: String, v: Float) {
        prefs(c).edit().putFloat(key, v.coerceIn(0f, 1f)).apply()
    }

    private fun angle(c: Context, key: String, fallback: Float): Float =
        prefs(c).getFloat(key, fallback).coerceIn(0f, 90f)

    private fun putAngle(c: Context, key: String, v: Float) {
        prefs(c).edit().putFloat(key, v.coerceIn(0f, 90f)).apply()
    }
}
