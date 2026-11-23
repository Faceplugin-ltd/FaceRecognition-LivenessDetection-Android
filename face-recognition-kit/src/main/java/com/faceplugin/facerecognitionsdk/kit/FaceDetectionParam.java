package com.faceplugin.facerecognitionsdk.kit;

import com.faceplugin.facerecognitionsdk.FaceRecognitionSDK;

/** Demo detect options. Emotion, mask, glasses and occlusion are ignored by this engine. */
public class FaceDetectionParam {
    public boolean check_liveness = false;
    public int check_liveness_level = 0;
    public boolean check_eye_closeness = false;
    public boolean check_face_occlusion = false;
    public boolean estimate_age_gender = false;
    public boolean check_pose = true;
    public boolean check_landmarks = true;
    public boolean check_quality = false;
    public boolean check_emotion = false;
    public boolean check_mask = false;
    public boolean check_glasses = false;
    /** Smile and mouth openness. */
    public boolean check_expression = false;

    public FaceRecognitionSDK.DetectOptions options() {
        FaceRecognitionSDK.DetectOptions options = new FaceRecognitionSDK.DetectOptions();
        options.liveness = check_liveness;
        options.quality = check_quality;
        options.pose = check_pose;
        options.ageGender = estimate_age_gender;
        options.eyes = check_eye_closeness;
        options.points = check_landmarks;
        options.expression = check_expression;
        return options;
    }

    public static FaceDetectionParam allAttributes() {
        FaceDetectionParam p = new FaceDetectionParam();
        p.check_liveness = true;
        p.check_eye_closeness = true;
        p.check_face_occlusion = true;
        p.estimate_age_gender = true;
        p.check_quality = true;
        p.check_emotion = true;
        p.check_mask = true;
        p.check_glasses = true;
        p.check_expression = true;
        return p;
    }
}
