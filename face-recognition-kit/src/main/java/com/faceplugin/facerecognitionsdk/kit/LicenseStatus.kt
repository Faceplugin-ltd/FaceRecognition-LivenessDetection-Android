package com.faceplugin.facerecognitionsdk.kit

import org.json.JSONObject

/** Parsed FaceRecognitionSDK.getLicenseStatus() JSON (same shape as the recognition SDK). */
data class LicenseStatus(
    val licensed: Boolean,
    val level: Int,
    val levelName: String,
    val recognition: Boolean,
    val liveness: Boolean,
    val label: String,
) {
    companion object {
        fun fromJson(json: String?): LicenseStatus {
            return try {
                val o = JSONObject(json ?: "{}")
                LicenseStatus(
                    licensed = o.optBoolean("licensed", false),
                    level = o.optInt("level", -1),
                    levelName = o.optString("levelName", "None"),
                    recognition = o.optBoolean("recognition", false),
                    liveness = o.optBoolean("liveness", false),
                    label = o.optString("label", "Not licensed"),
                )
            } catch (_: Exception) {
                LicenseStatus(false, -1, "None", false, false, "Not licensed")
            }
        }
    }
}
