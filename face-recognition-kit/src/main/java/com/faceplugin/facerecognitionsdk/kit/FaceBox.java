package com.faceplugin.facerecognitionsdk.kit;

import android.graphics.RectF;

import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK;

import java.util.LinkedHashMap;
import java.util.Map;

/** Demo face result. {@link #source} is the engine face used for a later template. */
public class FaceBox {
    public int x1;
    public int y1;
    public int x2;
    public int y2;
    public float yaw;
    public float roll;
    public float pitch;
    public float liveness;
    public float face_quality;
    public float face_luminance;
    public float left_eye_closed;
    public float right_eye_closed;
    public float face_occlusion;
    /** Inner-lip gap over mouth width, 0..1. NaN when not computed. */
    public float mouth_opened = Float.NaN;
    /** FaceEngine smile confidence, 0..1. NaN when not computed. */
    public float smile = Float.NaN;
    public int age;
    public int gender;
    public float[] landmarks_68 = new float[140];
    public int landmarkCount;
    public String livenessLabel = "";
    public String genderLabel = "";
    public String emotionLabel = "";
    public String maskLabel = "";
    public String qualityLabel = "";
    public String eyesLeftLabel = "";
    public String eyesRightLabel = "";
    public String rawJson = "";
    public final Map<String, String> extraAttributes = new LinkedHashMap<>();
    public FaceRecognitionSDK.Face source;

    public int copyLandmarks(float[] dest) {
        int n = Math.max(0, Math.min(landmarkCount, landmarks_68.length / 2));
        if (dest == null || dest.length < n * 2) return 0;
        System.arraycopy(landmarks_68, 0, dest, 0, n * 2);
        return n;
    }

    public RectF region() {
        return new RectF(x1, y1, x2, y2);
    }

    public int width() {
        return Math.max(0, x2 - x1);
    }

    public int height() {
        return Math.max(0, y2 - y1);
    }
}
